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
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler
import priv.seventeen.artist.arcartx.nms.EntityBridge
import priv.seventeen.artist.arcartx.util.EntityUtils.doWithSeenBy
import priv.seventeen.artist.blink.bukkitPlugin

open class ArcartXEntity(val entity: Entity) {

    private val uniqueId = entity.uniqueId

    protected var model: String = ""

    protected var scale = 1.0

    private var size: Pair<Double, Double> = Pair(-1.0, -1.0)

    private val hideBone: MutableSet<String> = HashSet()

    private val defaultState: MutableMap<String, String> = HashMap()

    private var displayName = true

    private var glowColor: IntArray? = null

    private var glowEnable = false

    private var hideHitBox = false

    fun isExpired(): Boolean {
        return !this.entity.isValid
    }

    /**
     * 为该实体设置模型。
     * @param modelID 模型 ID
     * @param scale 缩放
     */
    open fun setModel(modelID: String, scale: Double) {
        this.model = modelID
        this.scale = scale
        entity.doWithSeenBy { NetworkMessageSender.setEntityModel(it, uniqueId, modelID, scale)  }
     }

    /**
     * 为该实体设置模型，并可指定是否主动清除客户端既有模型状态。
     * reset=false 不主动清除状态；资源变化时必要的绑定重建仍会执行，不保证跨骨架动作续播。
     * @param modelID 模型 ID
     * @param scale 缩放
     * @param reset 是否请求重置，false 保留已有状态；不同模型资源仍需进行必要的绑定重建
     */
    open fun setModel(modelID: String, scale: Double, reset: Boolean) {
        this.model = modelID
        this.scale = scale
        entity.doWithSeenBy { NetworkMessageSender.setEntityModel(it, uniqueId, modelID, scale, reset)  }
    }

    open fun removeModel(){
        this.model = ""
        this.scale = 1.0
        entity.doWithSeenBy {  NetworkMessageSender.setEntityModel(it, uniqueId, "", scale) }
    }

    fun setSize(width: Double, height: Double) {
        AsteroidScheduler.ensureMainThread(bukkitPlugin) {
            this.size = Pair(width, height)
            EntityBridge.setEntitySize(entity, width.toFloat(), height.toFloat())
            entity.doWithSeenBy { NetworkMessageSender.sendEntitySize(it, uniqueId, width, height)  }
        }
    }

    fun setHideBone(bone: String, hide: Boolean) {
        if (hide) {
            hideBone.add(bone)
        } else {
            hideBone.remove(bone)
        }
        entity.doWithSeenBy { NetworkMessageSender.sendEntityHideBone(it, uniqueId, bone, hide)  }
    }

    fun setDefaultState(state: String, name: String) {
        defaultState[state] = name
        entity.doWithSeenBy { NetworkMessageSender.sendEntityDefaultAnimationState(it, uniqueId, state, name)  }

    }

    fun setDisplayName(displayName: Boolean) {
        this.displayName = displayName
        entity.doWithSeenBy { NetworkMessageSender.sendHideName(it, uniqueId, !displayName)  }
    }

    fun setGlowColor(r: Int, g: Int, b: Int) {
        this.glowColor = intArrayOf(r, g, b)
        entity.doWithSeenBy { NetworkMessageSender.sendPlayerGlowColor(it, uniqueId, r, g, b) }
    }

    fun enableGlow() {
        this.glowEnable = true
        entity.doWithSeenBy {  NetworkMessageSender.sendEntityGlowEnable(it, uniqueId)  }
    }

    fun disableGlow() {
        this.glowEnable = false
        entity.doWithSeenBy { NetworkMessageSender.sendEntityGlowDisable(it, uniqueId)  }
    }

    fun playAnimation(animation: String, speed: Double, transitionTime: Int, keepTime: Long) {
        entity.doWithSeenBy { NetworkMessageSender.sendEntityAnimation(it, uniqueId, animation, speed, transitionTime, keepTime)  }
    }

    fun playSound(resourcePath: String, soundCategory: String, distOrRoll: Int, pitch: Double, keepTime: Int) {
        entity.doWithSeenBy { NetworkMessageSender.sendPlayEntitySound(it, resourcePath, entity.uniqueId.toString(), soundCategory, distOrRoll, pitch, keepTime)  }
    }

    fun setHideHitBox(hide: Boolean) {
        this.hideHitBox = hide
        entity.doWithSeenBy { NetworkMessageSender.setEntityHitBoxHide(it, uniqueId, hide)  }
    }

    // 以自身位置添加伤害数字并且使可以看见该实体的玩家看到
    fun broadcastDamageDisplay(damageDisplayConfigId: String, damage: Double) {
        val loc = entity.location
        val x = loc.x
        val y = loc.y + entity.height / 2
        val z = loc.z
        entity.doWithSeenBy { NetworkMessageSender.sendDamageDisplay(it, damageDisplayConfigId, x, y, z, damage)  }
    }

    // 以自身位置添加伤害数字并且使传入列表内的玩家看到
    fun sendDamageDisplayToPlayers(damageDisplayConfigId: String, damage: Double, players: List<Player>) {
        val loc = entity.location
        val x = loc.x
        val y = loc.y + entity.height / 2
        val z = loc.z
        players.forEach { NetworkMessageSender.sendDamageDisplay(it, damageDisplayConfigId, x, y, z, damage) }
    }




    open fun startSeenBy(target: Player) {
        syncModelOnStartSeenBy(target)
        if (size.first > 0 && size.second > 0) {
            NetworkMessageSender.sendEntitySize(target, uniqueId, size.first, size.second)
        }
        hideBone.forEach {
            NetworkMessageSender.sendEntityHideBone(target, uniqueId, it, true)
        }
        defaultState.forEach { (state, name) ->
            NetworkMessageSender.sendEntityDefaultAnimationState(target, uniqueId, state, name)
        }
        glowColor?.let {
            NetworkMessageSender.sendPlayerGlowColor(target, uniqueId, it[0],  it[1],  it[2])
        }
        if (glowEnable) NetworkMessageSender.sendEntityGlowEnable(target, uniqueId)
        if(!displayName) NetworkMessageSender.sendHideName(target, uniqueId, true)
        if(hideHitBox) NetworkMessageSender.setEntityHitBoxHide(target, uniqueId, true)
    }

    protected open fun syncModelOnStartSeenBy(target: Player) {
        if (model.isNotEmpty()) NetworkMessageSender.setEntityModel(target, uniqueId, model, scale)
    }

    fun syncSize(){
        if(size.first > 0 && size.second > 0){
            if(entity.width != size.first || entity.height != size.second){
                EntityBridge.setEntitySize(entity, size.first.toFloat(), size.second.toFloat())
            }
        }
    }




}
