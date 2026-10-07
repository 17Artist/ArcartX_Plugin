/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.event.player

import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import priv.seventeen.artist.arcartx.core.playerhost.PlayerAppearance
import priv.seventeen.artist.arcartx.event.ArcartXEvent

/** Validated immutable old/new state. Cancellation leaves the whole appearance unchanged. */
class PlayerAppearanceChangeEvent(
    val player: Player,
    val previous: PlayerAppearance,
    val appearance: PlayerAppearance
) : ArcartXEvent() {
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
    override fun getHandlers() = handlerList
}

