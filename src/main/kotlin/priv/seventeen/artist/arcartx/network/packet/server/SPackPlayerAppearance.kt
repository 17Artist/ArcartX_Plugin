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
import priv.seventeen.artist.arcartx.core.playerhost.PlayerAppearance
import java.util.UUID

class SPackPlayerAppearance @JvmOverloads constructor(
    @SerializedName("uuid") val target: UUID,
    val revision: Long,
    appearance: PlayerAppearance,
    @SerializedName("profile_revision") val profileRevision: Long,
    /** Null preserves the original automatic snapshot policy; false is the legacy API's explicit intent. */
    val reset: Boolean? = null
) : ServerPacket {
    val model = appearance.model
    val scale = appearance.scale
    val variant = appearance.variant
    @SerializedName("profile_id") val profileId = appearance.profileId
    @SerializedName("pack_id") val packId = appearance.packId
    val slots = appearance.slots
    @SerializedName("legacy_costume") val legacyCostume = appearance.legacyCostume
}
