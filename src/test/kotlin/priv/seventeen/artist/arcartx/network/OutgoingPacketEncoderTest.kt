/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import priv.seventeen.artist.arcartx.network.encryptor.AESEncryptor
import priv.seventeen.artist.arcartx.network.encryptor.Base64Encryptor
import priv.seventeen.artist.arcartx.network.message.DecodeType
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.crypto.spec.SecretKeySpec

class OutgoingPacketEncoderTest {
    @Test
    fun `normal packets retain the existing client framing at fragment boundaries`() {
        for (length in listOf(0, 1, 22499, 22500, 22501, 45000, 45001, 225000)) {
            val original = "x".repeat(length)
            val frames = OutgoingPacketEncoder.encode(OutgoingPacket(73, 25, DecodeType.NORMAL, original))
            val messages = frames.map(::readFrame)
            val expectedParts = ((Base64Encryptor.base64Encrypt(original).length + 29999) / 30000).coerceAtLeast(1)
            assertEquals(expectedParts, messages.size)
            messages.forEachIndexed { part, message ->
                assertEquals(73, message.id)
                assertEquals(25, message.code)
                assertEquals(DecodeType.NORMAL.id, message.type)
                assertEquals(expectedParts, message.size)
                assertEquals(part, message.part)
                assertTrue(message.message.length <= 30000)
            }
            assertEquals(original, Base64Encryptor.base64Decrypt(messages.joinToString("") { it.message }))
        }
    }

    @Test
    fun `small plaintext and gzip unicode payloads are both readable by existing clients`() {
        val small = OutgoingPacketEncoder.encode(OutgoingPacket(1, 2, DecodeType.NORMAL, "{}"))
        assertEquals('{'.code.toByte(), small.single()[1])
        val json = "{\"text\":\"" + "武器动画 🎮 ".repeat(4000) + "\"}"
        val frames = OutgoingPacketEncoder.encode(OutgoingPacket(2, 3, DecodeType.NORMAL, json))
        assertEquals(0x1f.toByte(), frames.first()[1])
        assertEquals(0x8b.toByte(), frames.first()[2])
        assertEquals(json, Base64Encryptor.base64Decrypt(frames.map(::readFrame).joinToString("") { it.message }))
    }

    @Test
    fun `aes packets use the captured key and remain decryptable after fragmentation`() {
        val key = SecretKeySpec(ByteArray(16) { it.toByte() }, "AES")
        val json = "{\"files\":[\"" + "资源/model.png,".repeat(4000) + "\"]}"
        val frames = OutgoingPacketEncoder.encode(OutgoingPacket(8, 9, DecodeType.AES, json, key))
        val messages = frames.map(::readFrame)
        assertTrue(messages.size > 1)
        assertTrue(messages.all { it.type == DecodeType.AES.id })
        val encryptor = AESEncryptor().apply { aesKey = key }
        assertEquals(json, encryptor.decode(messages.joinToString("") { it.message }))
    }

    @Test
    fun `aes without a negotiated key cannot emit an empty packet`() {
        assertThrows(IllegalStateException::class.java) {
            OutgoingPacketEncoder.encode(OutgoingPacket(1, 2, DecodeType.AES, "{}"))
        }
    }

    private fun readFrame(frame: ByteArray): BaseMessage {
        assertEquals(32.toByte(), frame[0])
        assertTrue(frame.size < 32766, "Plugin message exceeds the channel limit")
        val body = frame.copyOfRange(1, frame.size)
        // 使用 JDK GZIP 校验 CRC/trailer，避免编码器和项目解码器共享同一错误。
        val json = if (body.size >= 2 && body[0] == 0x1f.toByte() && body[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
        } else body
        return Gson().fromJson(json.toString(Charsets.UTF_8), BaseMessage::class.java)
    }
}
