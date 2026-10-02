/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx

import priv.seventeen.artist.arcartx.command.*
import priv.seventeen.artist.arcartx.core.config.ArcartXConfigs
import priv.seventeen.artist.arcartx.database.DatabaseManager
import priv.seventeen.artist.arcartx.commons.command.AXCommand
import priv.seventeen.artist.arcartx.commons.command.AXCommandContainer
import priv.seventeen.artist.arcartx.language.AXLanguageKey
import priv.seventeen.artist.arcartx.language.L
import priv.seventeen.artist.arcartx.language.language
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.printStart
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendMessage
import priv.seventeen.artist.arcartx.network.NetworkManager
import priv.seventeen.artist.arcartx.network.OutgoingPacketDispatcher
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle

object ArcartX {

    private val commands: AXCommandContainer by lazy { AXCommandContainer(bukkitPlugin) }

    val axCommand: AXCommand = AXCommand("arcartx", "ax", "a", "aa", "ar")

    val configs: ArcartXConfigs by lazy { ArcartXConfigs() }

    var databaseManager: DatabaseManager? = null
        private set

    private var networkManager: NetworkManager? = null

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        language.reload()
        bukkitPlugin.printStart("&bArc&3art&1X", 1)
        registerCommands()
        databaseManager = DatabaseManager(configs.setting.database)
        networkManager = NetworkManager()
        OutgoingPacketDispatcher.start()
        bukkitPlugin.sendMessage(L(AXLanguageKey.SERVER_VERSION, AsteroidAPI.getMcVersion() ?: "unknown"))

    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        OutgoingPacketDispatcher.stop()
        AsteroidScheduler.cancelAll()
        databaseManager?.close()
        networkManager?.close()
    }

    private fun registerCommands() {
        commands.register(axCommand.apply {
            registerSubCommand(ScreenCommand())
            registerSubCommand(ModelCommand())
            registerSubCommand(CameraCommand())
            registerSubCommand(SoundCommand())
            registerSubCommand(WaypointCommand())
            registerSubCommand(DamageDisplayCommand())
            registerSubCommand(ItemEditCommand())
            registerSubCommand(AreaCommand())
            registerSubCommand(BedrockParticleCommand())
            registerSubCommand(ShaderCommand())
            registerSubCommand(SkyBoxCommand())
            registerSubCommand(ParserCommand())
            registerSubCommand(Crc64Command())
            registerSubCommand(ResourceSyncCommand())
            registerSubCommand(ReloadCommand())
        })
    }
}
