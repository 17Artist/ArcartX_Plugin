/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link.attribute

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.arcartx.commons.link.ArcartXLinkManager
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.event.SetCountsChangedEvent
import priv.seventeen.artist.symphony.api.service.ItemMutationOutcome
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult

/** ArcartX-owned adapter that exposes extra-slot items as Symphony sources. */
class SymphonyAttributeProvider(
    private val api: SymphonyApi,
    private val onFailure: (String) -> Unit
) : AttributeProvider, Listener {
    override fun getIdentifier(): String = IDENTIFIER

    override fun addAttribute(livingEntity: LivingEntity, sourceID: String, list: List<String>) {
        handle(api.sources.replaceSourceFromLines(livingEntity, source(sourceID), list))
    }

    override fun addAttribute(livingEntity: LivingEntity, sourceID: String, itemStack: ItemStack) {
        replaceAttribute(livingEntity, sourceID, itemStack)
    }

    override fun replaceAttribute(livingEntity: LivingEntity, sourceID: String, itemStack: ItemStack) {
        val key = source(sourceID)
        handle(
            if (itemStack.type.isAir) api.sources.removeSource(livingEntity, key)
            else api.sources.replaceSourceFromItem(livingEntity, key, itemStack)
        )
    }

    override fun removeAttribute(livingEntity: LivingEntity, sourceID: String) {
        handle(api.sources.removeSource(livingEntity, source(sourceID)))
    }

    override fun rebuildItem(livingEntity: LivingEntity, sourceID: String, itemStack: ItemStack): ItemStack =
        when (val outcome = api.items.rebuild(itemStack, livingEntity)) {
            is ItemMutationOutcome.Success -> outcome.itemStack
            is ItemMutationOutcome.Failure -> {
                onFailure("$sourceID: ${outcome.reason}")
                itemStack
            }
        }

    @EventHandler
    fun onSetCountsChanged(event: SetCountsChangedEvent) {
        val player = event.entity as? Player ?: return
        runCatching {
            ArcartXLinkManager.refreshAttributeItems(player, IDENTIFIER)
        }.onFailure { error ->
            onFailure(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun source(sourceID: String): AttributeSourceKey = AttributeSourceKey("arcartx", sourceID)

    private fun handle(result: SourceUpdateResult) {
        if (result is SourceUpdateResult.Rejected) onFailure(result.reason)
    }

    companion object {
        const val IDENTIFIER: String = "Symphony"
    }
}

object SymphonyLinker {
    fun install(onFailure: (String) -> Unit): Boolean {
        val api = Bukkit.getServicesManager().load(SymphonyApi::class.java) ?: return false
        val provider = SymphonyAttributeProvider(api, onFailure)
        ArcartXLinkManager.registerAttributeProvider(provider)
        Bukkit.getPluginManager().registerEvents(provider, bukkitPlugin)
        return true
    }
}
