/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.config

import priv.seventeen.artist.arcartx.core.config.area.AreaFolder
import priv.seventeen.artist.arcartx.core.config.bossbar.BossBarFolder
import priv.seventeen.artist.arcartx.core.config.camera.CameraPresetFolder
import priv.seventeen.artist.arcartx.core.config.camera.CameraSetting
import priv.seventeen.artist.arcartx.core.config.camera.SceneCameraFolder
import priv.seventeen.artist.arcartx.core.config.card.ChatCardFolder
import priv.seventeen.artist.arcartx.core.config.damagedisplay.DamageDisplayFolder
import priv.seventeen.artist.arcartx.core.config.economy.EconomySetting
import priv.seventeen.artist.arcartx.core.config.font.FontIconFolder
import priv.seventeen.artist.arcartx.core.config.hologram.data.HologramFolder
import priv.seventeen.artist.arcartx.core.config.hologram.entity.HologramEntityFolder
import priv.seventeen.artist.arcartx.core.config.hologram.world.HologramLocationFolder
import priv.seventeen.artist.arcartx.core.config.itemeffect.ItemEffectFolder
import priv.seventeen.artist.arcartx.core.config.key.client.ClientKeyFolder
import priv.seventeen.artist.arcartx.core.config.key.group.KeyGroupFolder
import priv.seventeen.artist.arcartx.core.config.key.simple.SimpleKeyFolder
import priv.seventeen.artist.arcartx.core.config.model.EntityModelFolder
import priv.seventeen.artist.arcartx.core.config.modellink.ModelLinkFolder
import priv.seventeen.artist.arcartx.core.config.script.ScriptsFolder
import priv.seventeen.artist.arcartx.core.config.setting.Setting
import priv.seventeen.artist.arcartx.core.config.slot.SlotFolder
import priv.seventeen.artist.arcartx.core.config.ui.folder.TipFolder
import priv.seventeen.artist.arcartx.core.config.ui.folder.UIFolder
import priv.seventeen.artist.arcartx.core.config.waypoint.WaypointFolder
import priv.seventeen.artist.arcartx.core.playerhost.PlayerHostProfiles
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.event.plugin.ArcartXReloadEvent
import priv.seventeen.artist.arcartx.language.language
import priv.seventeen.artist.arcartx.language.AXLanguageKey
import priv.seventeen.artist.arcartx.language.L
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendException
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.ensureAsyncThread
import priv.seventeen.artist.blink.bukkitPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 全局配置聚合器，管理所有配置文件夹的加载与重载 */
class ArcartXConfigs {

    init {
        compatibilityOld()
    }

    val setting: Setting = Setting()

    val economySetting: EconomySetting = EconomySetting()

    val cameraSetting: CameraSetting = CameraSetting()

    val cameraPresetFolder: CameraPresetFolder = CameraPresetFolder()

    val sceneCameraFolder: SceneCameraFolder = SceneCameraFolder()

    val entityModelFolder: EntityModelFolder = EntityModelFolder()

    val modelLinkFolder: ModelLinkFolder = ModelLinkFolder()

    val playerHostProfiles: PlayerHostProfiles = PlayerHostProfiles()

    val fontIconFolder: FontIconFolder = FontIconFolder()

    val hologramFolder: HologramFolder = HologramFolder()

    val hologramLocationFolder: HologramLocationFolder = HologramLocationFolder()

    val hologramEntityFolder: HologramEntityFolder = HologramEntityFolder()

    val clientKeyFolder: ClientKeyFolder = ClientKeyFolder()

    val keyGroupFolder: KeyGroupFolder = KeyGroupFolder()

    val simpleKeyFolder: SimpleKeyFolder = SimpleKeyFolder()


    val tipFolder: TipFolder = TipFolder()

    val chatCardFolder: ChatCardFolder = ChatCardFolder()

    val bossBarFolder: BossBarFolder = BossBarFolder()

    private val uiFolder: UIFolder = UIFolder()

    val itemEffectFolder: ItemEffectFolder = ItemEffectFolder()

    val slotFolder: SlotFolder = SlotFolder()

    val areaFolder: AreaFolder = AreaFolder()

    val waypointFolder: WaypointFolder = WaypointFolder()

    val damageDisplayFolder: DamageDisplayFolder = DamageDisplayFolder()

    private val scriptsFolder: ScriptsFolder = ScriptsFolder()



    fun reload(withResource: Boolean){
        language.reload()
        setting.reload()
        economySetting.reload()
        entityModelFolder.reload()
        modelLinkFolder.reload()
        playerHostProfiles.reload()

        cameraSetting.reload()
        cameraPresetFolder.reload()
        sceneCameraFolder.reload()

        fontIconFolder.reload()

        hologramFolder.reload()
        hologramLocationFolder.reload()
        hologramEntityFolder.reload()

        clientKeyFolder.reload()
        keyGroupFolder.reload()
        simpleKeyFolder.reload()


        tipFolder.reload()
        uiFolder.reload()
        chatCardFolder.reload()
        bossBarFolder.reload()
        itemEffectFolder.reload()

        slotFolder.reload()

        areaFolder.reload()

        scriptsFolder.reload()

        waypointFolder.reload()

        damageDisplayFolder.reload()

        ArcartXReloadEvent().call()

        ArcartXEntityManager.players.values.parallelStream().forEach {
            if (it.encryptor != null) {
                bukkitPlugin.ensureAsyncThread {
                    if (withResource) {
                        NetworkMessageSender.sendResourceReload(it.player, false)
                    }
                    NetworkMessageSender.sendSettingReload(it.player)
                    AsteroidScheduler.ensureMainThread(bukkitPlugin) {
                        it.syncAppearanceProfiles()
                    }
                }
            }
        }
    }


    private fun compatibilityOld(){
        val oldConfig = File(bukkitPlugin.dataFolder, "mythicmobs/key_words.yml")
        if(oldConfig.exists()){
            try {
                val newConfig = File(bukkitPlugin.dataFolder, "link/mythicmobs_key_words.yml")

                if(!newConfig.exists()){
                    if (!newConfig.parentFile.exists()) {
                        newConfig.parentFile.mkdirs()
                    }
                    Files.move(
                        oldConfig.toPath(),
                        newConfig.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    val oldDir = oldConfig.parentFile
                    if (oldDir.listFiles()?.isEmpty() == true) {
                        oldDir.delete()
                    }
                }
            } catch (e: Exception) {
                bukkitPlugin.sendException(L(AXLanguageKey.EXCEPTION_CONFIG_MIGRATION), e)
            }
        }
    }

}
