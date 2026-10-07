/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.entity.data

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.commons.link.attribute.AttributeProvider
import priv.seventeen.artist.arcartx.core.config.area.Area
import priv.seventeen.artist.arcartx.core.effect.data.EffectPosition
import priv.seventeen.artist.arcartx.core.effect.data.WorldTextureBuilder
import priv.seventeen.artist.arcartx.core.playerhost.*
import priv.seventeen.artist.arcartx.event.player.PlayerAppearanceChangeEvent
import priv.seventeen.artist.arcartx.event.player.PlayerAnimationPackChangeEvent
import priv.seventeen.artist.arcartx.event.player.PlayerCostumeChangeEvent
import priv.seventeen.artist.arcartx.event.player.PlayerExtraSlotUpdateEvent
import priv.seventeen.artist.arcartx.event.player.PlayerModelUpdateEvent
import priv.seventeen.artist.arcartx.event.player.PlayerVariantChangeEvent
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.network.packet.server.SPackCostume
import priv.seventeen.artist.arcartx.network.encryptor.AESEncryptor
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler
import priv.seventeen.artist.arcartx.script.ScriptManager
import priv.seventeen.artist.arcartx.util.EntityUtils.doWithSeenBy
import priv.seventeen.artist.arcartx.util.collections.CallBack
import priv.seventeen.artist.blink.bukkitPlugin
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.max

class ArcartXPlayer(val player: Player) : ArcartXEntity(player){

    var encryptor : AESEncryptor? = null

    var key : String? = null

    var alt: Boolean = false

    val callbacks: MutableMap<String, CallBack> = HashMap()

    val crc64 : MutableSet<Long> = HashSet()

    var area : Area? = null

    private val slotCache : MutableMap<String, ItemStack> = ConcurrentHashMap()

    private val tagCooldown : MutableMap<String, Long> = HashMap()

    private var tryModelTask : Any? = null

    private var controller : String? = null

    // 上一次广播的飞行态，用于变更检测（仅在 isFlying 改变时才 seenBy 广播）
    private var lastFlying : Boolean = false

    var playerModel: String = ""
        private set

    var playerModelScale = 1.0
        private set


    // 额外模型
    private var extraModels = HashMap<String, String>()

    // A single immutable authority for body, profile, costumes and animation pack.
    private var appearanceState = PlayerAppearance()
    private var appearanceRevision = 0L
    private var appearancePreview: PlayerAppearance? = null
    private var appearanceCommitInProgress = false
    private var explicitHostSelection = false

    @Volatile
    private var clientCapabilities: Set<String> = emptySet()

    fun supportsCustomPlayerHost(): Boolean = CUSTOM_PLAYER_HOST_CAPABILITY in clientCapabilities

    fun setClientCapabilities(capabilities: Collection<String>) {
        require(capabilities.size <= 64 && capabilities.all { it.length <= 64 && it.matches(Regex("[a-z][a-z0-9_.-]{0,63}")) }) {
            "客户端能力列表无效"
        }
        clientCapabilities = capabilities.toSet()
    }

    fun getAppearance(): PlayerAppearance = appearanceState

    fun getAppearanceRevision(): Long = appearanceRevision

    // 玩家模型变体：决定是否叠加原版附属物(盔甲/披风/鞘翅/时装)
    private var variant: PlayerModelVariant = PlayerModelVariant.DEFAULT_BBMODEL
        private set

    // 玩家动画包 id
    var animationPackId: String = ""
        private set

    // 标记是否发送资源
    var sentResource = false


    var jumpTime = 0L


    init {
        ArcartX.databaseManager?.let { database ->
            val keys = ArrayList(ArcartX.configs.slotFolder.setting.keys)
            // 异步执行 JDBC 查询，避免在玩家对象构造（可能落在主线程）时阻塞；
            // NMS 物品反序列化须回到主线程
            AsteroidScheduler.runAsync(bukkitPlugin, Runnable {
                val raw = database.slotDatabase.loadPlayerSlotDataRaw(player.uniqueId, keys)
                AsteroidScheduler.ensureMainThread(bukkitPlugin, Runnable {
                    raw.forEach { (slot, json) ->
                        database.slotDatabase.deserializeItemStack(json)?.let { slotCache[slot] = it }
                    }
                })
            })
        }
    }

    override fun setModel(modelID: String, scale: Double) {
        if (commitAppearance(appearanceState.copy(model = modelID, scale = scale, profileId = ""), true, false)) {
            PlayerModelUpdateEvent(player, modelID).call()
        }
    }

    override fun setModel(modelID: String, scale: Double, reset: Boolean) {
        if (commitAppearance(appearanceState.copy(model = modelID, scale = scale, profileId = ""), reset, false)) {
            PlayerModelUpdateEvent(player, modelID).call()
        }
    }


    override fun removeModel() {
        commitAppearance(appearanceState.copy(model = "", scale = 1.0, profileId = "", slots = emptyMap()), hostSelected = false)
    }

    fun addExtraModel(locator: String, modelID: String ) {
        extraModels[locator] = modelID
        player.doWithSeenBy { NetworkMessageSender.sendAddExtraModel(it, player.uniqueId, locator, modelID) }
    }

    fun removeExtraModel(locator: String) {
        extraModels.remove(locator)
        player.doWithSeenBy { NetworkMessageSender.sendRemoveExtraModel(it, player.uniqueId, locator) }
    }

    fun clearExtraModels() {
        extraModels.clear()
        player.doWithSeenBy { NetworkMessageSender.sendClearExtraModel(it, player.uniqueId) }
    }


    fun equipSuit(modelID: String, hide: Boolean = true) {
        changeAfterLegacyEvent(
            { it.copy(legacyCostume = LegacyCostumeState(modelID.takeIf { it.isNotEmpty() }, hide)) },
            { PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.EQUIP_SUIT, null, modelID, hide).call() }
        )
    }

    fun equipCostume(slot: CostumeSlot, modelID: String, hide: Boolean = false) {
        changeAfterLegacyEvent({ state ->
            val slots = LinkedHashMap(state.legacyCostume.slots)
            if (modelID.isEmpty()) slots.remove(slot.id) else slots[slot.id] = LegacyCostumeEntry(modelID, hide)
            state.copy(legacyCostume = LegacyCostumeState(slots = slots))
        }, { PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.EQUIP_SLOT, slot, modelID, hide).call() })
    }

    fun removeCostume(slot: CostumeSlot) {
        changeAfterLegacyEvent({ state ->
            val slots = LinkedHashMap(state.legacyCostume.slots).apply { remove(slot.id) }
            state.copy(legacyCostume = LegacyCostumeState(slots = slots))
        }, { PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.REMOVE_SLOT, slot, null, false).call() })
    }

    fun clearCostume() {
        changeAfterLegacyEvent(
            { it.copy(slots = emptyMap(), legacyCostume = LegacyCostumeState()) },
            { PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.CLEAR, null, null, false).call() }
        )
    }

    @JvmOverloads
    fun setPlayerHost(modelID: String, profileId: String = "", scale: Double = 1.0): Boolean {
        require(modelID.isNotBlank()) { "宿主模型 ID 不能为空" }
        return commitAppearance(appearanceState.copy(model = modelID, scale = scale, variant = "DEFAULT_BBMODEL", profileId = profileId), hostSelected = true)
    }

    @JvmOverloads
    fun equipCostume(slotId: String, modelID: String, binding: PlayerHostBinding = PlayerHostBinding(), hide: PlayerHostHide? = null): Boolean {
        HostProfileValidation.requireId(slotId)
        val slots = LinkedHashMap(appearanceState.slots).apply { put(slotId, PlayerHostCostume(modelID, binding, hide)) }
        return commitAppearance(appearanceState.copy(slots = slots))
    }

    fun removeCostume(slotId: String): Boolean {
        HostProfileValidation.requireId(slotId)
        return commitAppearance(appearanceState.copy(slots = LinkedHashMap(appearanceState.slots).apply { remove(slotId) }))
    }

    /** Call on the server thread. Cancelling the event leaves every field unchanged. */
    fun setAppearance(appearance: PlayerAppearance): Boolean = commitAppearance(appearance, hostSelected = appearance.isCustomHost())

    fun updateAppearance(update: Consumer<PlayerAppearanceBuilder>): Boolean {
        val builder = PlayerAppearanceBuilder(appearanceState)
        update.accept(builder)
        val next = builder.build()
        return commitAppearance(next, hostSelected = if (builder.hostSelectionChanged) next.isCustomHost() else null)
    }

    private fun validateAppearance(candidate: PlayerAppearance): PlayerAppearance {
        val next = candidate.validated()
        if (next.profileId.isNotEmpty()) {
            val profile = ArcartX.configs.playerHostProfiles.profile(next.profileId)
                ?: throw IllegalArgumentException("玩家宿主配置不存在: ${next.profileId}")
            require(profile["model"] == next.model) { "玩家宿主配置与模型不匹配" }
        }
        return next
    }

    private fun changeAfterLegacyEvent(change: (PlayerAppearance) -> PlayerAppearance, event: () -> Boolean,
        resetBody: Boolean? = null, hostSelected: Boolean? = null): Boolean = commitLegacyAppearanceChange(
        { appearanceState }, change, ::validateAppearance, event,
        { commitAppearance(it, resetBody, hostSelected) }
    )

    private fun commitAppearance(candidate: PlayerAppearance, resetBody: Boolean? = null, hostSelected: Boolean? = null): Boolean {
        val next = validateAppearance(candidate)
        val previous = appearanceState
        val nextHostSelection = hostSelected ?: explicitHostSelection
        if (next == previous && nextHostSelection == explicitHostSelection && resetBody != true) return true
        check(!appearanceCommitInProgress) { "不能在外观变更事件中再次提交外观" }
        appearanceCommitInProgress = true
        try {
            if (!PlayerAppearanceChangeEvent(player, previous, next).call()) return false
        } finally {
            appearanceCommitInProgress = false
        }
        val previousDisplay = appearancePreview ?: previous
        val previousHostSelection = explicitHostSelection
        appearancePreview = null
        AsteroidScheduler.cancelTask(tryModelTask)
        tryModelTask = null
        appearanceState = next
        explicitHostSelection = nextHostSelection
        appearanceRevision++
        model = next.model
        scale = next.scale
        playerModel = next.model
        playerModelScale = next.scale
        variant = PlayerModelVariant.valueOf(next.variant)
        animationPackId = next.packId
        player.doWithSeenBy { sendAppearanceTo(it, previousDisplay, resetBody, previousHostSelection) }
        return true
    }

    private fun sendAppearanceTo(target: Player, previous: PlayerAppearance? = null, resetBody: Boolean? = null,
        previousHostSelection: Boolean = explicitHostSelection) {
        val state = appearancePreview ?: appearanceState
        val recipient = ArcartXEntityManager.getPlayer(target)
        if (recipient?.supportsCustomPlayerHost() == true) {
            val projection = state.forRenderRootsCapability(
                PlayerHostCapabilities.COSTUME_RENDER_ROOTS in recipient.clientCapabilities)
            NetworkMessageSender.sendPlayerAppearance(target, player.uniqueId, appearanceRevision, projection,
                ArcartX.configs.playerHostProfiles.snapshot().revision, resetBody)
            return
        }
        // Old clients can render the body geometry but cannot bind a configurable host skeleton.
        val projection = state.legacyProjection(explicitHostSelection)
        val previousProjection = previous?.legacyProjection(previousHostSelection)
        val legacy = projection.costume
        val previousLegacy = previousProjection?.costume
        val legacyVariant = projection.variant
        val oldVariant = previousProjection?.variant
        val pack = projection.packId
        val oldPack = previousProjection?.packId
        if (previous == null || resetBody == true || state.model != previous.model || state.scale != previous.scale) {
            NetworkMessageSender.setEntityModel(target, player.uniqueId, state.model, state.scale,
                legacyAppearanceModelReset(resetBody, previous != null, state.model != previous?.model))
        }
        if (previous == null || legacy != previousLegacy) NetworkMessageSender.sendCostume(target, player.uniqueId,
            legacy.suit, legacy.suitHide, legacy.slots.mapValues { SPackCostume.Slot(it.value.model, it.value.hide) })
        if (previous == null || legacyVariant != oldVariant) NetworkMessageSender.sendPlayerVariant(target, player.uniqueId, legacyVariant)
        if (previous == null || pack != oldPack) NetworkMessageSender.sendAnimationPack(target, player.uniqueId, pack)
    }

    fun syncAppearanceProfiles() {
        player.doWithSeenBy {
            if (ArcartXEntityManager.getPlayer(it)?.supportsCustomPlayerHost() == true) sendAppearanceTo(it)
        }
    }

    override fun syncModelOnStartSeenBy(target: Player) {
        sendAppearanceTo(target)
    }



    fun setDefaultModel(scale: Double = 1.0) {
        if (changeAfterLegacyEvent(
                { it.copy(model = "__default_player__", scale = scale, variant = "DEFAULT_BBMODEL", profileId = "") },
                { PlayerVariantChangeEvent(player, PlayerModelVariant.DEFAULT_BBMODEL).call() }, true, false)) {
            PlayerModelUpdateEvent(player, "__default_player__").call()
        }
    }

    fun setCustomModel(modelID: String, scale: Double = 1.0) {
        if (changeAfterLegacyEvent(
                { it.copy(model = modelID, scale = scale, variant = "CUSTOM_BBMODEL", profileId = "") },
                { PlayerVariantChangeEvent(player, PlayerModelVariant.CUSTOM_BBMODEL).call() }, true, false)) {
            PlayerModelUpdateEvent(player, modelID).call()
        }
    }


    fun setAnimationPack(packId: String) {
        changeAfterLegacyEvent(
            { it.copy(packId = packId) },
            { PlayerAnimationPackChangeEvent(player, packId).call() }
        )
    }

    fun clearAnimationPack() {
        changeAfterLegacyEvent(
            { it.copy(packId = "") },
            { PlayerAnimationPackChangeEvent(player, "").call() }
        )
    }

    fun playFirstPersonAnimationByTime(animation: String, speed: Double, keepTime: Int){
        NetworkMessageSender.sendPlayerFirstPersonAnimationByTime(player, animation, keepTime, speed)
    }

    fun playFirstPersonAnimationByCountOf(animation: String, speed: Double, count: Int){
        NetworkMessageSender.sendPlayerFirstPersonAnimationByCount(player, animation, count, speed)
    }


    override fun startSeenBy(target: Player) {
        super.startSeenBy(target)
        NetworkMessageSender.sendSetController(target,player.uniqueId , controller?:"")
        extraModels.forEach { (key, value) ->
            NetworkMessageSender.sendAddExtraModel(target, player.uniqueId, key, value)
        }
        // 新观察者进入视野时，补发当前飞行态
        NetworkMessageSender.sendFlyingState(target, player.uniqueId, player.isFlying)
    }

    /** 每 tick 轮询：飞行态变化时按 seenBy 广播给观察者（vanilla 不同步其他玩家的 abilities.flying）。 */
    fun updateFlyingState() {
        val flying = player.isFlying
        if (flying != lastFlying) {
            lastFlying = flying
            player.doWithSeenBy { NetworkMessageSender.sendFlyingState(it, player.uniqueId, flying) }
        }
    }

    fun setController(controller: String) {
        this.controller = controller
        player.doWithSeenBy { NetworkMessageSender.sendSetController(it, player.uniqueId, controller) }
    }


    fun tryModel(modelID: String, scale: Double, time: Long){
        require(time >= 0) { "试穿时间不能为负数" }
        // A body-only projection avoids attaching the current host's bones/slots to a different rig.
        val preview = PlayerAppearance(model = modelID, scale = scale, variant = "CUSTOM_BBMODEL").validated()
        val previous = appearancePreview ?: appearanceState
        check(!appearanceCommitInProgress) { "不能在外观变更事件中再次提交外观" }
        appearanceCommitInProgress = true
        try {
            if (!PlayerAppearanceChangeEvent(player, previous, preview).call()) return
        } finally {
            appearanceCommitInProgress = false
        }
        appearancePreview = preview
        appearanceRevision++
        model = preview.model
        this.scale = preview.scale
        player.doWithSeenBy { sendAppearanceTo(it, previous, true) }
        AsteroidScheduler.cancelTask(tryModelTask)
        tryModelTask = AsteroidScheduler.runTaskLater(bukkitPlugin, Runnable {
            val displayed = appearancePreview ?: return@Runnable
            appearancePreview = null
            model = appearanceState.model
            this.scale = appearanceState.scale
            appearanceRevision++
            tryModelTask = null
            player.doWithSeenBy { sendAppearanceTo(it, displayed, true) }
        }, time / 50)
    }

    fun closeAppearance() {
        AsteroidScheduler.cancelTask(tryModelTask)
        tryModelTask = null
        appearancePreview = null
        clientCapabilities = emptySet()
    }


    fun setSlotItemStackOnlyClient(slotID : String, itemStack: ItemStack){
        NetworkMessageSender.sendSlotItemStack(player, slotID, itemStack)
    }

    fun removeSlotItemStackOnlyClient(slotID: String, startWith: Boolean){
        NetworkMessageSender.sendSlotItemRemove(player, slotID, startWith)
    }

    fun setSlotItemStack(slotID: String, itemStack: ItemStack){
        setSlotItemStackSnapshot(slotID, itemStack)
        PlayerExtraSlotUpdateEvent(player,slotID,itemStack).call()
        ArcartX.configs.slotFolder.setting[slotID]?.updateScriptArgs?.let {
            it.forEach{ arg ->
                ScriptManager.executeScript(arg.first, player, itemStack,arg.second)
            }
        }
    }


    fun refreshAttributeSlotItems(providerIdentifier: String, provider: AttributeProvider): Int {
        var changed = 0
        slotCache.keys.toList().sorted().forEach { slotID ->
            if (ArcartX.configs.slotFolder.setting[slotID]?.attribute != providerIdentifier) return@forEach
            val current = slotCache[slotID]?.takeUnless { it.type.isAir } ?: return@forEach
            val rebuilt = provider.rebuildItem(player, "ArcartX_Slot_$slotID", current.clone())
            require(rebuilt.amount == current.amount) {
                "Attribute provider $providerIdentifier changed stack amount while rebuilding extra slot $slotID"
            }
            require(!rebuilt.type.isAir) {
                "Attribute provider $providerIdentifier erased extra slot $slotID while rebuilding its presentation"
            }
            if (rebuilt != current) {
                setSlotItemStackSnapshot(slotID, rebuilt)
                changed++
            }
        }
        return changed
    }

    private fun setSlotItemStackSnapshot(slotID: String, itemStack: ItemStack){
        val snapshot = itemStack.clone()
        slotCache[slotID] = snapshot
        // 主线程序列化（NMS 安全）后异步落库，避免主线程阻塞在 DB 往返上
        ArcartX.databaseManager?.slotDatabase?.let { db ->
            val json = db.serializeItemStack(snapshot)
            AsteroidScheduler.runAsync(bukkitPlugin, Runnable {
                db.setSlotDataJson(player.uniqueId, slotID, json)
            })
        }
        setSlotItemStackOnlyClient(slotID, snapshot)
    }

    fun syncSlotCacheToClient(){
        slotCacheSyncSteps().forEach { it.invoke() }
    }

    /** 固定槽位 ID，实际轮到该槽位时读取当前物品，避免延迟同步恢复已经移除的物品。 */
    internal fun slotCacheSyncSteps(): Sequence<() -> Unit> {
        val slots = slotCache.keys.toList()
        return slots.asSequence().map { slotID ->
            step@{
                val itemStack = slotCache[slotID] ?: return@step
                setSlotItemStackOnlyClient(slotID, itemStack)
                PlayerExtraSlotUpdateEvent(player, slotID, itemStack).call()
                ArcartX.configs.slotFolder.setting[slotID]?.updateScriptArgs?.forEach { arg ->
                    ScriptManager.executeScript(arg.first, player, itemStack, arg.second)
                }
            }
        }
    }

    fun getSlotItemStack(slotID: String) : ItemStack? {
        return slotCache[slotID]
    }

    fun getTagCooldown(tag : String) : Long {
        if(tagCooldown.containsKey(tag)) {
            return max(tagCooldown[tag]!! - System.currentTimeMillis(), 0)
        }
        return 0
    }

    fun setTagCooldown(tag : String, time : Long) {
        tagCooldown[tag] = time + System.currentTimeMillis()
        NetworkMessageSender.sendItemCoolDown(player, tag, time)
    }


    fun setState(controller: String, state: String){
        player.doWithSeenBy { NetworkMessageSender.sendState(it,player.uniqueId , controller, state, 1.0, -1) }
    }

    fun setState(controller: String, state: String, speed: Double){
        player.doWithSeenBy { NetworkMessageSender.sendState(it,player.uniqueId , controller, state,speed, -1) }
    }

    fun setState(controller: String, state: String, speed: Double, moveBreak: Long){
        player.doWithSeenBy { NetworkMessageSender.sendState(it,player.uniqueId , controller, state,speed, moveBreak) }
    }

    fun setState(controller: String, state: String, speed: Double, moveBreak: Long, moveBreakFresh: Boolean){
        player.doWithSeenBy { NetworkMessageSender.sendState(it,player.uniqueId , controller, state,speed, moveBreak, moveBreakFresh) }
    }

    fun addWayPoint(id: String, title: String, waypointConfigId: String, x: Double, y: Double, z: Double) {
        NetworkMessageSender.sendWaypointCreate(player, id, title, waypointConfigId, x, y, z)
    }

    fun deleteWayPoint(id: String, regex: Boolean = false) {
        NetworkMessageSender.sendWaypointRemove(player, id,regex)
    }

    fun clearWayPoint() {
        NetworkMessageSender.sendWaypointRemoveAll(player)
    }

    fun addDamageDisplay(damageDisplayConfigId: String, x: Double, y: Double, z: Double, damage: Double) {
        NetworkMessageSender.sendDamageDisplay(player, damageDisplayConfigId, x, y, z, damage)
    }

    fun addDamageDisplay(damageDisplayConfigId: String, target: Entity, damage: Double) {
        val loc = target.location
        val x = loc.x
        val y = loc.y + target.height / 2
        val z = loc.z
        NetworkMessageSender.sendDamageDisplay(player, damageDisplayConfigId, x, y, z, damage)
    }




    fun playSoundForSelf(resourcePath: String, soundCategory: String, pitch: Float, keepTime: Int) {
        NetworkMessageSender.sendPlayEntitySound(
            player, resourcePath, player.uniqueId.toString(),
            soundCategory, 16,
            pitch.toDouble(), keepTime
        )
    }

    fun sendCustomPacket(id: String, vararg data: String) {
        NetworkMessageSender.sendCustomPacket(player, id, *data)
    }

    fun setClientTitle(text: String) {
        NetworkMessageSender.sendClientTitle(player, text)
    }

    fun parseAria(code: String) {
        NetworkMessageSender.sendExecuteScript(player, code, true)
    }

    fun setThirdPerson(enable: Boolean) {
        NetworkMessageSender.setThirdPerson(player, enable)
    }

    fun setViewLock(enable: Boolean) {
        NetworkMessageSender.setViewLock(player, if (enable) 2 else 0)
    }

    fun setViewLockMode(mode: Int) {
        NetworkMessageSender.setViewLock(player, mode)
    }

    fun setCameraFromPreset(id: String) {
        NetworkMessageSender.setCameraFromPreset(player, id)
    }

    fun setCameraLocation(offsetX: Double, offsetY: Double, offsetZ: Double, freeView: Boolean) {
        NetworkMessageSender.setCamera(player, offsetX, offsetY, offsetZ, freeView)
    }

    fun startSceneCamera(sceneId: String) {
        NetworkMessageSender.sendSceneCamera(player, sceneId)
    }

    fun stopSceneCamera(){
        NetworkMessageSender.sendSceneCameraStop(player)
    }

    fun enableShader(shader: String) {
        NetworkMessageSender.sendStartShader(player, shader)
    }

    fun disableShader() {
        NetworkMessageSender.sendCloseShader(player)
    }

    fun setSkyTexture(texturePath: String, forceNoCloud: Boolean) {
        NetworkMessageSender.sendSetSkyBoxTexture(player, texturePath, forceNoCloud)
    }

    fun clearSkyTexture() {
        NetworkMessageSender.sendClearSkyBoxTexture(player)
    }

    @Deprecated("弃用", replaceWith = ReplaceWith("spawnBedrockParticle(identifier, particleID, effectPosition)"))
    fun spawnBedrockParticle(
        id: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    ) {
        NetworkMessageSender.sendBedrockParticle(player, id, EffectPosition.location(x,y,z, pitch, yaw))
    }

    fun spawnBedrockParticle(
        identifier: String, particleID: String, effectPosition: EffectPosition
    ) {
        NetworkMessageSender.sendBedrockParticle(player, particleID, effectPosition)
    }




    fun playBlockAnimation(
        x: Int,
        y: Int,
        z: Int,
        animation: String,
        speed: Double,
        transitionTime: Int,
        keepTime: Long
    ) {
        NetworkMessageSender.sendBlockAnimation(player, x, y, z, animation, speed, transitionTime, keepTime)
    }

    fun spawnHammerCrackEffect(
        x: Int,
        y: Int,
        z: Int,
        radius: Float,
        depth: Float,
        `in`: Int,
        keep: Int,
        out: Int,
        mode: Int
    ) {
        NetworkMessageSender.sendHammerCrack(player, x, y, z, radius, depth, `in`, keep, out, mode)
    }

    fun sendChatCard(cardID: String, cardData: Map<String, String>) {
        NetworkMessageSender.sendCardMessage(player, cardID, cardData)
    }

    fun addWorldTexture(id: String, builder: WorldTextureBuilder, effectPosition: EffectPosition){
        NetworkMessageSender.sendWorldTexture(player, id, builder, effectPosition)
    }

    fun removeWorldTexture(id: String) {
        NetworkMessageSender.sendWorldTextureRemove(player, id)
    }





    enum class CostumeSlot(val id: String) {
        HEAD("HEAD"), BODY("BODY"), LEGS("LEGS"), FEET("FEET"), DECORATION("DECORATION")
    }

    enum class PlayerModelVariant {
        DEFAULT_BBMODEL, CUSTOM_BBMODEL
    }

    companion object {
        const val CUSTOM_PLAYER_HOST_CAPABILITY = PlayerHostCapabilities.CUSTOM_PLAYER_HOST
    }


}
