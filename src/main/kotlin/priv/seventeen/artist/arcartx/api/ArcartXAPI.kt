/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.api

import priv.seventeen.artist.arcartx.core.area.ArcartXAreaManager
import priv.seventeen.artist.arcartx.core.chatcard.ArcartXChatCardRegistry
import priv.seventeen.artist.arcartx.core.effect.ArcartXEffectManager
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.core.keybind.ArcartXKeyBindRegistry
import priv.seventeen.artist.arcartx.core.network.ArcartXNetworkSender
import priv.seventeen.artist.arcartx.core.sound.ArcartXSoundPlayer
import priv.seventeen.artist.arcartx.core.ui.ArcartXUIRegistry
import priv.seventeen.artist.arcartx.hook.mythicmobs.hitbox.InteractionProxyManager

/**
 * ArcartX 公开 API 入口，为附属插件提供统一的访问接口。
 * 所有方法均为线程安全的单例访问。
 */
object ArcartXAPI {

    /** 获取 UI 注册表，用于注册/管理自定义界面 */
    @JvmStatic
    fun getUIRegistry(): ArcartXUIRegistry = ArcartXUIRegistry

    /** 获取聊天卡片注册表，用于注册、重载、发送和管理自定义聊天卡片 */
    @JvmStatic
    fun getChatCardRegistry(): ArcartXChatCardRegistry = ArcartXChatCardRegistry

    /** 获取按键绑定注册表 */
    @JvmStatic
    fun getKeyBindRegistry(): ArcartXKeyBindRegistry = ArcartXKeyBindRegistry

    /** 获取实体管理器，用于管理 ArcartX 实体数据 */
    @JvmStatic
    fun getEntityManager(): ArcartXEntityManager = ArcartXEntityManager

    /** 获取音效播放器 */
    @JvmStatic
    fun getSoundPlayer(): ArcartXSoundPlayer = ArcartXSoundPlayer

    /** 获取网络发送器，用于向客户端发送自定义数据包 */
    @JvmStatic
    fun getNetworkSender(): ArcartXNetworkSender = ArcartXNetworkSender

    /** 获取区域管理器 */
    @JvmStatic
    fun getAreaManager(): ArcartXAreaManager = ArcartXAreaManager

    /** 获取特效管理器 */
    @JvmStatic
    fun getEffectManager(): ArcartXEffectManager = ArcartXEffectManager

    /** 获取交互代理（命中盒代理）管理器，用于创建/查询/操作实体的交互代理 */
    @JvmStatic
    fun getInteractionProxyManager(): InteractionProxyManager = InteractionProxyManager
}
