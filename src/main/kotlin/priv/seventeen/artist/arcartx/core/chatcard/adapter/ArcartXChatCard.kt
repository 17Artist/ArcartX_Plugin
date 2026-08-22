/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.chatcard.adapter

import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.network.NetworkMessageSender

interface ArcartXChatCard {

    val callbacks: MutableList<ChatCardCallBack>

    var id: String

    fun registerCallBack(callBack: ChatCardCallBack) {
        callbacks.add(callBack)
    }

    fun send(player: Player, data: Map<String, String>) {
        NetworkMessageSender.sendCardMessage(player, id, data)
    }
}
