/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.chatcard

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.core.chatcard.adapter.ArcartXChatCard
import priv.seventeen.artist.arcartx.core.config.card.ChatCard
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.util.collections.CallData
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** 聊天卡片注册表 */
object ArcartXChatCardRegistry {

    @JvmField
    val PACKET_ID: String = ChatCardProtocol.PACKET_ID

    private val _registeredChatCards: MutableMap<String, ChatCard> = ConcurrentHashMap()

    val registeredChatCards: Map<String, ChatCard> = Collections.unmodifiableMap(_registeredChatCards)

    fun register(id: String, src: YamlConfiguration): ArcartXChatCard = register(id, ChatCard(id, src))

    fun register(id: String, src: String): ArcartXChatCard =
        register(id, ChatCard(id, YamlConfiguration.loadConfiguration(src.reader())))

    fun register(id: String, src: File): ArcartXChatCard =
        register(id, ChatCard(id, YamlConfiguration.loadConfiguration(src)))

    fun register(id: String, chatCard: ChatCard): ArcartXChatCard = store(id, chatCard, true)

    /** 内置配置目录加载使用；初始/整包重载最终由 SPackSettings 一次性同步。 */
    internal fun registerWithoutSync(id: String, chatCard: ChatCard): ArcartXChatCard = store(id, chatCard, false)

    private fun store(id: String, chatCard: ChatCard, sync: Boolean): ArcartXChatCard {
        chatCard.id = id
        _registeredChatCards[id] = chatCard
        if (sync) {
            broadcastUpdate(chatCard)
        }
        return chatCard
    }

    fun unregister(id: String) {
        if (_registeredChatCards.remove(id) != null) {
            broadcastRemove(id)
        }
    }

    /** 内置配置目录重载使用；最终由 SPackSettings 一次性同步，避免逐卡片重复发包。 */
    internal fun unregisterWithoutSync(id: String) {
        _registeredChatCards.remove(id)
    }

    operator fun get(id: String): ArcartXChatCard? = registeredChatCards[id]

    fun reload(id: String, src: YamlConfiguration) = reload(id, ChatCard(id, src))

    fun reload(id: String, src: String) =
        reload(id, ChatCard(id, YamlConfiguration.loadConfiguration(src.reader())))

    fun reload(id: String, src: File) =
        reload(id, ChatCard(id, YamlConfiguration.loadConfiguration(src)))

    private fun reload(id: String, chatCard: ChatCard) {
        chatCard.id = id
        _registeredChatCards[id]?.let { chatCard.callbacks.addAll(it.callbacks) }
        _registeredChatCards[id] = chatCard
        broadcastUpdate(chatCard)
    }

    fun send(player: Player, id: String, data: Map<String, String>) {
        registeredChatCards[id]?.send(player, data)
    }

    internal fun dispatch(player: Player, payload: List<String>) {
        val packet = ChatCardProtocol.decodeAction(payload) ?: return
        val chatCard = registeredChatCards[packet.cardId] ?: return
        val callData = CallData(player, packet.identifier, packet.data)
        chatCard.callbacks.forEach { it.call(callData) }
    }

    private fun broadcastUpdate(chatCard: ChatCard) {
        ArcartXEntityManager.players.values.forEach { playerData ->
            NetworkMessageSender.sendChatCardConfig(playerData.player, chatCard)
        }
    }

    private fun broadcastRemove(id: String) {
        ArcartXEntityManager.players.values.forEach { playerData ->
            NetworkMessageSender.sendChatCardConfigRemove(playerData.player, id)
        }
    }
}
