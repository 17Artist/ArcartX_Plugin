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
import priv.seventeen.artist.arcartx.commons.link.attribute.AstraXHeroProvider
import priv.seventeen.artist.arcartx.commons.link.attribute.AttributePlusOldProvider
import priv.seventeen.artist.arcartx.commons.link.attribute.AttributePlusProvider
import priv.seventeen.artist.arcartx.commons.link.attribute.CraneAttributeProvider
import priv.seventeen.artist.arcartx.commons.link.attribute.SymphonyLinker
import priv.seventeen.artist.arcartx.commons.link.economy.PlayerPointsEconomyProvider
import priv.seventeen.artist.arcartx.commons.link.economy.RondoLinker
import priv.seventeen.artist.arcartx.commons.link.economy.VaultEconomyProvider
import priv.seventeen.artist.arcartx.commons.link.item.MythicMobsItemProvider
import priv.seventeen.artist.arcartx.commons.link.item.OvertureItemProvider
import priv.seventeen.artist.arcartx.commons.link.item.NeigeItemsItemProvider
import priv.seventeen.artist.arcartx.commons.link.overture.OvertureLinker
import priv.seventeen.artist.arcartx.language.AXLanguageKey
import priv.seventeen.artist.arcartx.language.L
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendMessage
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle

/** 内置插件集成注册器 */
object BuiltInLinker {

    @Awake(LifeCycle.LOAD)
    fun loadOvertureExtensions() {
        if (Bukkit.getPluginManager().getPlugin("Overture") == null) return
        runCatching {
            OvertureLinker.registerMetaExtensions()
        }.onFailure { error ->
            bukkitPlugin.sendMessage(
                L(
                    AXLanguageKey.OVERTURE_INTEGRATION_FAILED,
                    error.message ?: error.javaClass.simpleName
                )
            )
        }
    }

    @Awake(LifeCycle.ENABLE, priority = 1)
    fun loadProvider() {
        // 属性
        if (Bukkit.getPluginManager().getPlugin("AstraXHero") != null) {
            ArcartXLinkManager.registerAttributeProvider(AstraXHeroProvider())
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_ASTRAXHERO))
        }
        val plugin = Bukkit.getPluginManager().getPlugin("AttributePlus")
        if (plugin != null) {
            if(plugin.description.version.startsWith("3.3.2")) {
                ArcartXLinkManager.registerAttributeProvider( AttributePlusOldProvider())
                bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_ATTRIBUTE_PLUS_OLD))
            } else {
                ArcartXLinkManager.registerAttributeProvider(AttributePlusProvider())
                bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_ATTRIBUTE_PLUS))
            }
        }
        if (Bukkit.getPluginManager().getPlugin("CraneAttribute") != null) {
            ArcartXLinkManager.registerAttributeProvider(CraneAttributeProvider())
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_CRANE_ATTRIBUTE))
        }
        val symphony = Bukkit.getPluginManager().getPlugin("Symphony")
        if (symphony != null && symphony.isEnabled) {
            runCatching {
                check(SymphonyLinker.install { reason ->
                    bukkitPlugin.sendMessage(L(AXLanguageKey.SYMPHONY_INTEGRATION_FAILED, reason))
                }) { "SymphonyApi 服务尚未注册" }
            }.onSuccess {
                bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_SYMPHONY))
            }.onFailure { error ->
                bukkitPlugin.sendMessage(
                    L(AXLanguageKey.SYMPHONY_INTEGRATION_FAILED, error.message ?: error.javaClass.simpleName)
                )
            }
        }

        // 经济
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            ArcartXLinkManager.registerEconomyProvider(VaultEconomyProvider())
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_VAULT))
        }
        if(Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            ArcartXLinkManager.registerEconomyProvider(PlayerPointsEconomyProvider())
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_PLAYER_POINTS))
        }
        val rondo = Bukkit.getPluginManager().getPlugin("Rondo")
        if (rondo != null && rondo.isEnabled) {
            val registered = RondoLinker.registerEconomyProviders()
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_RONDO, registered.toString()))
        }

        // 物品
        if(Bukkit.getPluginManager().getPlugin("NeigeItems") != null) {
            ArcartXLinkManager.registerItemProvider(NeigeItemsItemProvider())
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_NEIGE_ITEMS))
        }
        if(Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            ArcartXLinkManager.registerItemProvider(MythicMobsItemProvider())
            bukkitPlugin.sendMessage(L(AXLanguageKey.FOUND_MYTHICMOBS_ITEM))
        }
        val overture = Bukkit.getPluginManager().getPlugin("Overture")
        if (overture != null && overture.isEnabled) {
            ArcartXLinkManager.registerItemProvider(OvertureItemProvider())
            bukkitPlugin.sendMessage(
                L(AXLanguageKey.FOUND_OVERTURE, OvertureLinker.registeredMetaCount.toString())
            )
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun unloadOvertureExtensions() {
        if (Bukkit.getPluginManager().getPlugin("Overture") == null) return
        OvertureLinker.unregisterMetaExtensions()
    }

}
