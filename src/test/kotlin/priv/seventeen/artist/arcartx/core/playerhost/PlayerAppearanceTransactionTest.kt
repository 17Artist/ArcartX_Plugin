/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.plugin.PluginManager
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer
import priv.seventeen.artist.arcartx.event.ArcartXEvent
import priv.seventeen.artist.arcartx.event.player.PlayerAppearanceChangeEvent
import sun.misc.Unsafe
import java.lang.reflect.Proxy
import java.util.UUID

/** Calls the production APIs; a cancelling event sink keeps NMS/network/database out of this unit test. */
@ResourceLock("bukkit-server")
class PlayerAppearanceTransactionTest {
    private val authority = PlayerAppearance(model = "hero", packId = "original_pack").validated()
    private val events = ArrayList<Event>()
    private var onAppearance: ((PlayerAppearanceChangeEvent) -> Unit)? = null
    private var originalServer: Any? = null

    @BeforeEach fun installCancellingEventSink() {
        val manager = proxy(PluginManager::class.java) { method, arguments ->
            if (method == "callEvent") {
                val event = arguments!![0] as Event
                events += event
                if (event is PlayerAppearanceChangeEvent) onAppearance?.invoke(event)
                (event as? ArcartXEvent)?.setCancelled(true)
            }
            null
        }
        val registries = HashMap<Class<*>, Registry<*>>()
        val server = proxy(Server::class.java) { method, arguments ->
            when (method) {
                "getPluginManager" -> manager
                "getRegistry" -> {
                    val entryType = arguments!![0] as Class<*>
                    registries.getOrPut(entryType) { testRegistry(entryType) }
                }
                else -> null
            }
        }
        val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        originalServer = serverField.get(null)
        serverField.set(null, server)
        // Match server bootstrap ordering: Registry must exist before Player's potion signatures load.
        Class.forName("org.bukkit.Registry", true, Bukkit::class.java.classLoader)
    }

    @AfterEach fun restoreServer() {
        Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }.set(null, originalServer)
    }

    @Test fun `builder callback rejects same-player writes before any legacy or full event`() {
        val changes = listOf<Pair<String, (ArcartXPlayer) -> Unit>>(
            "model" to { it.setModel("next", 1.0) },
            "preserving model" to { it.setModel("next", 1.0, false) },
            "remove model" to { it.removeModel() },
            "suit" to { it.equipSuit("suit") },
            "legacy slot" to { it.equipCostume(ArcartXPlayer.CostumeSlot.HEAD, "hat") },
            "remove legacy slot" to { it.removeCostume(ArcartXPlayer.CostumeSlot.HEAD) },
            "clear costumes" to { it.clearCostume() },
            "default body" to { it.setDefaultModel() },
            "custom body" to { it.setCustomModel("custom") },
            "animation pack" to { it.setAnimationPack("new_pack") },
            "clear animation pack" to { it.clearAnimationPack() },
            "host" to { it.setPlayerHost("hero") },
            "dynamic slot" to { it.equipCostume("hair", "hair") },
            "remove dynamic slot" to { it.removeCostume("hair") },
            "appearance" to { it.setAppearance(authority.copy(scale = 2.0)) },
            "nested builder" to { it.updateAppearance { nested -> nested.packId = "nested" } },
            "preview" to { it.tryModel("preview", 1.0, 1000) }
        )
        for ((name, change) in changes) {
            val player = newHandler()
            events.clear()
            assertThrows(IllegalStateException::class.java, {
                player.updateAppearance { change(player) }
            }, name)
            assertTrue(events.isEmpty(), "$name emitted an event before being rejected")
            assertSame(authority, player.getAppearance(), name)
            assertEquals(7L, player.getAppearanceRevision(), name)
            // A rejected nested operation must not leave the builder guard stuck.
            assertFalse(player.setAppearance(authority.copy(scale = 2.0)), name)
            assertEquals(1, events.size, name)
        }
    }

    @Test fun `handled nested writes leave the candidate based on the unchanged authority`() {
        val player = newHandler()
        assertFalse(player.updateAppearance { builder ->
            assertThrows(IllegalStateException::class.java) { player.setAnimationPack("nested") }
            builder.scale = 2.0
        })
        val offered = events.single() as PlayerAppearanceChangeEvent
        assertEquals("original_pack", offered.appearance.packId)
        assertEquals(2.0, offered.appearance.scale)
        assertSame(authority, player.getAppearance())
        assertEquals(7L, player.getAppearanceRevision())
    }

    @Test fun `throwing builder does not commit and releases its write guard`() {
        val player = newHandler()
        val failure = IllegalArgumentException("callback failed")
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            player.updateAppearance { builder ->
                builder.packId = "uncommitted"
                throw failure
            }
        }
        assertSame(failure, thrown)
        assertTrue(events.isEmpty())
        assertSame(authority, player.getAppearance())
        assertEquals(7L, player.getAppearanceRevision())
        assertFalse(player.setAppearance(authority.copy(scale = 2.0)))
        assertEquals(1, events.size)
    }

    @Test fun `same saved appearance still offers a cancelable restore while preview is active`() {
        val requests = listOf<(ArcartXPlayer) -> Boolean>(
            { it.setPlayerHost("hero") },
            { it.setAppearance(authority) },
            { it.updateAppearance {} }
        )
        for (request in requests) {
            val player = newHandler()
            val preview = PlayerAppearance(model = "preview", variant = "CUSTOM_BBMODEL").validated()
            setField(player, "appearancePreview", preview)
            events.clear()
            assertFalse(request(player), "active preview must not use the saved-state no-op shortcut")
            val offered = events.single() as PlayerAppearanceChangeEvent
            assertEquals(authority, offered.appearance)
            assertSame(authority, player.getAppearance())
            assertSame(preview, field(player, "appearancePreview"), "cancellation retains the preview")
            assertEquals(7L, player.getAppearanceRevision())
        }
    }

    @Test fun `unchanged saved appearance without a preview remains a no-op`() {
        val player = newHandler()
        assertTrue(player.setPlayerHost("hero"))
        assertTrue(player.setAppearance(authority))
        assertTrue(player.updateAppearance {})
        assertTrue(events.isEmpty())
        assertEquals(7L, player.getAppearanceRevision())
    }

    @Test fun `full appearance event rejects identical recursive writes before legacy events`() {
        val player = newHandler()
        onAppearance = {
            assertThrows(IllegalStateException::class.java) { player.setAnimationPack(authority.packId) }
            assertThrows(IllegalStateException::class.java) { player.setAppearance(authority) }
            assertThrows(IllegalStateException::class.java) { player.updateAppearance {} }
            assertThrows(IllegalStateException::class.java) { player.tryModel("preview", 1.0, 1000) }
        }
        assertFalse(player.setAppearance(authority.copy(scale = 2.0)))
        assertEquals(1, events.size)
        assertSame(authority, player.getAppearance())
        onAppearance = null
        assertFalse(player.setAppearance(authority.copy(scale = 3.0)))
        assertEquals(2, events.size)
    }

    private fun newHandler(): ArcartXPlayer {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        // Skip only the constructor's external database/config lifecycle; API implementations are unmodified.
        val handler = unsafe.allocateInstance(ArcartXPlayer::class.java) as ArcartXPlayer
        val player = proxy(Player::class.java) { method, _ ->
            when (method) {
                "getUniqueId" -> UUID(0, 1)
                "getName" -> "AppearanceQA"
                "isOnline" -> true
                else -> null
            }
        }
        setField(handler, "player", player)
        setField(handler, "appearanceState", authority)
        setField(handler, "appearanceRevision", 7L)
        setField(handler, "explicitHostSelection", true)
        return handler
    }

    private fun setField(instance: ArcartXPlayer, name: String, value: Any?) {
        ArcartXPlayer::class.java.getDeclaredField(name).apply { isAccessible = true }.set(instance, value)
    }

    private fun field(instance: ArcartXPlayer, name: String): Any? =
        ArcartXPlayer::class.java.getDeclaredField(name).apply { isAccessible = true }.get(instance)

    private fun testRegistry(entryType: Class<*>): Registry<*> {
        val entries = HashMap<NamespacedKey, Keyed>()
        return proxy(Registry::class.java) { method, arguments ->
            when (method) {
                "get" -> {
                    val key = arguments!![0] as NamespacedKey
                    if (entryType.name == "org.bukkit.potion.PotionEffectType")
                        entries.getOrPut(key) { TestPotionType(key) } else null
                }
                "iterator" -> entries.values.iterator()
                "stream" -> entries.values.stream()
                else -> null
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private class TestPotionType(private val potionKey: NamespacedKey) : PotionEffectType() {
        override fun getKey() = potionKey
        override fun getTranslationKey() = "effect.${potionKey.namespace}.${potionKey.key}"
        override fun getName() = potionKey.key
        override fun getId() = 0
        override fun isInstant() = false
        override fun getColor() = Color.WHITE
        override fun getDurationModifier() = 1.0
        override fun createEffect(duration: Int, amplifier: Int) = PotionEffect(this, duration, amplifier)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, arguments ->
            when (method.name) {
                "equals" -> instance === arguments?.get(0)
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "Test${type.simpleName}"
                else -> invoke(method.name, arguments) ?: when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Byte.TYPE -> 0.toByte()
                    java.lang.Short.TYPE -> 0.toShort()
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    java.lang.Character.TYPE -> '\u0000'
                    else -> null
                }
            }
        } as T
}
