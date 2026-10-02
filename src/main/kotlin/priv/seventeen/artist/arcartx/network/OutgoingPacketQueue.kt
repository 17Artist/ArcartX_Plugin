/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** 一个连接只允许一个编码/投递批次在途，后来的包不能越过尚未发完的分片。 */
internal class OutgoingPacketQueue(
    private val encoderExecutor: Executor,
    private val deliveryExecutor: Executor,
    private val send: (ByteArray) -> Unit,
    private val onFailure: (Exception) -> Unit,
    private val encode: (OutgoingPacket) -> List<ByteArray> = OutgoingPacketEncoder::encode
) : AutoCloseable {
    private val lock = Any()
    private val pending = ArrayDeque<OutgoingPacket>()
    private var retainedBytes = 0L
    private var retainedPackets = 0
    private var running = false
    private var closed = false

    fun submit(packet: OutgoingPacket): Boolean {
        val start = synchronized(lock) {
            if (closed) return false
            if (retainedPackets >= MAX_PENDING_PACKETS || retainedBytes + packet.retainedBytes > MAX_PENDING_BYTES) {
                throw RejectedExecutionException("ArcartX outgoing packet queue is full")
            }
            pending.addLast(packet)
            retainedBytes += packet.retainedBytes
            retainedPackets++
            if (running) false else {
                running = true
                true
            }
        }
        if (start) dispatch(encoderExecutor, ::encodeBatch)
        return true
    }

    private fun encodeBatch() {
        val batch = synchronized(lock) {
            if (closed) return
            List(minOf(pending.size, ENCODE_BATCH_PACKETS)) { pending.removeFirst() }
        }
        try {
            val frames = ArrayList<ByteArray>()
            for (packet in batch) {
                if (synchronized(lock) { closed }) return
                frames.addAll(encode(packet))
            }
            val bytes = batch.sumOf { it.retainedBytes }
            dispatch(deliveryExecutor) { deliverBatch(frames, 0, batch.size, bytes) }
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun deliverBatch(frames: List<ByteArray>, offset: Int, packetCount: Int, packetBytes: Long) {
        try {
            var next = offset
            val continueEncoding = synchronized(lock) {
                if (closed) return
                var sentBytes = 0
                while (next < frames.size && next - offset < DELIVERY_BATCH_FRAMES) {
                    val frame = frames[next]
                    if (next > offset && sentBytes + frame.size > DELIVERY_BATCH_BYTES) break
                    send(frame)
                    next++
                    sentBytes += frame.size
                    if (closed) return
                }
                if (next < frames.size) {
                    false
                } else {
                    retainedPackets -= packetCount
                    retainedBytes -= packetBytes
                    running = pending.isNotEmpty()
                    running
                }
            }
            if (next < frames.size) {
                dispatch(deliveryExecutor) { deliverBatch(frames, next, packetCount, packetBytes) }
            } else if (continueEncoding) {
                dispatch(encoderExecutor, ::encodeBatch)
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun dispatch(executor: Executor, task: () -> Unit) {
        if (synchronized(lock) { closed }) return
        try {
            executor.execute(task)
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun fail(error: Exception) {
        synchronized(lock) {
            if (closed) return
            close()
        }
        onFailure(error)
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending.clear()
            retainedBytes = 0
            retainedPackets = 0
            running = false
        }
    }

    companion object {
        internal const val MAX_PENDING_PACKETS = 2048
        internal const val MAX_PENDING_BYTES = 16L * 1024 * 1024
        private const val ENCODE_BATCH_PACKETS = 128
        internal const val DELIVERY_BATCH_FRAMES = 128
        internal const val DELIVERY_BATCH_BYTES = 256 * 1024
    }
}
