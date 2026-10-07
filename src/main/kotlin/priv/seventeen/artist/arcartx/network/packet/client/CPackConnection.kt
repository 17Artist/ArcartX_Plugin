/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network.packet.client

import com.google.gson.annotations.SerializedName
import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.core.playerhost.PlayerHostCapabilities
import priv.seventeen.artist.arcartx.language.AXLanguageKey
import priv.seventeen.artist.arcartx.language.L
import priv.seventeen.artist.arcartx.commons.message.ArcartXSender.Companion.sendMessage
import priv.seventeen.artist.arcartx.network.NetworkMessageSender
import priv.seventeen.artist.arcartx.network.encryptor.AESEncryptor
import priv.seventeen.artist.arcartx.network.encryptor.Base64Encryptor
import priv.seventeen.artist.arcartx.network.encryptor.DHEncryptor
import priv.seventeen.artist.arcartx.util.PlayerUtils.arcartXHandler
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler.ensureMainThread
import priv.seventeen.artist.arcartx.web.resource.ArcartXResourceClient
import priv.seventeen.artist.blink.bukkitPlugin
import javax.crypto.SecretKey

/** 客户端连接包，处理 DH 密钥交换、CRC64 校验和资源同步 */
class CPackConnection : ClientPacket {

    @SerializedName("message")
    var message: String? = null

    @SerializedName("code")
    var code: List<String> = ArrayList()

    @SerializedName("resource")
    private val resource: Map<String, String> = HashMap()

    @SerializedName("capabilities")
    private val capabilities: List<String?>? = null

    override fun handle(player: Player) {
        val playerEntity = ArcartXEntityManager.getPlayer(player) ?: return

        // 客户端输入边界校验：丢弃畸形/超大数据，防御 DoS
        val msg = message
        if (msg.isNullOrBlank() || msg.length > MAX_KEY_LENGTH) return
        if (code.size > MAX_CODE_ENTRIES || resource.size > MAX_RESOURCE_ENTRIES) return
        val supported = PlayerHostCapabilities.parse(capabilities) ?: return

        // 初始化加密器
        val secretKey = createSecretKey(playerEntity.key)
        val encryptor = AESEncryptor().apply { aesKey = secretKey }
        playerEntity.encryptor = encryptor

        // 解析 CRC64 列表
        val crc64List = parseClientCrc64(encryptor)

        // CRC64 校验
        if (!checkCrc64(player, crc64List)) {
            return
        }
        playerEntity.setClientCapabilities(supported)

        // 资源文件检测与同步
        sendOrUpdate(player)
    }


    private fun createSecretKey(localKey: String?): SecretKey {
        return DHEncryptor.getSecretKey(
            Base64Encryptor.base64DecryptByte(message),
            Base64Encryptor.base64DecryptByte(localKey)
        )
    }


    private fun parseClientCrc64(encryptor: AESEncryptor): List<Long> {
        return try {
            code.map { encryptor.decode(it)?.toLong() ?: 0L }
        } catch (e: Exception) {
            listOf(0L)
        }
    }


    private fun checkCrc64(player: Player, clientCrc: List<Long>): Boolean {
        val config = ArcartX.configs.setting.crc64Setting

        // 未启用校验时直接通过
        if (!config.enable) return true

        val serverCrcList = config.list
        val isOp = player.isOp

        // 检查列表长度
        val sizeMismatch = when {
            !config.allowPartial && clientCrc.size != serverCrcList.size -> true
            clientCrc.size > serverCrcList.size -> true
            else -> false
        }

        // 检查 CRC 值是否匹配
        val valueMismatch = clientCrc.zip(serverCrcList).any { (client, server) -> client != server }

        if (sizeMismatch || valueMismatch) {
            return handleCrcMismatch(player, clientCrc, isOp)
        }

        return true
    }


    private fun handleCrcMismatch(player: Player, clientCrc: List<Long>, isOp: Boolean): Boolean {
        if (isOp) {
            // OP 允许临时通过，但给予提示
            ArcartXEntityManager.getPlayer(player)?.crc64?.addAll(clientCrc)

            with(bukkitPlugin) {
                sendMessage(player, L(AXLanguageKey.CRC_MISMATCH))
                clientCrc.forEach { sendMessage(player, L(AXLanguageKey.CRC_LIST_ITEM, it.toString())) }
                sendMessage(player, L(AXLanguageKey.CRC_UPDATE_COMMAND))
            }
            return true
        }

        bukkitPlugin.ensureMainThread { player.kickPlayer(L(AXLanguageKey.KICK_VERIFY_FAILED)) }
        return false
    }


    private fun sendOrUpdate(player: Player) {
        val handler = player.arcartXHandler

        // 无需资源同步的情况：直接发送重载信号
        if (handler == null || handler.sentResource) {
            NetworkMessageSender.sendResourceReload(player, true)
            return
        }

        handler.sentResource = true

        val client = ArcartXResourceClient.getInstance()
        if (client == null) {
            NetworkMessageSender.sendResourceReload(player, true)
            return
        }

        // 计算需要更新和删除的文件
        val (filesToUpdate, filesToDelete) = calculateResourceDiff(client)

        // 如果没有需要更新或删除的文件，只发送重载信号
        if (filesToUpdate.isEmpty() && filesToDelete.isEmpty()) {
            NetworkMessageSender.sendResourceReload(player, true)
            return
        }

        // 有需要同步的文件，生成下载链接并发送
        client.generateSignedDownloadLinkAsync(filesToUpdate, 15, 2) { map ->
            filesToDelete.forEach { name ->
                if(!map.containsKey(name)) {
                    map[name] = "delete"
                }
            }
            bukkitPlugin.sendMessage(L(AXLanguageKey.SEND_RESOURCE_UPDATE, filesToUpdate.size.toString()))
            NetworkMessageSender.sendResourceReload(player, map)
        }
    }


    private fun calculateResourceDiff(client: ArcartXResourceClient): Pair<List<String>, List<String>> {
        val serverFiles = client.cachedCrc64List
        val clientFiles = resource

        val serverCrc64Set = serverFiles.values.toSet()
        val clientCrc64Set = clientFiles.values.toSet()

        // 客户端有但服务端没有的文件 -> 需要删除
        val filesToDelete = clientFiles.entries
            .filter { (_, crc) -> crc !in serverCrc64Set }
            .map { it.key }

        // 服务端有但客户端没有的文件 -> 需要更新
        val filesToUpdate = serverFiles.entries
            .filter { (_, crc) -> crc !in clientCrc64Set }
            .map { it.key }

        return Pair(filesToUpdate, filesToDelete)
    }

    companion object {
        /** 客户端公钥 base64 串长度上限（DH-2048 公钥约数百字节，留足余量） */
        private const val MAX_KEY_LENGTH = 8192
        /** 客户端 CRC64 列表条目上限，防御超大列表 */
        private const val MAX_CODE_ENTRIES = 100_000
        /** 客户端资源清单条目上限 */
        private const val MAX_RESOURCE_ENTRIES = 100_000
    }
}
