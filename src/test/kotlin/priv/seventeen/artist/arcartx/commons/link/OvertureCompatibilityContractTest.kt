/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import priv.seventeen.artist.arcartx.commons.link.overture.OvertureLinker

class OvertureCompatibilityContractTest {

    @Test
    fun `costume hide values are normalized as booleans`() {
        assertNull(OvertureLinker.normalizeScalar("costume_hide", null))
        assertEquals("0", OvertureLinker.normalizeScalar("costume_hide", false))
        assertEquals("0", OvertureLinker.normalizeScalar("costume_hide", 0))
        assertEquals("0", OvertureLinker.normalizeScalar("costume_hide", "off"))
        assertEquals("1", OvertureLinker.normalizeScalar("costume_hide", true))
        assertEquals("1", OvertureLinker.normalizeScalar("costume_hide", 0.5))
        assertEquals("1", OvertureLinker.normalizeScalar("costume_hide", -1))
        assertEquals("1", OvertureLinker.normalizeScalar("costume_hide", "yes"))
        assertThrows(IllegalArgumentException::class.java) {
            OvertureLinker.normalizeScalar("costume_hide", "invalid")
        }
    }

    @Test
    fun `tooltip type is normalized as a string`() {
        assertNull(OvertureLinker.normalizeScalar("type", null))
        assertNull(OvertureLinker.normalizeScalar("type", "  "))
        assertEquals("weapon", OvertureLinker.normalizeScalar("type", " weapon "))
        assertEquals("42", OvertureLinker.normalizeScalar("type", 42))
    }
}
