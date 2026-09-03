/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.aria.target

import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.util.PlaceholderUtils.placeholder
import priv.seventeen.artist.arcartx.util.PlayerUtils.arcartXHandler
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler
import priv.seventeen.artist.aria.callable.InvocationData

/** 玩家信息 */
object PlayerInfo {

    @JvmStatic
    @AriaInvokeHandler(value = "getLevel", target = Player::class)
    fun getLevel(data: InvocationData): Int {
        val player = data.target as Player
        return player.level
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getExp", target = Player::class)
    fun getExp(data: InvocationData): Float {
        val player = data.target as Player
        return player.exp
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getExpToLevel", target = Player::class)
    fun getExpToLevel(data: InvocationData): Int {
        val player = data.target as Player
        return player.expToLevel
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getTotalExperience", target = Player::class)
    fun getTotalExperience(data: InvocationData): Int {
        val player = data.target as Player
        return player.totalExperience
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getGameMode", target = Player::class)
    fun getGameMode(data: InvocationData): String {
        val player = data.target as Player
        return player.gameMode.name
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getLocale", target = Player::class)
    fun getLocale(data: InvocationData): String {
        val player = data.target as Player
        return player.locale
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getPing", target = Player::class)
    fun getPing(data: InvocationData): Int {
        val player = data.target as Player
        return player.ping
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getSaturation", target = Player::class)
    fun getSaturation(data: InvocationData): Float {
        val player = data.target as Player
        return player.saturation
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getExhaustion", target = Player::class)
    fun getExhaustion(data: InvocationData): Float {
        val player = data.target as Player
        return player.exhaustion
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getWalkSpeed", target = Player::class)
    fun getWalkSpeed(data: InvocationData): Float {
        val player = data.target as Player
        return player.walkSpeed
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getFlySpeed", target = Player::class)
    fun getFlySpeed(data: InvocationData): Float {
        val player = data.target as Player
        return player.flySpeed
    }

    @JvmStatic
    @AriaInvokeHandler(value = "hasPermission", target = Player::class)
    fun hasPermission(data: InvocationData): Boolean {
        if (data.size() != 1) return false
        val permission = data[0].stringValue()
        if (permission.isBlank()) return false
        val player = data.target as Player
        return player.hasPermission(permission)
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isOp", target = Player::class)
    fun isOp(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.isOp
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isOnline", target = Player::class)
    fun isOnline(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.isOnline
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isBlocking", target = Player::class)
    fun isBlocking(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.isBlocking
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isFlightAllowed", target = Player::class)
    fun isFlightAllowed(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.allowFlight
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isAfterJump", target = Player::class)
    fun isAfterJump(data: InvocationData): Boolean {
        val arcartXPlayer = (data.target as Player).arcartXHandler ?: return false
        return arcartXPlayer.jumpTime > System.currentTimeMillis()
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isSprinting", target = Player::class)
    fun isRun(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.isSprinting
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isSneaking", target = Player::class)
    fun isSneak(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.isSneaking
    }

    @JvmStatic
    @AriaInvokeHandler(value = "isFlying", target = Player::class)
    fun isFlying(data: InvocationData): Boolean {
        val player = data.target as Player
        return player.isFlying
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getFood", target = Player::class)
    fun getFood(data: InvocationData): Int {
        val player = data.target as Player
        return player.foodLevel
    }

    @JvmStatic
    @AriaInvokeHandler(value = "getPlaceholder", target = Player::class)
    fun getPlaceholder(data: InvocationData): String {
        if (data.size() != 1) return ""
        val player = data.target as Player
        return data.get(0).stringValue().placeholder(player)
    }
}
