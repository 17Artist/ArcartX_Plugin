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
import priv.seventeen.artist.arcartx.util.PlayerUtils.arcartXHandler
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler
import priv.seventeen.artist.aria.callable.InvocationData

object PlayerFirstPersonAnimation {

    /**
     * 按持续时间播放第一人称动画。
     * 参数: 动画名称, 播放速度, 持续时间（毫秒）
     */
    @JvmStatic
    @AriaInvokeHandler(value = "playFirstPersonAnimationByTime", target = Player::class)
    fun playFirstPersonAnimationByTime(data: InvocationData) {
        if (data.size() != 3) return
        val arcartXPlayer = (data.target as Player).arcartXHandler ?: return
        val animation = data[0].stringValue()
        val speed = data[1].doubleValue()
        val keepTime = data[2].intValue()
        arcartXPlayer.playFirstPersonAnimationByTime(animation, speed, keepTime)
    }

    /**
     * 按播放次数播放第一人称动画。
     * 参数: 动画名称, 播放速度, 播放次数
     */
    @JvmStatic
    @AriaInvokeHandler(value = "playFirstPersonAnimationByCountOf", target = Player::class)
    fun playFirstPersonAnimationByCountOf(data: InvocationData) {
        if (data.size() != 3) return
        val arcartXPlayer = (data.target as Player).arcartXHandler ?: return
        val animation = data[0].stringValue()
        val speed = data[1].doubleValue()
        val count = data[2].intValue()
        arcartXPlayer.playFirstPersonAnimationByCountOf(animation, speed, count)
    }
}
