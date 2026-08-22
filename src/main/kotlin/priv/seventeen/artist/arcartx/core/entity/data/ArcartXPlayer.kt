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
import priv.seventeen.artist.arcartx.commons.link.attribute.AttributeProvider
import priv.seventeen.artist.arcartx.core.config.area.Area
import priv.seventeen.artist.arcartx.core.effect.data.EffectPosition
import priv.seventeen.artist.arcartx.core.effect.data.WorldTextureBuilder
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

    // 时装
    private var costumeSuit: String? = null
    private var costumeSuitHide: Boolean = false
    private val costumeSlots = HashMap<CostumeSlot, Pair<String, Boolean>>()

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
        super.setModel(modelID, scale)
        playerModel = modelID
        playerModelScale = scale
        PlayerModelUpdateEvent(player, modelID).call()
    }


    override fun removeModel() {
        super.removeModel()
        playerModel = ""
        playerModelScale = 1.0
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
        if (!PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.EQUIP_SUIT, null, modelID, hide).call()) return
        costumeSlots.clear()
        costumeSuit = modelID
        costumeSuitHide = hide
        broadcastCostume()
    }

    fun equipCostume(slot: CostumeSlot, modelID: String, hide: Boolean = false) {
        if (!PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.EQUIP_SLOT, slot, modelID, hide).call()) return
        costumeSuit = null
        costumeSlots[slot] = modelID to hide
        broadcastCostume()
    }

    fun removeCostume(slot: CostumeSlot) {
        if (!PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.REMOVE_SLOT, slot, null, false).call()) return
        costumeSuit = null
        costumeSlots.remove(slot)
        broadcastCostume()
    }

    fun clearCostume() {
        if (!PlayerCostumeChangeEvent(player, PlayerCostumeChangeEvent.Type.CLEAR, null, null, false).call()) return
        costumeSuit = null
        costumeSlots.clear()
        broadcastCostume()
    }

    private fun costumeSlotsDto(): Map<String, SPackCostume.Slot> =
        costumeSlots.entries.associate { (slot, v) -> slot.id to SPackCostume.Slot(v.first, v.second) }

    private fun broadcastCostume() {
        val dto = costumeSlotsDto()
        player.doWithSeenBy { NetworkMessageSender.sendCostume(it, player.uniqueId, costumeSuit, costumeSuitHide, dto) }
    }


    private fun broadcastVariant() {
        player.doWithSeenBy { NetworkMessageSender.sendPlayerVariant(it, player.uniqueId, variant.name) }
    }



    fun setDefaultModel(scale: Double = 1.0) {
        if (!PlayerVariantChangeEvent(player, PlayerModelVariant.DEFAULT_BBMODEL).call()) return
        this.variant = PlayerModelVariant.DEFAULT_BBMODEL
        setModel("__default_player__", scale)
        broadcastVariant()
    }

    fun setCustomModel(modelID: String, scale: Double = 1.0) {
        if (!PlayerVariantChangeEvent(player, PlayerModelVariant.CUSTOM_BBMODEL).call()) return
        this.variant = PlayerModelVariant.CUSTOM_BBMODEL
        setModel(modelID, scale)
        broadcastVariant()
    }


    private fun broadcastAnimationPack() {
        player.doWithSeenBy { NetworkMessageSender.sendAnimationPack(it, player.uniqueId, animationPackId) }
    }

    fun setAnimationPack(packId: String) {
        if (!PlayerAnimationPackChangeEvent(player, packId).call()) return
        this.animationPackId = packId
        broadcastAnimationPack()
    }

    fun clearAnimationPack() {
        if (!PlayerAnimationPackChangeEvent(player, "").call()) return
        this.animationPackId = ""
        broadcastAnimationPack()
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
        // 新观察者进入视野时，补发当前时装(运行时内存态，无 DB)
        if (costumeSuit != null || costumeSlots.isNotEmpty()) {
            NetworkMessageSender.sendCostume(target, player.uniqueId, costumeSuit, costumeSuitHide, costumeSlotsDto())
        }
        // 补发当前变体(仅非默认；DEFAULT 是客户端默认值，无需下发)
        if (variant != PlayerModelVariant.DEFAULT_BBMODEL) {
            NetworkMessageSender.sendPlayerVariant(target, player.uniqueId, variant.name)
        }
        // 补发当前动画包(仅非空)
        if (animationPackId.isNotEmpty()) {
            NetworkMessageSender.sendAnimationPack(target, player.uniqueId, animationPackId)
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
        super.setModel(modelID, scale)
        AsteroidScheduler.cancelTask(tryModelTask)
        tryModelTask = AsteroidScheduler.runTaskLater(bukkitPlugin, Runnable {
            super.setModel(playerModel, playerModelScale)
        }, time / 50)
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
        slotCache.forEach { (slotID, itemStack) ->
            setSlotItemStackOnlyClient(slotID, itemStack)
            PlayerExtraSlotUpdateEvent(player,slotID,itemStack).call()
            ArcartX.configs.slotFolder.setting[slotID]?.updateScriptArgs?.let {
                it.forEach{ arg ->
                    ScriptManager.executeScript(arg.first, player, itemStack,arg.second)
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


}
