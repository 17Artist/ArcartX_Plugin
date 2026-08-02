/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link.overture

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.core.item.ItemSignal
import priv.seventeen.artist.overture.core.meta.Meta

/**
 * ArcartX 对 Overture 的可选扩展。
 */
object OvertureLinker {

    private val scalarTags = listOf(
        "icon",
        "drop",
        "model",
        "armor_texture",
        "costume_model",
        "costume_hide",
        "fp_model",
        "url",
        "cooldown"
    )

    private val registrations = mutableListOf<RegistrationHandle>()

    val registeredMetaCount: Int
        get() = registrations.size


    fun registerMetaExtensions(): Int {
        if (registrations.isNotEmpty()) return registrations.size

        val pending = mutableListOf<RegistrationHandle>()
        try {
            for (tagName in scalarTags) {
                val registrationKey = NamespacedKey(bukkitPlugin, tagName)
                pending += OvertureAPI.registerMeta(bukkitPlugin, registrationKey) { _, value, locked ->
                    ArcartXScalarTagMeta(
                        registrationKey.toString(),
                        tagName,
                        normalizeScalar(tagName, value),
                        locked
                    )
                }
            }

            val extraModelKey = NamespacedKey(bukkitPlugin, "extra_model")
            pending += OvertureAPI.registerMeta(bukkitPlugin, extraModelKey) { section, _, locked ->
                ArcartXExtraModelMeta(
                    extraModelKey.toString(),
                    readExtraModels(section),
                    locked
                )
            }
        } catch (error: Throwable) {
            pending.asReversed().forEach(RegistrationHandle::close)
            throw error
        }

        registrations += pending
        return registrations.size
    }

    fun unregisterMetaExtensions() {
        registrations.asReversed().forEach(RegistrationHandle::close)
        registrations.clear()
    }

    internal fun normalizeScalar(tagName: String, value: Any?): String? {
        if (value == null) return null
        if (tagName != "costume_hide") {
            return value.toString().trim().takeIf(String::isNotEmpty)
        }

        return when (value) {
            is Boolean -> if (value) "1" else "0"
            is Number -> if (value.toDouble() == 0.0) "0" else "1"
            else -> when (value.toString().trim().lowercase()) {
                "1", "true", "yes", "on" -> "1"
                "0", "false", "no", "off" -> "0"
                else -> throw IllegalArgumentException(
                    "arcartx:costume_hide 必须是 true/false、yes/no、on/off 或 1/0"
                )
            }
        }
    }

    private fun readExtraModels(section: ConfigurationSection?): Map<String, String> {
        if (section == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (locator in section.getKeys(false)) {
            val modelId = section.get(locator)?.toString()?.trim().orEmpty()
            if (locator.isNotBlank() && modelId.isNotEmpty()) {
                result[locator] = modelId
            }
        }
        return result
    }
}

private class ArcartXScalarTagMeta(
    override val key: String,
    private val tagName: String,
    private val value: String?,
    override var locked: Boolean
) : Meta() {

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        sourceTag.remove(tagName)
    }

    override fun build(
        player: Player?,
        compound: ItemTag,
        sourceTag: ItemTag,
        signals: Set<ItemSignal>
    ) {
        value ?: return
        sourceTag[tagName] = ItemTagData.of(value)
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        sourceTag.remove(tagName)
    }
}

private class ArcartXExtraModelMeta(
    override val key: String,
    private val models: Map<String, String>,
    override var locked: Boolean
) : Meta() {

    override fun prepareRebuild(compound: ItemTag, sourceTag: ItemTag) {
        sourceTag.remove("extra_model")
    }

    override fun build(
        player: Player?,
        compound: ItemTag,
        sourceTag: ItemTag,
        signals: Set<ItemSignal>
    ) {
        if (models.isEmpty()) return
        val extraModels = ItemTag()
        for ((locator, modelId) in models) {
            extraModels[locator] = ItemTagData.of(modelId)
        }
        sourceTag["extra_model"] = ItemTagData.of(extraModels)
    }

    override fun drop(player: Player?, compound: ItemTag, sourceTag: ItemTag) {
        sourceTag.remove("extra_model")
    }
}
