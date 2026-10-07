/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LegacyAppearanceChangeTest {
    @Test fun `costume event callback keeps its model pack and unrelated slot changes`() {
        var state = PlayerAppearance(model = "before", packId = "before_pack").validated()
        var commits = 0
        assertTrue(commitLegacyAppearanceChange({ state }, { current ->
            current.copy(legacyCostume = LegacyCostumeState(slots = current.legacyCostume.slots +
                ("HEAD" to LegacyCostumeEntry("new_hat", false))))
        }, { it.validated() }, {
            state = state.copy(model = "after", packId = "after_pack",
                slots = mapOf("hair" to PlayerHostCostume("new_hair")),
                legacyCostume = LegacyCostumeState(slots = mapOf("BODY" to LegacyCostumeEntry("coat", true))))
                .validated()
            true
        }, { state = it; commits++; true }))
        assertEquals("after", state.model)
        assertEquals("after_pack", state.packId)
        assertEquals("new_hair", state.slots["hair"]!!.model)
        assertEquals(setOf("HEAD", "BODY"), state.legacyCostume.slots.keys)
        assertEquals(1, commits)
    }

    @Test fun `model and pack operations rebase without restoring a removed costume`() {
        for (change in listOf<(PlayerAppearance) -> PlayerAppearance>(
            { it.copy(model = "new_host", profileId = "", variant = "DEFAULT_BBMODEL") },
            { it.copy(packId = "new_pack") },
            { it.copy(packId = "") }
        )) {
            var state = PlayerAppearance(model = "old_host", packId = "old_pack",
                slots = mapOf("hair" to PlayerHostCostume("hair"))).validated()
            assertTrue(commitLegacyAppearanceChange({ state }, change, { it.validated() }, {
                state = state.copy(slots = emptyMap()).validated()
                true
            }, { state = it; true }))
            assertTrue(state.slots.isEmpty())
        }
    }

    @Test fun `cancelled legacy event does not commit its operation or roll back callback side effects`() {
        var state = PlayerAppearance(model = "host", packId = "before").validated()
        assertFalse(commitLegacyAppearanceChange({ state }, { it.copy(packId = "rejected") }, { it.validated() }, {
            state = state.copy(scale = 2.0).validated()
            false
        }, { fail<Unit>("cancelled operation must not commit"); true }))
        assertEquals("before", state.packId)
        assertEquals(2.0, state.scale)
    }

    @Test fun `invalid input is refused before legacy event and commit`() {
        val state = PlayerAppearance(model = "host").validated()
        var events = 0
        var commits = 0
        assertThrows(IllegalArgumentException::class.java) {
            commitLegacyAppearanceChange({ state }, { it.copy(scale = Double.NaN) }, { it.validated() },
                { events++; true }, { commits++; true })
        }
        assertEquals(0, events)
        assertEquals(0, commits)
    }

    @Test fun `full appearance cancellation preserves the latest event callback state`() {
        var state = PlayerAppearance(model = "host", packId = "before").validated()
        assertFalse(commitLegacyAppearanceChange({ state }, { it.copy(packId = "rejected") }, { it.validated() }, {
            state = state.copy(scale = 2.0).validated()
            true
        }, { false }))
        assertEquals("before", state.packId)
        assertEquals(2.0, state.scale)
    }
}
