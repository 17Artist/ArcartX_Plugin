/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.listener

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Skull
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.*
import org.bukkit.persistence.PersistentDataType
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.arcartx.blockItemNamespace
import priv.seventeen.artist.arcartx.blockNamespace
import priv.seventeen.artist.arcartx.commons.link.ArcartXLinkManager
import priv.seventeen.artist.arcartx.core.area.ArcartXAreaManager
import priv.seventeen.artist.arcartx.core.editor.ArcartXEditorManager
import priv.seventeen.artist.arcartx.core.editor.area.ArcartXAreaEditor
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.event.client.ClientChannelEvent
import priv.seventeen.artist.arcartx.event.client.ClientInitializedEvent
import priv.seventeen.artist.arcartx.event.player.PlayerAreaEnterEvent
import priv.seventeen.artist.arcartx.event.player.PlayerAreaLeaveEvent
import priv.seventeen.artist.arcartx.event.player.PlayerExtraSlotUpdateEvent
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.nms.ItemBridge
import priv.seventeen.artist.arcartx.script.ScriptManager
import priv.seventeen.artist.arcartx.util.EntityUtils.doWithSeenBy
import priv.seventeen.artist.arcartx.util.ItemStackUtils.getCooldown
import priv.seventeen.artist.arcartx.util.ItemStackUtils.getTag
import priv.seventeen.artist.arcartx.util.ItemStackUtils.setCooldown
import priv.seventeen.artist.arcartx.util.PlayerUtils.arcartXHandler
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.ensureMainThread
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.cancelTask
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.runTaskTimer
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendDebug
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.asteroid.packet.PacketEvent
import priv.seventeen.artist.asteroid.packet.PacketListener
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle

@AutoListener(priority = EventPriority.HIGHEST)
fun onPlayerJoinEvent(event: PlayerJoinEvent) {
    val player = event.player
    try {
        val method = player.javaClass.getMethod("addChannel", String::class.java)
        method.invoke(player, "arcartx:main")
    } catch (e: Exception) {
        // 反射注册插件消息通道失败（不同服务端实现差异）：调试模式下记录便于排查
        bukkitPlugin.sendDebug("addChannel arcartx:main 失败: ${e.message}")
    }
    ClientChannelEvent(player).call()
    NetworkMessageSender.sendPlayerJoinPacket(event.player)
}

@AutoListener
fun onClientInitialized(event: ClientInitializedEvent.Start) {
    NetworkMessageSender.sendSetting(event.player)
}

@AutoListener
fun onClientInitialized(event: ClientInitializedEvent.End) {
    if (ArcartX.configs.setting.playerDefaultModelEnabled) {
        val p = ArcartXEntityManager.getPlayer(event.player) ?: return
        if (p.playerModel.isEmpty()) {
            p.setModel("__default_player__", 1.0)
        }
    }
}

@AutoListener
fun onPlayerChangeWorld(event: PlayerChangedWorldEvent){
    NetworkMessageSender.sendWorldChange(event.player, event.player.world)
    event.player.arcartXHandler?.startSeenBy(event.player)
}

@AutoListener(priority = EventPriority.LOWEST)
fun onPlayerLeaveServer(event: PlayerQuitEvent){
    ArcartXEntityManager.removePlayer(event.player)
    ArcartXEditorManager.removeEditorData(event.player.uniqueId)
}



@AutoListener
fun onSlotUpdateAttribute(event: PlayerExtraSlotUpdateEvent) {
    ArcartX.configs.slotFolder.setting[event.slotID]?.attribute?.let { attr ->
        ArcartXLinkManager.getAttributeProvider(attr)?.apply {
            val sourceID = "ArcartX_Slot_" + event.slotID
            replaceAttribute(event.player, sourceID, event.itemStack)
        }
    }
}

@AutoListener
fun onPlayerAttackLivingEvent(event: EntityDamageEvent) {
    if (event !is EntityDamageByEntityEvent) return
    if (event.damager is Player) {
        NetworkMessageSender.sendEntityInteraction(event.damager as Player, event.entity.uniqueId, 0)
    }
}



@AutoListener
fun onPlayerPlaceBlockEvent(event: BlockPlaceEvent) {
    if(!event.itemInHand.type.isAir && event.itemInHand.getTag("model").isNotEmpty() && event.itemInHand.type == Material.PLAYER_HEAD){
        val block = event.block
        if(block.state is Skull){
            val skull = block.state as Skull
            skull.setOwner("_arcartx_model_")
            skull.persistentDataContainer.set(
                blockNamespace,
                PersistentDataType.STRING,
                event.itemInHand.getTag("model")
            )
            val drop = event.itemInHand.clone()
            drop.amount = 1
            skull.persistentDataContainer.set(
                blockItemNamespace,
                PersistentDataType.STRING,
                ItemBridge.item2json(drop)
            )
            skull.update()
        }
    }
}

@AutoListener
fun onPlayerBreakBlockEvent(event: BlockBreakEvent){
    if(event.block.state is Skull){
        val skull = event.block.state as Skull
        if(skull.persistentDataContainer.has(blockItemNamespace, PersistentDataType.STRING)){
            val item = ItemBridge.json2Item(skull.persistentDataContainer.get(blockItemNamespace, PersistentDataType.STRING) ?: return)
            event.isDropItems = false
            event.block.world.dropItem(event.block.location, item)
        }
    }
}

@AutoListener
fun onPlayerInteractionLivingEvent(event: PlayerInteractEntityEvent) {
    NetworkMessageSender.sendEntityInteraction(event.player, event.rightClicked.uniqueId, 1)
}

@AutoListener
fun onAttackEntityEvent(event: EntityDamageEvent) {
    if (event !is EntityDamageByEntityEvent) return
    if (event.damager is Projectile) {
        if ((event.damager as Projectile).shooter is Player) {
            NetworkMessageSender.sendEntityInteraction((event.damager as Projectile).shooter  as Player, event.entity.uniqueId, 0)

        }
    }
}


@AutoListener(priority = EventPriority.LOWEST, ignoreCancelled = true)
fun onPlayerMove(event: PlayerMoveEvent) {
    // 仅朝向变化（坐标未变）时直接返回，避免无意义的区域计算
    if (event.from.x == event.to?.x && event.from.y == event.to?.y && event.from.z == event.to?.z) return
    val playerData = ArcartXEntityManager.players[event.player.uniqueId] ?: return
    val newArea = ArcartXAreaManager.getArea(event.player.location)
    val lastArea = playerData.area

    if (lastArea === newArea) return

    playerData.area = newArea

    lastArea?.let {
        PlayerAreaLeaveEvent(event.player, it, newArea).call()
        it.leaveScript?.forEach{ script ->
            ScriptManager.executeScript(script.first, event.player, script.second)
        }
    }
    newArea?.let {
        PlayerAreaEnterEvent(event.player, it).call()
        it.enterScript?.forEach{ script ->
            ScriptManager.executeScript(script.first, event.player, script.second)
        }
    }
}

@AutoListener
fun onPlayerInteract(event: PlayerInteractEvent) {
    if (!event.player.isOp) return
    val block = event.clickedBlock ?: return
    if (!ArcartXAreaEditor.checkTool(event.player.inventory.itemInMainHand)) return
    event.isCancelled = true
    if(event.player.inventory.itemInMainHand.getCooldown(event.player) > 0) return
    event.player.inventory.itemInMainHand.setCooldown(event.player, 1000)
    val player = ArcartXEntityManager.getPlayer(event.player) ?: return

    when (event.action) {
        Action.LEFT_CLICK_BLOCK -> ArcartXAreaEditor.handleLeftClick(event.player, player, block)
        Action.RIGHT_CLICK_BLOCK -> ArcartXAreaEditor.handleRightClick(event.player, player, block)
        else -> return
    }
}

@AutoListener
fun onPlayerSlotItemChange(event : PlayerItemHeldEvent){
    if (!event.player.isOp) return
    val item = event.player.inventory.getItem(event.newSlot)
    val data = ArcartXEditorManager.getOrCreateEditorData(event.player)
    if(item == null){
        if(data.areaEditEffectTask != null){
            cancelTask(data.areaEditEffectTask)
            data.areaEditEffectTask = null
        }
        return
    } else {
        if (!ArcartXAreaEditor.checkTool(item)) {
            if(data.areaEditEffectTask != null){
                cancelTask(data.areaEditEffectTask)
                data.areaEditEffectTask = null
            }
        } else {
            if(data.areaEditEffectTask == null){
                data.areaEditEffectTask = runTaskTimer(bukkitPlugin, Runnable {
                    ArcartXAreaEditor.startDraw(event.player)
                },0,20)
            }
        }
    }
}

private fun handlePacketReceive(player: Player, packetName: String, packet: Any) {
    when (packetName) {
        "PacketPlayInPosition", "PacketPlayInPositionLook", "Pos", "PosRot" -> {
            val fields = priv.seventeen.artist.asteroid.packet.PacketFields.of(packet)
            val onGround = fields.readTyped(Boolean::class.java, 0)
            val y = fields.readTyped(Double::class.java, 0)
            if (y as Double > player.location.y && player.isOnGround && !(onGround as Boolean)) {
                bukkitPlugin.ensureMainThread {
                    ArcartXEntityManager.players[player.uniqueId]?.let {
                        it.player.doWithSeenBy { p: Player -> NetworkMessageSender.sendPlayerJump(p, player) }
                        it.jumpTime = System.currentTimeMillis() + 500L
                    }
                }
            }
        }
    }
}

/** 玩家事件监听器 */
object ArcartXPacketListener : PacketListener {

    @Awake(LifeCycle.ACTIVE)
    fun register() {
        AsteroidAPI.addPacketListener(bukkitPlugin, this)
    }


    @Awake(LifeCycle.DISABLE)
    fun unregister() {
        AsteroidAPI.removePacketListener(bukkitPlugin, this)
    }

    override fun onReceive(event: PacketEvent) {
        handlePacketReceive(event.player, event.packetName, event.packet)
    }
}
