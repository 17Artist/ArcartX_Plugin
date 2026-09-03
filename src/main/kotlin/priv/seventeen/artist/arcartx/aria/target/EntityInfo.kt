/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.aria.target

import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler
import priv.seventeen.artist.aria.callable.InvocationData

/** 实体信息 */
object EntityInfo {

    @JvmStatic
    @AriaInvokeHandler(value = "getName", target = LivingEntity::class)
    fun getName(data: InvocationData): String {
        val entity = data.target as LivingEntity
        return entity.name
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getUniqueId", target = LivingEntity::class)
    fun getUniqueId(data: InvocationData): String {
        val entity = data.target as LivingEntity
        return entity.uniqueId.toString()
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getEntityType", target = LivingEntity::class)
    fun getEntityType(data: InvocationData): String {
        val entity = data.target as LivingEntity
        return entity.type.name
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getWorldName", target = LivingEntity::class)
    fun getWorldName(data: InvocationData): String {
        val entity = data.target as LivingEntity
        return entity.world.name
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getVelocityX", target = LivingEntity::class)
    fun getVelocityX(data: InvocationData): Double {
        val entity = data.target as LivingEntity
        return entity.velocity.x
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getVelocityY", target = LivingEntity::class)
    fun getVelocityY(data: InvocationData): Double {
        val entity = data.target as LivingEntity
        return entity.velocity.y
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getVelocityZ", target = LivingEntity::class)
    fun getVelocityZ(data: InvocationData): Double {
        val entity = data.target as LivingEntity
        return entity.velocity.z
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getFireTicks", target = LivingEntity::class)
    fun getFireTicks(data: InvocationData): Int {
        val entity = data.target as LivingEntity
        return entity.fireTicks
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getFreezeTicks", target = LivingEntity::class)
    fun getFreezeTicks(data: InvocationData): Int {
        val entity = data.target as LivingEntity
        return entity.freezeTicks
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getFallDistance", target = LivingEntity::class)
    fun getFallDistance(data: InvocationData): Float {
        val entity = data.target as LivingEntity
        return entity.fallDistance
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getTicksLived", target = LivingEntity::class)
    fun getTicksLived(data: InvocationData): Int {
        val entity = data.target as LivingEntity
        return entity.ticksLived
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isDead", target = LivingEntity::class)
    fun isDead(data: InvocationData): Boolean {
        val entity = data.target as LivingEntity
        return entity.isDead
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isValid", target = LivingEntity::class)
    fun isValid(data: InvocationData): Boolean {
        val entity = data.target as LivingEntity
        return entity.isValid
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isInvulnerable", target = LivingEntity::class)
    fun isInvulnerable(data: InvocationData): Boolean {
        val entity = data.target as LivingEntity
        return entity.isInvulnerable
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isSilent", target = LivingEntity::class)
    fun isSilent(data: InvocationData): Boolean {
        val entity = data.target as LivingEntity
        return entity.isSilent
    }

    @JvmStatic
    @AriaInvokeHandler(value = "hasAI", target = LivingEntity::class)
    fun hasAI(data: InvocationData): Boolean {
        val entity = data.target as LivingEntity
        return entity.hasAI()
    }

    @JvmStatic
    @AriaInvokeHandler(value = "hasGravity", target = LivingEntity::class)
    fun hasGravity(data: InvocationData): Boolean {
        val entity = data.target as LivingEntity
        return entity.hasGravity()
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isInWater", target = LivingEntity::class)
    fun isInWater(data: InvocationData): Boolean {
        val player = data.target as LivingEntity
        return player.isInWater
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isFallFlying", target = LivingEntity::class)
    fun isFallFlying(data: InvocationData): Boolean {
        val player = data.target as LivingEntity
        return player.isGliding
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isSwimming", target = LivingEntity::class)
    fun isSwimming(data: InvocationData): Boolean {
        val player = data.target as LivingEntity
        return player.isSwimming
    }


    @JvmStatic
    @AriaInvokeHandler(value = "isSleeping", target = LivingEntity::class)
    fun isSleeping(data: InvocationData): Boolean {
        val player = data.target as LivingEntity
        return player.isSleeping
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getPosX", target = LivingEntity::class)
    fun getPosX(data: InvocationData): Double {
        val player = data.target as LivingEntity
        return player.location.x
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getPosY", target = LivingEntity::class)
    fun getPosY(data: InvocationData): Double {
        val player = data.target as LivingEntity
        return player.location.y
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getPosZ", target = LivingEntity::class)
    fun getPosZ(data: InvocationData): Double {
        val player = data.target as LivingEntity
        return player.location.z
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getYaw", target = LivingEntity::class)
    fun getYaw(data: InvocationData): Float {
        val player = data.target as LivingEntity
        return player.location.yaw
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getPitch", target = LivingEntity::class)
    fun getPitch(data: InvocationData): Float {
        val player = data.target as LivingEntity
        return player.location.pitch
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getAir", target = LivingEntity::class)
    fun getAir(data: InvocationData): Int {
        val player = data.target as LivingEntity
        return player.remainingAir
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isOnGround", target = LivingEntity::class)
    fun isOnGround(data: InvocationData): Boolean {
        val player = data.target as LivingEntity
        return player.isOnGround
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getHealth", target = LivingEntity::class)
    fun getSelfHealth(data: InvocationData): Double {
        val player = data.target as LivingEntity
        return player.health
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getMaxHealth", target = LivingEntity::class)
    fun getSelfMaxHealth(data: InvocationData): Double {
        val player = data.target as LivingEntity
        return player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isPlayer", target = LivingEntity::class)
    fun isPlayer(data: InvocationData): Boolean {
        val player = data.target as LivingEntity
        return player is Player
    }
}
