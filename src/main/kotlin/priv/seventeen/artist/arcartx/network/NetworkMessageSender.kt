/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network

import io.netty.buffer.Unpooled
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.arcartx.core.config.camera.CameraElement
import priv.seventeen.artist.arcartx.core.config.card.ChatCard
import priv.seventeen.artist.arcartx.core.config.ui.type.UI
import priv.seventeen.artist.arcartx.core.effect.data.EffectPosition
import priv.seventeen.artist.arcartx.core.effect.data.WorldTextureBuilder
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.event.client.ClientInitializedEvent
import priv.seventeen.artist.arcartx.network.encryptor.Base64Encryptor
import priv.seventeen.artist.arcartx.network.message.DecodeType
import priv.seventeen.artist.arcartx.network.message.MessageID
import priv.seventeen.artist.arcartx.network.packet.server.*
import priv.seventeen.artist.arcartx.util.ByteArrayUtils
import priv.seventeen.artist.arcartx.util.JsonUtils.toJson
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.ensureAsyncThread
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.ensureMainThread
import priv.seventeen.artist.blink.bukkitPlugin
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Consumer

/** 网络消息发送器，封装所有服务端到客户端的数据包发送逻辑 */
object NetworkMessageSender {

    fun sendPlayerJoinPacket(player: Player) {
        sendPacketAsync(player, MessageID.Server.CONNECTION, DecodeType.NORMAL, SPackConnection(player))
    }

    fun sendResourceReload(player: Player, init: Boolean) {
        if (init) {
            bukkitPlugin.ensureMainThread {
                ClientInitializedEvent.Start(player).call()
            }
        }
        sendPacketAsync(player, MessageID.Server.RESOURCE_RELOAD, DecodeType.AES, SPackResourceReload(!init))
    }

    fun sendResourceReload(player: Player, files: Map<String, String>) {
        bukkitPlugin.ensureMainThread {
            ClientInitializedEvent.Start(player).call()
        }
        sendPacketAsync(player, MessageID.Server.RESOURCE_RELOAD, DecodeType.AES, SPackResourceReload(files))
    }

    fun sendExecuteScript(player: Player, code: String, async: Boolean) {
        sendPacketAsync(player, MessageID.Server.EXECUTE_ARIA, DecodeType.NORMAL, SPackExecuteScript(code, async))
    }

    fun sendSetting(player: Player) {
        sendPacketAsync(player, MessageID.Server.SETTING, DecodeType.NORMAL, SPackSettings())
    }

    fun sendSettingReload(player: Player) {
        sendPacketAsync(player, MessageID.Server.SETTING, DecodeType.NORMAL, SPackSettings(true))
    }

    fun sendWaypointCreate(player: Player, id: String, title: String, dataId: String, x: Double, y: Double, z: Double) {
        sendPacketAsync(player, MessageID.Server.WAYPOINT_CREATE, DecodeType.NORMAL, SPackWaypointCreate(id, title, dataId, x, y, z))
    }

    fun sendWaypointRemove(player: Player, id: String, regex: Boolean) {
        sendPacketAsync(player, MessageID.Server.WAYPOINT_DELETE, DecodeType.NORMAL, SPackWaypointRemove(id,regex,false))
    }

    fun sendWaypointRemoveAll(player: Player) {
        sendPacketAsync(player, MessageID.Server.WAYPOINT_DELETE, DecodeType.NORMAL, SPackWaypointRemove("",
            regex = false,
            clear = true
        ))
    }

    fun sendDamageDisplay(player: Player, dataId: String, x: Double, y: Double, z: Double, damage: Double) {
        sendPacketAsync(player, MessageID.Server.DAMAGE_DISPLAY, DecodeType.NORMAL, SPackDamageDisplay(dataId, damage, x, y, z))
    }

    fun sendCostume(player: Player, target: UUID, suit: String?, suitHide: Boolean, slots: Map<String, SPackCostume.Slot>) {
        sendPacketSync(player, MessageID.Server.COSTUME, DecodeType.NORMAL, SPackCostume(target, suit, suitHide, slots))
    }

    fun sendPlayerVariant(player: Player, target: UUID, variant: String) {
        sendPacketSync(player, MessageID.Server.PLAYER_VARIANT, DecodeType.NORMAL, SPackPlayerVariant(target, variant))
    }

    fun sendAnimationPack(player: Player, target: UUID, packId: String) {
        sendPacketSync(player, MessageID.Server.ANIMATION_PACK, DecodeType.NORMAL, SPackAnimationPack(target, packId))
    }



    fun sendPlaySound(
        player: Player,
        path: String,
        x: Int,
        y: Int,
        z: Int,
        soundCategory: String,
        distOrRoll: Int,
        pitch: Double,
        keepTime: Int
    ) {
        sendPacketAsync(
            player,
            MessageID.Server.LOCATION_SOUND,
            DecodeType.NORMAL,
            SPackPlaySound(path, x, y, z, soundCategory, distOrRoll, pitch, keepTime)
        )
    }

    fun sendPlayNameSound(
        player: Player,
        path: String,
        x: Int,
        y: Int,
        z: Int,
        soundCategory: String,
        distOrRoll: Int,
        pitch: Double,
        keepTime: Int,
        key: String
    ) {
        sendPacketAsync(
            player,
            MessageID.Server.LOCATION_SOUND,
            DecodeType.NORMAL,
            SPackPlaySound(key, path, x, y, z, soundCategory, distOrRoll, pitch, keepTime)
        )
    }

    fun sendStopSound(player: Player, key: String) {
        sendPacketAsync(player, MessageID.Server.LOCATION_SOUND, DecodeType.NORMAL, SPackPlaySound(key, true))
    }


    fun sendPlayEntitySound(
        player: Player,
        path: String,
        entity: String,
        soundCategory: String,
        distOrRoll: Int,
        pitch: Double,
        keepTime: Int,
        name: String? = null
    ) {
        sendPacketAsync(
            player,
            MessageID.Server.ENTITY_SOUND,
            DecodeType.NORMAL,
            SPackPlayEntitySound(path, entity, soundCategory, distOrRoll, pitch, keepTime, name)
        )
    }



    fun sendClientPlaceholder(player: Player, key: String, value: String) {
        sendPacketSync(player, MessageID.Server.PAPI, DecodeType.NORMAL, SPackPlaceholder(key, value, false, ""))
    }

    fun sendServerVariable(player: Player, key: String, value: Any) {
        sendPacketSync(player, MessageID.Server.PAPI, DecodeType.NORMAL, SPackPlaceholder(key, value, false, ""))
    }

    fun sendRemoveServerVariable(player: Player, key: String, startWith: Boolean = false) {
        sendPacketSync(
            player,
            MessageID.Server.REMOVE_VARIABLE,
            DecodeType.NORMAL,
            SPackRemoveVariable(key,startWith)
        )
    }

    fun sendScreenPacket(player: Player, key: String, value: Any, updateID: String) {
        sendPacketSync(
            player,
            MessageID.Server.PAPI,
            DecodeType.NORMAL,
            SPackPlaceholder(key, value, true, updateID)
        )
    }

    fun sendOpenUI(player: Player, screenID: String) {
        sendPacketSync(
            player,
            MessageID.Server.SCREEN_COMMAND,
            DecodeType.NORMAL,
            SPackScreenCommand("open", screenID)
        )
    }

    fun sendCloseUI(player: Player, screenID: String) {
        sendPacketSync(
            player,
            MessageID.Server.SCREEN_COMMAND,
            DecodeType.NORMAL,
            SPackScreenCommand("close", screenID)
        )
    }

    fun sendUIRunCode(player: Player, screenID: String, code: String) {
        sendPacketSync(
            player,
            MessageID.Server.SCREEN_COMMAND,
            DecodeType.NORMAL,
            SPackScreenCommand("run", screenID, code)
        )
    }


    fun sendPlayerGlowColor(player: Player, target: UUID, r: Int, g: Int, b: Int) {
        sendPacketSync(
            player, MessageID.Server.ENTITY_GLOW, DecodeType.NORMAL, SPackEntityGlow(
                target, "color",
                "$r,$g,$b"
            )
        )
    }

    fun sendEntityGlowEnable(player: Player, target: UUID) {
        sendPacketSync(player, MessageID.Server.ENTITY_GLOW, DecodeType.NORMAL, SPackEntityGlow(target, "glow", ""))
    }

    fun sendEntityGlowDisable(player: Player, target: UUID) {
        sendPacketSync(player, MessageID.Server.ENTITY_GLOW, DecodeType.NORMAL, SPackEntityGlow(target, "unglow", ""))
    }

    fun sendBase64Image(player: Player, name: String, base64: String) {
        sendPacketAsync(player, MessageID.Server.BASE64_IMAGE, DecodeType.NORMAL, SPackBase64Image(name, base64))
    }


    fun sendItemCoolDown(player: Player, key: String, time: Long) {
        sendPacketSync(player, MessageID.Server.ITEM_COOLDOWN, DecodeType.NORMAL, SPackItemCoolDown(key, time))
    }

    // 槽位
    fun sendSlotItemStack(player: Player, id: String, itemStack: ItemStack) {
        sendPacketSync(player, MessageID.Server.SLOT_ITEM_STACK, DecodeType.NORMAL, SPackSlotItemStack(id, itemStack))
    }


    fun sendSlotItemRemove(player: Player, id: String, isFirstWith: Boolean) {
        sendPacketSync(
            player,
            MessageID.Server.SLOT_ITEM_STACK,
            DecodeType.NORMAL,
            SPackSlotItemStack(id,  isFirstWith, true)
        )
    }

    // 自定义包
    fun sendCustomPacket(player: Player, key: String, vararg value: String) {
        sendPacketSync(
            player,
            MessageID.Server.CUSTOM,
            DecodeType.NORMAL,
            SPackCustomPacket(key, listOf(*value))
        )
    }

    fun sendStartShader(player: Player, name: String) {
        sendPacketAsync(player, MessageID.Server.SHADER, DecodeType.NORMAL, SPackShader(name, true))
    }

    fun sendCloseShader(player: Player) {
        sendPacketAsync(player, MessageID.Server.SHADER, DecodeType.NORMAL, SPackShader("", false))
    }

    fun sendSetSkyBoxTexture(player: Player, path: String, forceNoCloud: Boolean) {
        sendPacketAsync(player, MessageID.Server.SKYBOX, DecodeType.NORMAL, SPackSkyBox(path, forceNoCloud, false))
    }

    fun sendClearSkyBoxTexture(player: Player) {
        sendPacketAsync(player, MessageID.Server.SKYBOX, DecodeType.NORMAL, SPackSkyBox("",
            forceNoCloud = false,
            clear = true
        ))
    }

    fun sendWorldChange(player: Player, world: World) {
        sendPacketSync(player, MessageID.Server.WORLD_CHANGE, DecodeType.NORMAL, SPackWorldChange(world.name))
    }

    fun sendEntityInteraction(player: Player, uuid: UUID, type: Int) {
        sendPacketAsync(player, MessageID.Server.ENTITY_INTERACTION, DecodeType.NORMAL, SPackInteraction(uuid, type))
    }

    fun sendPlayerJump(player: Player, jumper: Player) {
        sendPacketAsync(player, MessageID.Server.PLAYER_JUMP, DecodeType.NORMAL, SPackPlayerJump(jumper))
    }

    fun sendState(player: Player, target: UUID, controller: String, state: String, speed: Double, moveBreak: Long, moveBreakFresh: Boolean = false) {
        sendPacketSync(
            player,
            MessageID.Server.STATE_CHANGE,
            DecodeType.NORMAL,
            SPackSetState(target, controller, state, speed, moveBreak, moveBreakFresh),
        )
    }

    fun sendSetController(player: Player, target: UUID, controller: String) {
        sendPacketSync(player, MessageID.Server.CONTROLLER, DecodeType.NORMAL, SPackSetController(target, controller))
    }

    fun sendFlyingState(player: Player, target: UUID, flying: Boolean) {
        sendPacketAsync(player, MessageID.Server.FLYING_STATE, DecodeType.NORMAL, SPackFlyingState(target, flying))
    }

    fun sendSceneCamera(player: Player, name: String) {
        ArcartX.configs.sceneCameraFolder.configs[name]?.let {
            sendPacketAsync(player, MessageID.Server.SCENE_CAMERA, DecodeType.NORMAL, SPackSceneCamera(it))
        }
    }

    fun sendSceneCameraStop(player: Player) {
        sendPacketAsync(player, MessageID.Server.SCENE_CAMERA, DecodeType.NORMAL, SPackSceneCamera(null))
    }

    fun sendClientTitle(player: Player, text: String) {
        sendPacketAsync(player, MessageID.Server.CLIENT_TITLE, DecodeType.NORMAL, SPackClientTitle(text))
    }

    fun setCamera(player: Player, x: Double, y: Double, z: Double, freeView: Boolean) {
        sendPacketAsync(player, MessageID.Server.CAMERA_SET, DecodeType.NORMAL, SPackCamera(x, y, z, freeView))
    }

    fun setCameraFromPreset(player: Player, presetName: String) {
        sendPacketAsync(player, MessageID.Server.CAMERA_PRESET, DecodeType.NORMAL,
            SPackCameraPreset(presetName)
        )
    }

    fun setCameraFromElement(player: Player, element: CameraElement) {
        sendPacketAsync(player, MessageID.Server.CAMERA_ELEMENT, DecodeType.NORMAL, SPackCameraElement(element))
    }

    fun sendController(player: Player) {
        sendPacketSync(player, MessageID.Server.CONTROLLER_DATA, DecodeType.NORMAL, SPacketController())
    }

    fun sendHideName(player: Player, target: UUID, hide: Boolean) {
        sendPacketSync(player, MessageID.Server.ENTITY_HIDE_NAME, DecodeType.NORMAL, SPackHideName(target, hide))
    }

    fun sendUI(player: Player, ui: UI) {
        sendPacketSync(player, MessageID.Server.SCREEN, DecodeType.NORMAL, SPackScreen(ui))
    }


    fun setViewLock(player: Player, lockMode: Int) {
        sendPacketSync(player, MessageID.Server.CAMERA_LOCK_MODE, DecodeType.NORMAL, SPackLockView(lockMode))
    }

    fun setThirdPerson(player: Player, thirdPerson: Boolean) {
        sendPacketSync(player, MessageID.Server.CAMERA_THIRD_PERSON, DecodeType.NORMAL, SPackThirdPerson(thirdPerson))
    }

    /**
     * 令玩家转向 [yaw]。[strength] > 0 时，纯自由相机模式下相机会每帧平滑贴合到该朝向（类二游式软跟随）；
     * [strength] = 0（默认）时玩家转向但相机不跟随。多次调用叠加=相机平滑改朝最新朝向（latest-wins）。
     * @param strength 相机跟随强度（每 1/60s 向目标闭合比例 0-1，越大越快贴合）。
     */
    fun sendPlayerLook(player: Player, yaw: Float, strength: Float = 0f) {
        sendPacketSync(player, MessageID.Server.PLAYER_LOOK, DecodeType.NORMAL, SPackPlayerLook(yaw, strength))
    }



    fun setEntityModel(player: Player, entity: UUID, modelID: String, scale: Double) {
        sendPacketSync(
            player,
            MessageID.Server.ENTITY_MODEL,
            DecodeType.NORMAL,
            SPackEntityModel(entity, modelID, scale)
        )
    }

    fun setEntityModel(player: Player, entity: UUID, modelID: String, scale: Double, reset : Boolean) {
        sendPacketSync(
            player,
            MessageID.Server.ENTITY_MODEL,
            DecodeType.NORMAL,
            SPackEntityModel(entity, modelID, scale, reset)
        )
    }

    fun sendEntitySize(player: Player, entity: UUID, width: Double, height: Double) {
        sendPacketSync(
            player,
            MessageID.Server.ENTITY_SIZE,
            DecodeType.NORMAL,
            SPackEntitySize(entity, width, height)
        )
    }

    fun sendEntityAnimation(
        player: Player,
        entity: UUID,
        animationName: String,
        speed: Double,
        transitionTime: Int,
        time: Long
    ) {
        sendPacketSync(
            player,
            MessageID.Server.ENTITY_ANIMATION,
            DecodeType.NORMAL,
            SPackAnimation(entity, animationName, speed, transitionTime, time)
        )
    }



    fun sendEntityDefaultAnimationState(player: Player, entity: UUID, animationName: String, toName: String) {
        sendPacketSync(
            player,
            MessageID.Server.ENTITY_ANIMATION_DEFAULT_STATE,
            DecodeType.NORMAL,
            SPackEntityDefaultState(entity, animationName, toName)
        )
    }

    fun sendEntityHideBone(player: Player, entity: UUID, boneName: String, hide: Boolean) {
        sendPacketSync(
            player,
            MessageID.Server.ENTITY_HIDE_BONE,
            DecodeType.NORMAL,
            SPackHideBone(entity, boneName, hide)
        )
    }

    fun sendModelEffect(player: Player, modelID: String, scale: Float, keepTime: Int, glow: Boolean, effectPosition: EffectPosition) {
        sendPacketSync(
            player,
            MessageID.Server.MODEL_EFFECT,
            DecodeType.NORMAL,
            SPackModelEffect(modelID, scale, keepTime, glow, effectPosition)
        )
    }

    fun sendAdyeshachJoin(player: Player, uuid: UUID) {
        sendPacketSync(player, MessageID.Server.ADYESHACH_JOIN, DecodeType.NORMAL, SPackAdyeshachJoin(uuid))
    }

    fun sendHammerCrack(player: Player, x: Int, y: Int, z: Int, radius: Float, depth: Float, `in`: Int, keep: Int, out: Int, mode: Int) {
        sendPacketSync(
            player,
            MessageID.Server.HAMMER_CRACK,
            DecodeType.NORMAL,
            SPackHammerCrackEffect(x, y, z, radius, depth, `in`, keep, out, mode)
        )
    }


    fun sendBlockAnimation(player: Player, x: Int, y: Int, z: Int, animation: String, speed: Double, transitionTime: Int, keepTime: Long) {
        sendPacketSync(
            player,
            MessageID.Server.BLOCK_ANIMATION,
            DecodeType.NORMAL,
            SPackBlockAnimation(x, y, z, animation, speed, transitionTime, keepTime)
        )
    }

    fun sendAddExtraModel(player: Player, target: UUID, locator: String, modelID: String) {
        sendPacketSync(
            player,
            MessageID.Server.EXTRA_MODEL,
            DecodeType.NORMAL,
            SPackExtraModel(target, locator, false, modelID)
        )
    }

    fun sendRemoveExtraModel(player: Player, target: UUID, locator: String) {
        sendPacketSync(
            player,
            MessageID.Server.EXTRA_MODEL,
            DecodeType.NORMAL,
            SPackExtraModel(target, locator, true, "")
        )
    }

    fun sendClearExtraModel(player: Player, target: UUID) {
        sendPacketSync(
            player,
            MessageID.Server.EXTRA_MODEL,
            DecodeType.NORMAL,
            SPackExtraModel(target, "", true, "clear_all")
        )
    }

    fun sendCardMessage(player: Player, cardID: String, cardData: Map<String, String>) {
        sendPacketSync(
            player,
            MessageID.Server.CARD_MESSAGE,
            DecodeType.NORMAL,
            SPackCardMessage(cardID, cardData)
        )
    }

    fun sendChatCardConfig(player: Player, chatCard: ChatCard) {
        sendPacketSync(
            player,
            MessageID.Server.CHAT_CARD_CONFIG,
            DecodeType.NORMAL,
            SPackChatCardConfig.update(chatCard)
        )
    }

    fun sendChatCardConfigRemove(player: Player, id: String) {
        sendPacketSync(
            player,
            MessageID.Server.CHAT_CARD_CONFIG,
            DecodeType.NORMAL,
            SPackChatCardConfig.remove(id)
        )
    }

    fun sendBlockModel(player: Player, x: Int, y: Int, z: Int, modelID: String) {
        sendPacketSync(player, MessageID.Server.BLOCK_MODEL, DecodeType.NORMAL, SPackBlockModel(x, y, z, modelID))
    }

    fun sendSlotDebug(player: Player, enable: Boolean){
        sendPacketAsync(player, MessageID.Server.SLOT_DEBUG, DecodeType.NORMAL, SPackSlotDebug(enable))
    }

    fun sendWorldTexture(player: Player, id: String, builder: WorldTextureBuilder, effectPosition: EffectPosition) {
        sendPacketAsync(player, MessageID.Server.WORLD_TEXTURE, DecodeType.NORMAL, SPackWorldTexture(id, builder, effectPosition))
    }

    fun sendWorldTextureRemove(player: Player, id: String) {
        sendPacketAsync(player, MessageID.Server.WORLD_TEXTURE_REMOVE, DecodeType.NORMAL, SPackRemoveWorldTexture(id))
    }

    fun sendBedrockParticle(player: Player, particleID: String, effectPosition: EffectPosition) {
        sendPacketAsync(player, MessageID.Server.BEDROCK_PARTICLES, DecodeType.NORMAL, SPackBedrockParticle(particleID, effectPosition))
    }

    fun sendPlayerFirstPersonAnimationByTime(player: Player, animation: String, keepTime: Int, speed: Double) {
        sendPacketAsync(player, MessageID.Server.FIRST_PERSON_ANIMATION, DecodeType.NORMAL, SPackFirstPersonAnimation(animation, keepTime, speed, false))
    }

    fun sendPlayerFirstPersonAnimationByCount(player: Player, animation: String, count: Int, speed: Double) {
        sendPacketAsync(player, MessageID.Server.FIRST_PERSON_ANIMATION, DecodeType.NORMAL, SPackFirstPersonAnimation(animation, count, speed, true))
    }


    fun sendOpenEditor(player: Player){
        sendPacketSync(player, MessageID.Server.OPEN_EDITOR, DecodeType.NORMAL, SPackOpenEditor())
    }

    fun sendFiles(player: Player, files: Map<String, String>){
        if(files.isEmpty()) return
        sendPacketAsync(player, MessageID.Server.FILE_LIST, DecodeType.NORMAL, SPackFilesUpdate(files))
    }

    fun setEntityHitBoxHide(player: Player,entity: UUID, hide: Boolean) {
        sendPacketSync(player, MessageID.Server.HIDE_HIT_BOX, DecodeType.NORMAL, SPackHideHitBox(entity, hide))
    }

    fun sendModelEffect(player: Player, identifier: String, modelID: String, animation: String, speed: Double, scale: Float, keepTime: Int, glow: Boolean, effectPosition: EffectPosition) {
        sendPacketSync(
            player,
            MessageID.Server.NAMED_MODEL_EFFECT,
            DecodeType.NORMAL,
            SPackNamedModelEffect(identifier,modelID, animation, speed, scale, keepTime, glow, effectPosition)
        )
    }

    fun sendRemoveModelEffect(player: Player, identifier: String){
        sendPacketSync(
            player,
            MessageID.Server.REMOVE_NAMED_EFFECT,
            DecodeType.NORMAL,
            SPackNamedModelEffectRemove(identifier)
        )
    }

    fun sendShake(player: Player, duration: Int, amplitude: Int){
        sendPacketAsync(player, MessageID.Server.CAMERA_SHAKE ,DecodeType.NORMAL, SPackShake(duration, amplitude))
    }

    fun setUse(player: Player, enable: Boolean){
        sendPacketSync(player, MessageID.Server.SET_USE, DecodeType.NORMAL, SPackSetUse(enable))
    }

    private val idCounter = java.util.concurrent.atomic.AtomicInteger(0)

    private fun getNextID(): Int {
        return idCounter.getAndIncrement()
    }

    fun sendPacketSync(player: Player, messageID: MessageID, decodeType: DecodeType, packet: ServerPacket) {
        bukkitPlugin.ensureMainThread { buildAndSend(player, messageID, decodeType, packet) }
    }

    fun sendPacketAsync(player: Player, messageID: MessageID, decodeType: DecodeType, packet: ServerPacket) {
        bukkitPlugin.ensureAsyncThread { buildAndSend(player, messageID, decodeType, packet) }
    }

    /** 编码 + base64/AES + 分块 + 写通道；sync/async 共用，仅线程调度不同 */
    private fun buildAndSend(player: Player, messageID: MessageID, decodeType: DecodeType, packet: ServerPacket) {
        val messages: List<BaseMessage> = createMessage { message: BaseMessage ->
            message.type = decodeType.id
            message.code = messageID.id
            message.id = getNextID()
            // base64 编码
            if (decodeType === DecodeType.AES) {
                ArcartXEntityManager.getPlayer(player)?.encryptor?.encode(packet.toJson())?.let {
                    message.message = it
                }
            } else {
                message.message = Base64Encryptor.base64Encrypt(packet.toJson())
            }
        }
        messages.forEach(Consumer { message: BaseMessage ->
            val bytes: ByteArray = message.toJson().toByteArray(StandardCharsets.UTF_8)
            val processed = ByteArrayUtils.compressIfNeeded(bytes)
            val buf = Unpooled.buffer(processed.size + 1)
            buf.writeByte(NetworkManager.COMPRESSED_FLAG)
            buf.writeBytes(processed)
            player.sendPluginMessage(bukkitPlugin, NetworkManager.CHANNEL, buf.array())
            buf.release()
        })
    }

    private fun createMessage(packet: IPacketWriter): ArrayList<BaseMessage> {
        val result = ArrayList<BaseMessage>()

        val message = BaseMessage()
        packet.write(message)

        if (message.message.length > 30000) {
            val size: Int = (message.message.length / 30000) + (if (message.message.length % 30000 > 0) 1 else 0)

            for (part in 0 until size) {
                val mul = getBaseMessage(message, part, size)
                result.add(mul)
            }
        } else {
            result.add(message)
        }

        return result
    }

    private fun getBaseMessage(message: BaseMessage, part: Int, size: Int): BaseMessage {
        val mul = BaseMessage()
        mul.type = message.type
        mul.id = message.id
        mul.code = message.code
        val cutString: String = if (part + 1 == size) {
            message.message.substring(30000 * part)
        } else {
            message.message.substring(30000 * part, 30000 * (part + 1))
        }
        mul.message = cutString

        mul.part = part
        mul.size = size
        return mul
    }

    private fun interface IPacketWriter {
        fun write(message: BaseMessage)
    }






}
