/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network

import priv.seventeen.artist.arcartx.network.encryptor.AESEncryptor
import priv.seventeen.artist.arcartx.network.encryptor.Base64Encryptor
import priv.seventeen.artist.arcartx.network.message.DecodeType
import priv.seventeen.artist.arcartx.util.ByteArrayUtils
import priv.seventeen.artist.arcartx.util.JsonUtils.toJson
import java.security.Key

/** 序列化完成的快照；后台编码不再读取玩家、物品或 UI 对象。 */
internal data class OutgoingPacket(
    val id: Int,
    val code: Int,
    val type: DecodeType,
    val json: String,
    val encryptionKey: Key? = null
) {
    val retainedBytes: Long get() = json.length.toLong() * 2
}

internal object OutgoingPacketEncoder {
    private const val FRAGMENT_LENGTH = 30_000

    fun encode(packet: OutgoingPacket): List<ByteArray> {
        val encoded = if (packet.type == DecodeType.AES) {
            val encryptor = AESEncryptor().apply { aesKey = packet.encryptionKey }
            checkNotNull(encryptor.encode(packet.json)) { "ArcartX packet encryption failed" }
        } else {
            Base64Encryptor.base64Encrypt(packet.json)
        }
        val count = ((encoded.length.toLong() + FRAGMENT_LENGTH - 1) / FRAGMENT_LENGTH).toInt().coerceAtLeast(1)
        return List(count) { part ->
            val start = part * FRAGMENT_LENGTH
            val message = BaseMessage().apply {
                id = packet.id
                code = packet.code
                type = packet.type.id
                size = count
                this.part = part
                this.message = encoded.substring(start, minOf(start + FRAGMENT_LENGTH, encoded.length))
            }
            val body = ByteArrayUtils.compressIfNeeded(message.toJson().toByteArray(Charsets.UTF_8))
            ByteArray(body.size + 1).also { frame ->
                frame[0] = NetworkManager.COMPRESSED_FLAG.toByte()
                body.copyInto(frame, 1)
            }
        }
    }
}
