/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.commons.link.attribute.AttributeProvider
import priv.seventeen.artist.arcartx.commons.link.economy.EconomyProvider
import priv.seventeen.artist.arcartx.commons.link.item.ItemProvider
import priv.seventeen.artist.arcartx.commons.link.papi.AXPlaceholder
import priv.seventeen.artist.arcartx.commons.link.papi.PlaceholderHooker
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager

/** 第三方插件集成管理器 */
object ArcartXLinkManager {


    private val attributeProviders: MutableMap<String, AttributeProvider> = mutableMapOf()

    private val economyProviders: MutableMap<String, EconomyProvider> = mutableMapOf()

    private val itemProviders: MutableMap<String, ItemProvider> = mutableMapOf()

    private var placeholderHook: PlaceholderHooker? = null


    fun registerAttributeProvider(attributeProvider: AttributeProvider) {
        attributeProviders[attributeProvider.getIdentifier()] = attributeProvider
    }

    fun registerEconomyProvider(economyProvider: EconomyProvider) {
        economyProviders[economyProvider.getIdentifier()] = economyProvider
    }

    fun registerItemProvider(itemProvider: ItemProvider) {
        itemProviders[itemProvider.getIdentifier()] = itemProvider
    }

    fun getAttributeProvider(name: String): AttributeProvider? {
        return attributeProviders[name]
    }

    fun refreshAttributeItems(livingEntity: LivingEntity, providerIdentifier: String): Int {
        check(Bukkit.isPrimaryThread()) { "Extra-slot item refresh must run on the Bukkit primary thread" }
        val player = livingEntity as? Player ?: return 0
        val provider = attributeProviders[providerIdentifier] ?: return 0
        return ArcartXEntityManager.getPlayer(player)?.refreshAttributeSlotItems(providerIdentifier, provider) ?: 0
    }

    fun getEconomyProvider(name: String): EconomyProvider? {
        return economyProviders[name]
    }

    fun getItemProvider(name: String): ItemProvider? {
        return itemProviders[name]
    }


    fun registerPlaceholder(placeholder: AXPlaceholder){
        if(placeholderHook == null){
            if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
                placeholderHook = PlaceholderHooker()
            }
        }
        placeholderHook?.register(placeholder)
    }

    fun unregisterPlaceholder(placeholder: AXPlaceholder){
        if(placeholderHook == null){
            if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
                placeholderHook = PlaceholderHooker()
            }
        }
        placeholderHook?.unregister(placeholder)
    }


}
