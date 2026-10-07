/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import priv.seventeen.artist.arcartx.network.message.MessageID
import priv.seventeen.artist.arcartx.network.packet.server.SPackPlayerAppearance
import java.util.UUID

class PlayerHostContractTest {
    @Test fun `legacy reset projection preserves explicit intent across model and scale changes`() {
        assertFalse(legacyAppearanceModelReset(false, true, true))
        assertFalse(legacyAppearanceModelReset(false, true, false))
        assertFalse(legacyAppearanceModelReset(false, false, true))
        assertTrue(legacyAppearanceModelReset(true, true, false))
        assertTrue(legacyAppearanceModelReset(null, true, true))
        assertTrue(legacyAppearanceModelReset(null, false, true))
        assertFalse(legacyAppearanceModelReset(null, true, false))
    }

    @Test fun `appearance reset is absent by default and literal false for an explicit legacy request`() {
        val state = PlayerAppearance(model = "next").validated()
        val automatic = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 1, state, 1)).asJsonObject
        assertFalse(automatic.has("reset"))
        val preserving = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 2, state, 1, false)).asJsonObject
        assertTrue(preserving.has("reset"))
        assertFalse(preserving["reset"].asBoolean)
        val resetting = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 3, state, 1, true)).asJsonObject
        assertTrue(resetting["reset"].asBoolean)
        assertNotNull(SPackPlayerAppearance::class.java.getConstructor(UUID::class.java, Long::class.javaPrimitiveType,
            PlayerAppearance::class.java, Long::class.javaPrimitiveType))
    }

    @Test fun `missing capabilities use the legacy path`() {
        assertEquals(emptySet<String>(), PlayerHostCapabilities.parse(null))
        assertEquals(setOf(PlayerHostCapabilities.CUSTOM_PLAYER_HOST), PlayerHostCapabilities.parse(
            listOf(PlayerHostCapabilities.CUSTOM_PLAYER_HOST, PlayerHostCapabilities.CUSTOM_PLAYER_HOST)))
    }

    @Test fun `malformed capabilities are refused without null dereference`() {
        assertNull(PlayerHostCapabilities.parse(listOf(null)))
        assertNull(PlayerHostCapabilities.parse(List(65) { "custom_player_host_v1" }))
        assertNull(PlayerHostCapabilities.parse(listOf("x".repeat(65))))
        assertNull(PlayerHostCapabilities.parse(listOf("Host")))
    }

    @Test fun `render-root capability and unfiltered appearances retain their original snapshot`() {
        val capability = PlayerHostCapabilities.COSTUME_RENDER_ROOTS
        assertEquals("custom_costume_render_roots_v1", capability)
        assertEquals(setOf(PlayerHostCapabilities.CUSTOM_PLAYER_HOST, capability), PlayerHostCapabilities.parse(
            listOf(PlayerHostCapabilities.CUSTOM_PLAYER_HOST, capability)))
        val selected = PlayerAppearance(model = "hero", slots = mapOf(
            "hair" to PlayerHostCostume("full", renderRoots = listOf("Hair")))).validated()
        assertSame(selected, selected.forRenderRootsCapability(true))
        val whole = PlayerAppearance(model = "hero", slots = mapOf("whole" to PlayerHostCostume("full"))).validated()
        assertSame(whole, whole.forRenderRootsCapability(false))
        val empty = PlayerAppearance(model = "hero").validated()
        assertSame(empty, empty.forRenderRootsCapability(false))
    }

    @Test fun `unsupported receiver only loses selected slots without mutating server authority`() {
        val authority = PlayerAppearance(model = "hero", scale = 1.25, profileId = "hero", packId = "actions",
            slots = linkedMapOf(
                "hair" to PlayerHostCostume("full", renderRoots = listOf("Hair")),
                "whole" to PlayerHostCostume("ordinary"),
                "head" to PlayerHostCostume("full", renderRoots = listOf("Head"))
            ), legacyCostume = LegacyCostumeState(slots = mapOf("HEAD" to LegacyCostumeEntry("old_hat", true)))).validated()
        val receiver = authority.forRenderRootsCapability(false)
        assertEquals(setOf("whole"), receiver.slots.keys)
        assertSame(authority.slots.getValue("whole"), receiver.slots.getValue("whole"))
        assertEquals(authority.model, receiver.model)
        assertEquals(authority.scale, receiver.scale)
        assertEquals(authority.variant, receiver.variant)
        assertEquals(authority.profileId, receiver.profileId)
        assertEquals(authority.packId, receiver.packId)
        assertSame(authority.legacyCostume, receiver.legacyCostume)
        assertEquals(setOf("hair", "whole", "head"), authority.slots.keys)
        assertThrows(UnsupportedOperationException::class.java) { (receiver.slots as MutableMap).clear() }
        val packet = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 8, receiver, 3)).asJsonObject
        assertEquals(setOf("whole"), packet.getAsJsonObject("slots").keySet())
        assertFalse(packet.getAsJsonObject("slots").getAsJsonObject("whole").has("render_roots"))
        assertEquals("actions", packet["pack_id"].asString)
        assertTrue(packet.getAsJsonObject("legacy_costume").getAsJsonObject("slots").has("HEAD"))
    }

    @Test fun `projection filters every nonnull selector including empty unvalidated lists`() {
        val raw = PlayerAppearance(model = "hero", packId = "actions", slots = mapOf(
            "empty_selector" to PlayerHostCostume("full", renderRoots = emptyList()),
            "selected" to PlayerHostCostume("full", renderRoots = listOf("Hair"))))
        assertSame(raw, raw.forRenderRootsCapability(true))
        val receiver = raw.forRenderRootsCapability(false)
        assertTrue(receiver.slots.isEmpty())
        assertEquals(raw.model, receiver.model)
        assertEquals(raw.packId, receiver.packId)
        assertEquals(2, raw.slots.size)
        assertThrows(UnsupportedOperationException::class.java) { (receiver.slots as MutableMap).clear() }
    }

    @Test fun `server overrides remain sparse and explicit empty maps remain merge operations`() {
        val result = HostProfileValidation.validate("hero", mapOf("model" to "characters/hero",
            "parts" to emptyMap<String, Any?>(), "slots" to emptyMap<String, Any?>()))
        assertFalse(result.containsKey("semantic_bones"))
        assertEquals(emptyMap<String, Any?>(), result["parts"])
        assertEquals(emptyMap<String, Any?>(), result["slots"])
    }

    @Test fun `profile publication does not retain caller owned maps or lists`() {
        val bones = arrayListOf("Head")
        val source = linkedMapOf<String, Any?>("model" to "hero", "parts" to linkedMapOf("HEAD" to bones))
        val frozen = HostProfileValidation.validate("hero", source)
        bones += "Body"
        source["model"] = "changed"
        assertEquals("hero", frozen["model"])
        assertEquals(listOf("Head"), (frozen["parts"] as Map<*, *>)["HEAD"])
        assertThrows(UnsupportedOperationException::class.java) { (frozen as MutableMap)["model"] = "changed" }
    }

    @Test fun `profile typo and invalid semantic bone are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "part" to emptyMap<String, Any?>())) }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "semantic_bones" to mapOf("right_arm" to ""))) }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "locators" to mapOf("right_hand" to mapOf("locator" to "item")))) }
    }

    @Test fun `slot ids preserve exact stable key and permit Chinese labels`() {
        val patch = HostProfileValidation.validate("hero", mapOf("model" to "hero", "slots" to
            mapOf("hair.front" to mapOf("label" to "前发", "order" to 1))))
        assertTrue((patch["slots"] as Map<*, *>).containsKey("hair.front"))
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.requireId("Hair") }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.requireId("头发") }
        HostProfileValidation.requireId("a".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.requireId("a".repeat(65)) }
    }

    @Test fun `appearance copies attachment collections before commit`() {
        val boneMap = linkedMapOf("Hair" to "Head")
        val hidden = arrayListOf("HEAD")
        val slots = linkedMapOf("hair" to PlayerHostCostume("hair", PlayerHostBinding(boneMap = boneMap),
            PlayerHostHide(parts = hidden)))
        val state = PlayerAppearance(model = "hero", slots = slots).validated()
        boneMap["Hair"] = "Body"
        hidden.clear()
        slots.clear()
        assertEquals("Head", state.slots["hair"]!!.binding.boneMap["Hair"])
        assertEquals(listOf("HEAD"), state.slots["hair"]!!.hide!!.parts)
    }

    @Test fun `attachment transforms reject NaN zero scaling and missing roots`() {
        fun candidate(binding: PlayerHostBinding) = PlayerAppearance(model = "hero", slots = mapOf("hair" to PlayerHostCostume("hair", binding)))
        assertThrows(IllegalArgumentException::class.java) { candidate(PlayerHostBinding(mode = "attach")).validated() }
        assertThrows(IllegalArgumentException::class.java) { candidate(PlayerHostBinding(offset = PlayerHostOffset(position = listOf(Double.NaN, 0.0, 0.0)))).validated() }
        assertThrows(IllegalArgumentException::class.java) { candidate(PlayerHostBinding(offset = PlayerHostOffset(scale = listOf(1.0, 0.0, 1.0)))).validated() }
        assertDoesNotThrow { candidate(PlayerHostBinding(offset = PlayerHostOffset(scale = listOf(-1.0, 1.0, 1.0)))).validated() }
        assertDoesNotThrow { candidate(PlayerHostBinding(mode = "attach", sourceRoot = "Hair",
            anchor = PlayerHostAnchor("Head", "hair"))).validated() }
    }

    @Test fun `missing hide inherits while explicit empty hide disables it`() {
        val inherit = PlayerAppearance(model = "hero", slots = mapOf("hair" to PlayerHostCostume("hair"))).validated()
        val clear = inherit.copy(slots = mapOf("hair" to PlayerHostCostume("hair", hide = PlayerHostHide()))).validated()
        val gson = Gson()
        val inherited = gson.toJsonTree(inherit).asJsonObject.getAsJsonObject("slots").getAsJsonObject("hair")
        assertFalse(inherited.has("hide"))
        val explicit = gson.toJsonTree(clear).asJsonObject.getAsJsonObject("slots").getAsJsonObject("hair").getAsJsonObject("hide")
        assertEquals(0, explicit.getAsJsonArray("parts").size())
        assertEquals(0, explicit.getAsJsonArray("bones").size())
    }

    @Test fun `missing and null render roots preserve whole-costume wire compatibility`() {
        val gson = Gson()
        val original = PlayerHostCostume("costumes/full")
        val tree = gson.toJsonTree(original).asJsonObject
        assertFalse(tree.has("render_roots"))
        val absent = gson.fromJson(tree, PlayerHostCostume::class.java)
        assertNull(absent.renderRoots)
        tree.add("render_roots", com.google.gson.JsonNull.INSTANCE)
        val explicitNull = gson.fromJson(tree, PlayerHostCostume::class.java)
        assertNull(explicitNull.renderRoots)
        assertEquals(original, absent)
        assertEquals(original, explicitNull)
        val state = PlayerAppearance(model = "hero", slots = mapOf("full" to explicitNull)).validated()
        assertFalse(gson.toJsonTree(state.slots.getValue("full")).asJsonObject.has("render_roots"))
        assertTrue(com.google.gson.GsonBuilder().serializeNulls().create().toJsonTree(explicitNull)
            .asJsonObject["render_roots"].isJsonNull)
    }

    @Test fun `render roots are copied frozen and retained by appearance builders and costume copies`() {
        val roots = arrayListOf("Hair", "Head")
        val costume = PlayerHostCostume("costumes/full", renderRoots = roots)
        val state = PlayerAppearance(model = "hero", slots = mapOf("hair" to costume)).validated()
        roots.clear()
        val frozen = state.slots.getValue("hair")
        assertEquals(listOf("Hair", "Head"), frozen.renderRoots)
        assertThrows(UnsupportedOperationException::class.java) { (frozen.renderRoots as MutableList).clear() }
        assertEquals(frozen.renderRoots, frozen.copy(model = "costumes/another").renderRoots)
        val builder = PlayerAppearanceBuilder(state)
        builder.packId = "another_pack"
        assertEquals(frozen.renderRoots, builder.build().slots.getValue("hair").renderRoots)
        assertNotEquals(frozen, frozen.copy(renderRoots = listOf("Other")))
    }

    @Test fun `render roots reject empty oversized malformed and null element collections`() {
        fun validated(roots: List<String>?) = PlayerAppearance(model = "hero",
            slots = mapOf("hair" to PlayerHostCostume("full", renderRoots = roots))).validated()
        assertDoesNotThrow { validated(null) }
        assertDoesNotThrow { validated(List(4096) { "Root$it" }) }
        assertThrows(IllegalArgumentException::class.java) { validated(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { validated(List(4097) { "Root$it" }) }
        for (invalid in listOf("", " ", "\u0000", "a".repeat(257))) {
            assertThrows(IllegalArgumentException::class.java) { validated(listOf(invalid)) }
        }
        @Suppress("UNCHECKED_CAST")
        val nullElement = listOf<String?>(null) as List<String>
        @Suppress("UNCHECKED_CAST")
        val numericElement = listOf(123) as List<String>
        assertThrows(IllegalArgumentException::class.java) { validated(nullElement) }
        assertThrows(IllegalArgumentException::class.java) { validated(numericElement) }
        // Name syntax belongs here; existence and overlapping subtrees belong to baked-model validation.
        assertDoesNotThrow { validated(listOf("NotYetBaked", "NotYetBaked", "中文骨骼")) }
    }

    @Test fun `Gson render roots require literal string arrays before DTO conversion`() {
        val gson = Gson()
        for (invalid in listOf("123", "true", "\"Hair\"", "{}", "[123]", "[true]", "[null]", "[{}]", "[[\"Hair\"]]")) {
            val json = """{"model":"full","render_roots":$invalid}"""
            assertThrows(com.google.gson.JsonParseException::class.java) {
                gson.fromJson(json, PlayerHostCostume::class.java)
            }
        }
        val valid = gson.fromJson("""{"model":"full","render_roots":["Hair","Head"]}""", PlayerHostCostume::class.java)
        assertEquals(listOf("Hair", "Head"), valid.renderRoots)
    }

    @Test fun `full appearance packets serialize render roots at the costume top level`() {
        val appearance = PlayerAppearance(model = "hero", profileId = "hero", slots = mapOf(
            "hair" to PlayerHostCostume("costumes/full", renderRoots = listOf("Hair"))
        )).validated()
        val json = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 8, appearance, 3)).asJsonObject
        val slot = json.getAsJsonObject("slots").getAsJsonObject("hair")
        assertEquals(listOf("Hair"), slot.getAsJsonArray("render_roots").map { it.asString })
        assertFalse(slot.getAsJsonObject("binding").has("render_roots"))
        assertEquals(appearance, Gson().fromJson(Gson().toJson(appearance), PlayerAppearance::class.java).validated())
    }

    @Test fun `old Java and Kotlin constructor and copy JVM signatures remain callable`() {
        val type = PlayerHostCostume::class.java
        assertNotNull(type.getConstructor(String::class.java))
        assertNotNull(type.getConstructor(String::class.java, PlayerHostBinding::class.java))
        assertNotNull(type.getConstructor(String::class.java, PlayerHostBinding::class.java, PlayerHostHide::class.java))
        assertNotNull(type.getConstructor(String::class.java, PlayerHostBinding::class.java, PlayerHostHide::class.java, List::class.java))
        val oldDefaultConstructor = type.getConstructor(String::class.java, PlayerHostBinding::class.java,
            PlayerHostHide::class.java, Int::class.javaPrimitiveType, Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"))
        val constructed = oldDefaultConstructor.newInstance("full", null, null, 6, null)
        assertEquals(PlayerHostCostume("full"), constructed)
        val filtered = PlayerHostCostume("full", renderRoots = listOf("Hair"))
        val oldCopy = type.getMethod("copy", String::class.java, PlayerHostBinding::class.java, PlayerHostHide::class.java)
        val copied = oldCopy.invoke(filtered, "renamed", filtered.binding, filtered.hide) as PlayerHostCostume
        assertEquals(listOf("Hair"), copied.renderRoots)
        val oldDefaultCopy = type.getMethod("copy\$default", type, String::class.java, PlayerHostBinding::class.java,
            PlayerHostHide::class.java, Int::class.javaPrimitiveType, Any::class.java)
        val unchanged = oldDefaultCopy.invoke(null, filtered, null, null, null, 7, null) as PlayerHostCostume
        assertEquals(filtered, unchanged)
        assertEquals("full", filtered.component1())
        assertEquals(filtered.binding, filtered.component2())
        assertEquals(filtered.hide, filtered.component3())
    }

    @Test fun `full snapshots carry revisions separately from body identity`() {
        val state = PlayerAppearance(model = "hero", profileId = "hero", slots = mapOf("hair" to PlayerHostCostume("hair"))).validated()
        val json = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 7, state, 3)).asJsonObject
        assertEquals(7, json["revision"].asLong)
        assertEquals(3, json["profile_revision"].asLong)
        assertEquals("hero", json["profile_id"].asString)
        assertEquals("hero", json["model"].asString)
        assertTrue(json.has("legacy_costume"))
        assertEquals(setOf("uuid", "revision", "model", "scale", "variant", "profile_id", "profile_revision",
            "pack_id", "slots", "legacy_costume"), json.keySet())
        assertEquals(1069, MessageID.Server.PLAYER_APPEARANCE.id)
        val changed = state.copy(slots = emptyMap()).validated()
        assertEquals(state.model, changed.model)
        assertEquals(state.packId, changed.packId)
    }

    @Test fun `legacy suits and enum keys preserve the old wire representation`() {
        val legacy = LegacyCostumeState(slots = mapOf("HEAD" to LegacyCostumeEntry("hat", true)))
        val state = PlayerAppearance(model = "__default_player__", legacyCostume = legacy).validated()
        assertFalse(state.isCustomHost())
        assertTrue(state.copy(model = "hero").isCustomHost())
        assertFalse(state.copy(model = "hero", variant = "CUSTOM_BBMODEL").isCustomHost())
        assertThrows(IllegalArgumentException::class.java) { state.copy(legacyCostume = legacy.copy(suit = "suit")).validated() }
    }

    @Test fun `client bounds and body part names are enforced before publication`() {
        val costume = PlayerHostCostume("hair", hide = PlayerHostHide(parts = listOf("HEAD", "OTHER")))
        assertDoesNotThrow { PlayerAppearance(model = "hero", scale = 1024.0, slots = mapOf("hair" to costume)).validated() }
        assertThrows(IllegalArgumentException::class.java) { PlayerAppearance(model = "hero", scale = 1024.01).validated() }
        assertThrows(IllegalArgumentException::class.java) { PlayerAppearance(model = "a".repeat(257)).validated() }
        assertThrows(IllegalArgumentException::class.java) { PlayerAppearance(model = "hero", slots = (0..64).associate { "slot$it" to costume }).validated() }
        assertThrows(IllegalArgumentException::class.java) { PlayerAppearance(model = "hero", slots = mapOf("hair" to costume.copy(hide = PlayerHostHide(parts = listOf("head"))))).validated() }
        assertThrows(IllegalArgumentException::class.java) { PlayerAppearance(model = "hero", slots = mapOf("hair" to costume.copy(binding = PlayerHostBinding(offset = PlayerHostOffset(scale = listOf(0.0000001, 1.0, 1.0)))))).validated() }
        assertThrows(IllegalArgumentException::class.java) { PlayerAppearance(model = "hero", slots = mapOf("hair" to costume.copy(binding = PlayerHostBinding(offset = PlayerHostOffset(position = listOf(1000001.0, 0.0, 0.0)))))).validated() }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "parts" to mapOf("head" to listOf("Head")))) }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "parts" to mapOf("HEAD" to listOf("Head"), "BODY" to listOf("Head")))) }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "locators" to mapOf("other" to mapOf("bone" to "Head", "locator" to "item")))) }
        assertThrows(IllegalArgumentException::class.java) { HostProfileValidation.validate("hero", mapOf("model" to "hero", "locators" to mapOf("right_hand" to mapOf("bone" to "Head", "locator" to "")))) }
    }

    private class ProfileEnvelope(@field:JsonAdapter(PlayerHostProfileMapAdapter::class)
        val player_host_profiles: Map<String, Map<String, Any?>>)

    @Test fun `profile delete markers survive field specific serialization without changing legacy nulls`() {
        val patch = HostProfileValidation.validate("hero", mapOf("model" to "hero", "parts" to mapOf("HEAD" to null),
            "semantic_bones" to mapOf("right_arm" to null), "slots" to mapOf("hair" to null)))
        val json = Gson().toJsonTree(ProfileEnvelope(mapOf("hero" to patch))).asJsonObject
        val profile = json.getAsJsonObject("player_host_profiles").getAsJsonObject("hero")
        assertTrue(profile.getAsJsonObject("parts")["HEAD"].isJsonNull)
        assertTrue(profile.getAsJsonObject("semantic_bones")["right_arm"].isJsonNull)
        assertTrue(profile.getAsJsonObject("slots")["hair"].isJsonNull)
        assertFalse(Gson().toJsonTree(LegacyCostumeState()).asJsonObject.has("suit"))
    }

    @Test fun `server serializer matches the shared client interoperability fixtures`() {
        val appearance = PlayerAppearance(model = "characters/hero", profileId = "hero", packId = "hero_actions",
            slots = linkedMapOf(
                "hair" to PlayerHostCostume("costumes/hair", PlayerHostBinding(mode = "attach", sourceRoot = "Hair", anchor = PlayerHostAnchor("Head"))),
                "clothes" to PlayerHostCostume("costumes/clothes", hide = PlayerHostHide(parts = listOf("BODY", "OTHER"), bones = listOf("Coat")))
            ), legacyCostume = LegacyCostumeState(slots = mapOf("HEAD" to LegacyCostumeEntry("costumes/hat", true)))).validated()
        val actual = Gson().toJsonTree(SPackPlayerAppearance(UUID(0, 1), 7, appearance, 3))
        val fixture = javaClass.getResourceAsStream("/player-host-appearance-v1.json")!!.bufferedReader().use { it.readText() }
        assertEquals(Gson().fromJson(fixture, com.google.gson.JsonElement::class.java), actual)
        val profile = mapOf<String, Any?>("schema" to 1, "model" to "characters/hero",
            "parts" to mapOf("HEAD" to listOf("Head"), "BODY" to listOf("Body")),
            "semantic_bones" to mapOf("head" to "Head", "body" to "Body"),
            "locators" to mapOf("right_hand" to mapOf("bone" to "RightArm", "locator" to "item")),
            "slots" to mapOf("hair" to mapOf("label" to "头发", "default_hide" to mapOf("parts" to listOf("HEAD"), "bones" to emptyList<String>()))))
        val profileFixture = javaClass.getResourceAsStream("/player-host-profile-v1.json")!!.bufferedReader().use { it.readText() }
        assertEquals(Gson().fromJson(profileFixture, com.google.gson.JsonElement::class.java), Gson().toJsonTree(HostProfileValidation.validate("hero", profile)))
    }

    @Test fun `legacy external default retains wardrobe and pack until host is explicitly selected`() {
        val state = PlayerAppearance(model = "external/old", packId = "old_actions",
            legacyCostume = LegacyCostumeState(slots = mapOf("HEAD" to LegacyCostumeEntry("old_hat", true)))).validated()
        val oldApi = state.legacyProjection(false)
        assertFalse(oldApi.bodyOnly)
        assertEquals("DEFAULT_BBMODEL", oldApi.variant)
        assertEquals(state.legacyCostume, oldApi.costume)
        assertEquals("old_actions", oldApi.packId)
        val newHostApi = state.legacyProjection(true)
        assertTrue(newHostApi.bodyOnly)
        assertEquals("CUSTOM_BBMODEL", newHostApi.variant)
        assertEquals(LegacyCostumeState(), newHostApi.costume)
        assertEquals("", newHostApi.packId)
        val restoredByOldApi = state.legacyProjection(false)
        assertEquals(oldApi, restoredByOldApi)
        assertNotEquals(newHostApi, restoredByOldApi)
        assertTrue(state.copy(profileId = "registered").legacyProjection(false).bodyOnly)
        assertFalse(state.copy(model = "__default_player__").legacyProjection(true).bodyOnly)
    }

    @Test fun `batch wardrobe edits retain intent and model or profile setters explicitly select intent`() {
        val initial = PlayerAppearance(model = "external/old")
        val wardrobeOnly = PlayerAppearanceBuilder(initial)
        wardrobeOnly.packId = "actions"
        wardrobeOnly.slots["hair"] = PlayerHostCostume("hair")
        assertFalse(wardrobeOnly.hostSelectionChanged)
        val selectSameBody = PlayerAppearanceBuilder(initial)
        selectSameBody.model = initial.model
        assertTrue(selectSameBody.hostSelectionChanged)
        assertTrue(selectSameBody.build().isCustomHost())
        val selectLocalMeta = PlayerAppearanceBuilder(initial)
        selectLocalMeta.profileId = ""
        assertTrue(selectLocalMeta.hostSelectionChanged)
    }
}
