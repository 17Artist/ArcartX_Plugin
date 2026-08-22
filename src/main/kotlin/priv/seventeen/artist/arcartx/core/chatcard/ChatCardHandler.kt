/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.chatcard

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.arcartx.api.ArcartXAPI.getChatCardRegistry
import priv.seventeen.artist.arcartx.core.chatcard.adapter.ArcartXChatCard
import java.io.File

/** 外部插件可直接继承的聊天卡片处理器，使用方式与 [priv.seventeen.artist.arcartx.core.ui.UIHandler] 一致。 */
abstract class ChatCardHandler(val identifier: String, private val chatCardConfig: File) {

    abstract val plugin: JavaPlugin

    val chatCard: ArcartXChatCard = getChatCardRegistry().register(identifier, chatCardConfig).apply {
        registerCallBack {
            onPacket(it.player, it.identifier, it.data)
        }
    }

    open fun send(player: Player, data: Map<String, String>) {
        getChatCardRegistry().send(player, identifier, data)
    }

    open fun onPacket(player: Player, identifier: String, data: List<String>) {
    }

    fun reload() {
        getChatCardRegistry().reload(identifier, chatCardConfig)
    }

    fun unregister() {
        getChatCardRegistry().unregister(identifier)
    }
}
