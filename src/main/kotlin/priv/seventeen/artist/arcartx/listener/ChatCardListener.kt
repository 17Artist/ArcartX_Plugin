/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.listener

import priv.seventeen.artist.arcartx.core.chatcard.ArcartXChatCardRegistry
import priv.seventeen.artist.arcartx.core.chatcard.ChatCardProtocol
import priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent
import priv.seventeen.artist.blink.event.AutoListener

/** 固定通过 ClientCustomPacketEvent/ArcartX:ChatCard 接收聊天卡片控件回包。 */
@AutoListener
fun onChatCardPacket(event: ClientCustomPacketEvent) {
    if (event.id != ChatCardProtocol.PACKET_ID) return
    ArcartXChatCardRegistry.dispatch(event.player, event.data)
}
