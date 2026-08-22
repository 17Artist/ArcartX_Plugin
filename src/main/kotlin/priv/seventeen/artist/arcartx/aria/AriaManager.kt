/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.aria

import org.bukkit.Bukkit
import priv.seventeen.artist.arcartx.aria.target.*
import priv.seventeen.artist.aria.callable.CallableManager
import priv.seventeen.artist.aria.context.GlobalStorage
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle

/** Aria 脚本函数注册管理器 */
object AriaManager {

    // 全局上下文
    val GLOBAL_STORAGE: GlobalStorage = GlobalStorage()

    private var initialized = false

    @Awake(LifeCycle.ENABLE)
    fun registerCallable() {
        if (!initialized) {
            initialized = true
            val manager = CallableManager.INSTANCE
            manager.registerObjectFunction(PlayerInfo::class.java)
            manager.registerObjectFunction(PlayerMessage::class.java)
            manager.registerObjectFunction(PlayerFirstPersonAnimation::class.java)
            manager.registerObjectFunction(EntityMovement::class.java)
            manager.registerObjectFunction(EntityInfo::class.java)
            manager.registerObjectFunction(EntityEffect::class.java)
            manager.registerObjectFunction(EntityCombat::class.java)
            manager.registerObjectFunction(EntitySound::class.java)

            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                manager.registerObjectFunction(MythicMob::class.java)
            }
        }
    }
}
