/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.chatcard

/** ChatCard 控件动作的客户端到服务端回调协议。模板同步使用专用 SPackChatCardConfig。 */
object ChatCardProtocol {

    const val PACKET_ID = "ArcartX:ChatCard"

    data class ActionPacket(
        val cardId: String,
        val identifier: String,
        val data: List<String>
    )

    /** 客户端回包格式：[cardId, actionId, ...data]。 */
    fun decodeAction(payload: List<String>): ActionPacket? {
        if (payload.size < 2) return null
        val cardId = payload[0]
        val identifier = payload[1]
        if (cardId.isEmpty() || identifier.isEmpty()) return null
        return ActionPacket(cardId, identifier, payload.drop(2))
    }
}
