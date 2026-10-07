/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.arcartx.commons.config.ReloadableConfig
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendException
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler
import priv.seventeen.artist.blink.bukkitPlugin
import java.io.File
import java.util.Collections

data class PlayerHostRegistrySnapshot(
    val revision: Long,
    val profiles: Map<String, Map<String, Any?>>
)

/** One immutable publication per successful reload. Each profile is a sparse axmeta override. */
class PlayerHostProfiles : ReloadableConfig {
    @Volatile private var current = PlayerHostRegistrySnapshot(0L, emptyMap())
    private val folder = File(bukkitPlugin.dataFolder, "player_host")

    init {
        if (!folder.exists() && folder.mkdirs()) {
            bukkitPlugin.getResource("assets/player_host/示例.yml")?.use { input ->
                File(folder, "示例.yml").outputStream().use { input.copyTo(it) }
            }
        }
        reload()
    }

    fun snapshot(): PlayerHostRegistrySnapshot = current

    fun profile(id: String): Map<String, Any?>? = current.profiles[id]

    @Synchronized override fun reload() {
        try {
            val next = LinkedHashMap<String, Map<String, Any?>>()
            folder.walkTopDown().filter { it.isFile && it.extension == "yml" }.sortedBy { it.path }.forEach { file ->
                val yaml = YamlConfiguration()
                yaml.load(file)
                val root = yaml.getConfigurationSection("root")
                    ?: throw IllegalArgumentException("宿主配置需要 root")
                root.getValues(false).forEach { (id, value) ->
                    require(value is ConfigurationSection) { "宿主配置 $id 必须是配置段" }
                    require(!next.containsKey(id)) { "宿主配置 ID 重复: $id" }
                    next[id] = HostProfileValidation.validate(id, sectionMap(value))
                }
            }
            publish(next)
        } catch (error: Exception) {
            bukkitPlugin.sendException("玩家宿主配置读取失败，原设置继续使用", error)
        }
    }

    @Synchronized fun register(id: String, patch: Map<String, Any?>) {
        val valid = HostProfileValidation.validate(id, patch)
        publish(LinkedHashMap(current.profiles).apply { put(id, valid) })
    }

    @Synchronized fun unregister(id: String) {
        publish(LinkedHashMap(current.profiles).apply { remove(id) })
    }

    /** Register/unregister are batched; call once after editing the registry. */
    fun syncPlayers() {
        AsteroidScheduler.ensureMainThread(bukkitPlugin) {
            ArcartXEntityManager.players.values.forEach { handler ->
                if (handler.encryptor != null) NetworkMessageSender.sendSettingReload(handler.player)
            }
            ArcartXEntityManager.players.values.forEach { handler -> handler.syncAppearanceProfiles() }
        }
    }

    private fun publish(next: Map<String, Map<String, Any?>>) {
        require(next.size <= 256) { "玩家宿主配置数量不能超过 256" }
        if (next != current.profiles) current = PlayerHostRegistrySnapshot(
            current.revision + 1, Collections.unmodifiableMap(LinkedHashMap(next))
        )
    }

    companion object {
        private fun sectionMap(section: ConfigurationSection): Map<String, Any?> =
            section.getValues(false).mapValues { (_, value) ->
                if (value is ConfigurationSection) sectionMap(value) else value
            }
    }
}
