/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

/** Legacy events may change other fields. Validate input first, then rebase only this operation. */
internal fun commitLegacyAppearanceChange(
    current: () -> PlayerAppearance,
    change: (PlayerAppearance) -> PlayerAppearance,
    validate: (PlayerAppearance) -> PlayerAppearance,
    event: () -> Boolean,
    commit: (PlayerAppearance) -> Boolean
): Boolean {
    validate(change(current()))
    if (!event()) return false
    return commit(validate(change(current())))
}
