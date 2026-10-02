/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.util

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/** 字节数组压缩工具 */
object ByteArrayUtils {

    private const val COMPRESSION_THRESHOLD = 256

    /** 解压后数据的硬上限，防止恶意客户端用伪造的原始长度触发巨量内存分配 */
    private const val MAX_DECOMPRESSED_SIZE = 2 * 1024 * 1024

    private val deflaterPool = ThreadLocal.withInitial { Deflater(Deflater.BEST_SPEED, true) }
    private val inflaterPool = ThreadLocal.withInitial { Inflater(true) }

    internal fun releaseCompressionResources() {
        deflaterPool.get().end()
        deflaterPool.remove()
    }

    fun compressIfNeeded(data: ByteArray): ByteArray {
        if (data.size < COMPRESSION_THRESHOLD) {
            return data
        }
        return compressGzip(data)
    }

    private fun compressGzip(data: ByteArray): ByteArray {
        val estimatedSize = (data.size * 0.7).toInt().coerceAtLeast(64)
        val output = ByteArrayOutputStream(estimatedSize + 18)

        output.write(byteArrayOf(0x1f.toByte(), 0x8b.toByte(), 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        val deflater = deflaterPool.get()
        deflater.reset()
        deflater.setInput(data)
        deflater.finish()

        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }

        val crc = CRC32()
        crc.update(data)
        val crcValue = crc.value.toInt()
        val size = data.size

        output.write(byteArrayOf(
            (crcValue and 0xff).toByte(),
            ((crcValue shr 8) and 0xff).toByte(),
            ((crcValue shr 16) and 0xff).toByte(),
            ((crcValue shr 24) and 0xff).toByte(),
            (size and 0xff).toByte(),
            ((size shr 8) and 0xff).toByte(),
            ((size shr 16) and 0xff).toByte(),
            ((size shr 24) and 0xff).toByte()
        ))

        return output.toByteArray()
    }


    fun decompress(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        if (data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            return decompressGzip(data)
        }
        return data
    }

    private fun decompressGzip(data: ByteArray): ByteArray {
        if (data.size < 18) return data

        // 尾部 4 字节是客户端声明的原始长度，仅用于参考与校验，绝不能据此直接分配内存
        val declaredSize = ((data[data.size - 4].toInt() and 0xff) or
                ((data[data.size - 3].toInt() and 0xff) shl 8) or
                ((data[data.size - 2].toInt() and 0xff) shl 16) or
                ((data[data.size - 1].toInt() and 0xff) shl 24))
        require(declaredSize in 0..MAX_DECOMPRESSED_SIZE) { "非法的解压长度: $declaredSize" }

        val inflater = inflaterPool.get()
        inflater.reset()
        inflater.setInput(data, 10, data.size - 18)

        // 流式解压：以声明长度作为初始容量提示，但真实写入量按 MAX 上限实时校验
        val output = ByteArrayOutputStream(declaredSize.coerceAtLeast(64))
        val buffer = ByteArray(8192)
        var total = 0
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                // 数据被截断或畸形，没有更多输入可解压，中止以避免死循环
                break
            }
            total += count
            require(total <= MAX_DECOMPRESSED_SIZE) { "解压数据超过上限 $MAX_DECOMPRESSED_SIZE 字节" }
            output.write(buffer, 0, count)
        }

        return output.toByteArray()
    }

}
