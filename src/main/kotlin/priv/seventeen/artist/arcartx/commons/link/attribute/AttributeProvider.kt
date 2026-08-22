/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link.attribute

import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack

/** 属性系统提供者接口 */
interface AttributeProvider {

    fun getIdentifier(): String

    fun addAttribute(livingEntity: LivingEntity, sourceID: String, list: List<String>)

    fun addAttribute(livingEntity: LivingEntity, sourceID: String, itemStack: ItemStack)

    fun removeAttribute(livingEntity: LivingEntity, sourceID: String)


    fun replaceAttribute(livingEntity: LivingEntity, sourceID: String, itemStack: ItemStack) {
        removeAttribute(livingEntity, sourceID)
        addAttribute(livingEntity, sourceID, itemStack)
    }


    fun rebuildItem(livingEntity: LivingEntity, sourceID: String, itemStack: ItemStack): ItemStack = itemStack

}
