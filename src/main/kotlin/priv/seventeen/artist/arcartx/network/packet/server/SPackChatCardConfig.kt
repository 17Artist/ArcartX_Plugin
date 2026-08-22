/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network.packet.server

import com.google.gson.annotations.SerializedName
import priv.seventeen.artist.arcartx.core.config.card.ChatCard

/** 单张聊天卡片模板的注册、重载与注销包。 */
class SPackChatCardConfig private constructor(
    @SerializedName("remove")
    private val remove: Boolean,
    @SerializedName("id")
    private val id: String,
    @SerializedName("chat_card")
    private val chatCard: ChatCard?
) : ServerPacket {

    companion object {
        fun update(chatCard: ChatCard): SPackChatCardConfig =
            SPackChatCardConfig(false, chatCard.id, chatCard)

        fun remove(id: String): SPackChatCardConfig =
            SPackChatCardConfig(true, id, null)
    }
}
