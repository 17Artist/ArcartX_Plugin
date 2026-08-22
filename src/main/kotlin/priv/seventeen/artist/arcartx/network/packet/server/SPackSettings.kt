/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network.packet.server

import com.google.gson.annotations.SerializedName
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.arcartx.core.chatcard.ArcartXChatCardRegistry
import priv.seventeen.artist.arcartx.core.config.animation.Controller
import priv.seventeen.artist.arcartx.core.config.bossbar.BossBar
import priv.seventeen.artist.arcartx.core.config.camera.CameraElement
import priv.seventeen.artist.arcartx.core.config.camera.CameraSetting
import priv.seventeen.artist.arcartx.core.config.card.ChatCard
import priv.seventeen.artist.arcartx.core.config.damagedisplay.DamageDisplayData
import priv.seventeen.artist.arcartx.core.config.font.FontIcon
import priv.seventeen.artist.arcartx.core.config.hologram.data.Hologram
import priv.seventeen.artist.arcartx.core.config.hologram.entity.HologramEntityElement
import priv.seventeen.artist.arcartx.core.config.hologram.world.HologramLocationElement
import priv.seventeen.artist.arcartx.core.config.itemeffect.ItemEffectData
import priv.seventeen.artist.arcartx.core.config.key.client.ClientKeyElement
import priv.seventeen.artist.arcartx.core.config.key.group.KeyGroupElement
import priv.seventeen.artist.arcartx.core.config.key.simple.SimpleKeyElement
import priv.seventeen.artist.arcartx.core.config.model.EntityModelData
import priv.seventeen.artist.arcartx.core.config.modellink.ModelLinkData
import priv.seventeen.artist.arcartx.core.config.ui.type.Tip
import priv.seventeen.artist.arcartx.core.config.ui.type.UI
import priv.seventeen.artist.arcartx.core.config.waypoint.WaypointData
import priv.seventeen.artist.arcartx.core.controller.ControllerRegistry
import priv.seventeen.artist.arcartx.core.ui.ArcartXUIRegistry

class SPackSettings : ServerPacket {

    @SerializedName("title")
    private val title: String = ArcartX.configs.setting.title

    @SerializedName("fontIconSetting")
    private val fontIconSetting: Map<String, FontIcon> = ArcartX.configs.fontIconFolder.fontIconIds

    @SerializedName("clientKeyElements")
    private val clientKeyElements: Map<String, ClientKeyElement> = ArcartX.configs.clientKeyFolder.elementMap

    @SerializedName("keyGroupElements")
    private val keyGroupElements: Map<String, KeyGroupElement> = ArcartX.configs.keyGroupFolder.elementMap

    @SerializedName("simpleKeyElements")
    private val simpleKeyElements: Map<String, SimpleKeyElement> = ArcartX.configs.simpleKeyFolder.element

    @SerializedName("ui")
    private val uiData: Collection<UI> = ArcartXUIRegistry.registeredUI.values

    @SerializedName("tip")
    private val tipData: Collection<Tip> = ArcartX.configs.tipFolder.configs.values

    @SerializedName("chat_card")
    private val chatCardData: Collection<ChatCard> = ArcartXChatCardRegistry.registeredChatCards.values

    @SerializedName("boss_bar")
    private val bossBarData: Collection<BossBar> = ArcartX.configs.bossBarFolder.configs.values

    @SerializedName("item_effect")
    private val itemEffectData: Map<String, ItemEffectData> = ArcartX.configs.itemEffectFolder.data

    @SerializedName("hologram_data")
    private val hologramData: Map<String, Hologram> = ArcartX.configs.hologramFolder.configs

    @SerializedName("hologram_location")
    private val hologramLocation: Map<String, HologramLocationElement> = ArcartX.configs.hologramLocationFolder.elements

    @SerializedName("hologram_entity")
    private val hologramEntity: Map<String, HologramEntityElement> = ArcartX.configs.hologramEntityFolder.elements

    @SerializedName("entity_model")
    private val entityModelData: Map<String, EntityModelData> = ArcartX.configs.entityModelFolder.elements

    @SerializedName("action_controller")
    private val actionControllerData: Map<String, Controller> = LinkedHashMap(ControllerRegistry.controllers)

    @SerializedName("camera_preset")
    private val cameraData: Map<String, CameraElement> = ArcartX.configs.cameraPresetFolder.elements

    @SerializedName("camera_setting")
    private val cameraSetting: CameraSetting = ArcartX.configs.cameraSetting

    @SerializedName("waypoint")
    private val waypoint: Map<String, WaypointData> = ArcartX.configs.waypointFolder.elements;

    @SerializedName("damage_display")
    private val damageDisplay: Map<String, DamageDisplayData> = ArcartX.configs.damageDisplayFolder.elements

    @SerializedName("model_link")
    private val modelLink: Map<String, ModelLinkData> = ArcartX.configs.modelLinkFolder.elements

    @SerializedName("isReload")
    private val isReload : Boolean



    constructor() {
        isReload = false
    }

    constructor(isReload: Boolean) {
        this.isReload = isReload
    }
}
