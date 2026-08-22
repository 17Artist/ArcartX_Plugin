/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.config.card

import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.arcartx.core.chatcard.ArcartXChatCardRegistry
import priv.seventeen.artist.arcartx.commons.config.ArcartXConfigFolder
import priv.seventeen.artist.arcartx.language.AXLanguageKey
import priv.seventeen.artist.arcartx.language.L
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendMessage
import priv.seventeen.artist.blink.bukkitPlugin

class ChatCardFolder : ArcartXConfigFolder<ChatCard>(bukkitPlugin, "chat_card", ::ChatCard) {

    init {
        this.load()
    }

    override fun onCreateFolder(plugin: JavaPlugin, folderPath: String) {
        this.createConfig(plugin,   "${folderPath}聊天卡片示例.yml")
    }

    override fun onSetFileID(config: ChatCard, id: String) {
        config.id = id
    }


    override fun reload() {
        // 只移除本目录上一轮加载的卡片。
        val builtInIds = configs.keys.toList()
        builtInIds.forEach(ArcartXChatCardRegistry::unregisterWithoutSync)

        super.reload()

        configs.forEach { (id, card) ->
            ArcartXChatCardRegistry.registerWithoutSync(id, card)
        }

        bukkitPlugin.sendMessage(L(AXLanguageKey.LOAD_CHAT_CARD, configs.size.toString()))
    }
}
