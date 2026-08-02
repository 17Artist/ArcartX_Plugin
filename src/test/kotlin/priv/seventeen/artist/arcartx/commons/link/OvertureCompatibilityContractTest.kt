/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import priv.seventeen.artist.arcartx.commons.link.overture.OvertureLinker
import java.nio.file.Files
import java.nio.file.Path

class OvertureCompatibilityContractTest {

    private val linkerSource = read(
        "src/main/kotlin/priv/seventeen/artist/arcartx/commons/link/overture/OvertureLinker.kt"
    )
    private val providerSource = read(
        "src/main/kotlin/priv/seventeen/artist/arcartx/commons/link/item/OvertureItemProvider.kt"
    )
    private val builtInLinkerSource = read(
        "src/main/kotlin/priv/seventeen/artist/arcartx/commons/link/BuiltInLinker.kt"
    )
    private val buildSource = read("build.gradle.kts")

    @Test
    fun `arcartx owns every overture client tag extension`() {
        val tags = setOf(
            "icon",
            "drop",
            "model",
            "armor_texture",
            "costume_model",
            "costume_hide",
            "fp_model",
            "extra_model",
            "url",
            "cooldown"
        )

        tags.forEach { tag ->
            assertTrue(linkerSource.contains("\"$tag\""), "Missing ArcartX tag Meta: $tag")
        }
        assertTrue(linkerSource.contains("OvertureAPI.registerMeta"))
        assertTrue(linkerSource.contains("sourceTag[tagName] = ItemTagData.of(value)"))
        assertTrue(linkerSource.contains("sourceTag.remove(tagName)"))
        assertTrue(linkerSource.contains("sourceTag[\"extra_model\"] = ItemTagData.of(extraModels)"))
        assertTrue(linkerSource.contains("sourceTag.remove(\"extra_model\")"))
    }

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
    fun `overture is loaded before arcartx and registered during load`() {
        assertTrue(buildSource.contains("\"Rondo\", \"Overture\""))
        assertTrue(buildSource.contains("compileOnly(\"priv.seventeen.artist.overture:overture:1.0.0\")"))
        assertTrue(builtInLinkerSource.contains("@Awake(LifeCycle.LOAD)"))
        assertTrue(builtInLinkerSource.contains("OvertureLinker.registerMetaExtensions()"))
    }

    @Test
    fun `overture participates in the unified item provider api`() {
        assertTrue(providerSource.contains("override fun getIdentifier(): String = \"Overture\""))
        assertTrue(providerSource.contains("OvertureAPI.generateItem(id)"))
        assertFalse(providerSource.contains("Player"))
    }

    private fun read(relativePath: String): String =
        Files.readString(Path.of(relativePath))
}
