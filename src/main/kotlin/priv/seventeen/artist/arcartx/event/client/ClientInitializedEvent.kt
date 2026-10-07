/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.event.client

import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import priv.seventeen.artist.arcartx.event.ArcartXEvent

/** 客户端初始化事件 */
abstract class ClientInitializedEvent(val player: Player) : ArcartXEvent(allowCancelled = false) {

    class Start(player: Player) : ClientInitializedEvent(player) {
        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
        override fun getHandlers() = handlerList
    }

    /** 首次实体、槽位数据的所有分片投递后触发；可能延后多个 tick，不代表客户端已处理完毕。 */
    class End(player: Player) : ClientInitializedEvent(player) {
        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
        override fun getHandlers() = handlerList
    }

    class Reload(player: Player) : ClientInitializedEvent(player) {
        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
        override fun getHandlers() = handlerList
    }

    class ResourceLoaded(player: Player) : ClientInitializedEvent(player) {
        companion object {
            @JvmStatic
            val handlerList = HandlerList()
        }
        override fun getHandlers() = handlerList
    }
}
