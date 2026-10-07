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

/** 一个连接只允许一个编码/投递批次在途；初始化游标在 FIFO 中占位，后来的包不能越过它。 */
internal class OutgoingPacketQueue(
    private val encoderExecutor: Executor,
    private val deliveryExecutor: Executor,
    private val send: (ByteArray) -> Unit,
    private val onFailure: (Exception) -> Unit,
    private val encode: (OutgoingPacket) -> List<ByteArray> = OutgoingPacketEncoder::encode,
    private val onBacklog: (Backlog) -> Unit = {}
) : AutoCloseable {
    private interface Entry
    private class PacketEntry(val packet: OutgoingPacket) : Entry
    private class Initialization(var steps: Iterator<() -> Unit>?, var completion: (() -> Unit)?) : Entry {
        val packets = ArrayDeque<OutgoingPacket>()
        var retainedBytes = 0L
        var retainedPackets = 0
        var failure: Exception? = null
    }

    internal data class Backlog(val packets: Int, val bytes: Long, val initializing: Boolean)
    private data class Batch(val packets: List<OutgoingPacket>, val initialization: Initialization?)

    private val lock = Any()
    private val pending = ArrayDeque<Entry>()
    private val production = ThreadLocal<Initialization>()
    private var initialization: Initialization? = null
    private var retainedBytes = 0L
    private var retainedPackets = 0
    private var running = false
    private var closed = false
    private var backlogReported = false

    /** 仅当前初始化回调的调用线程为 true；不能把其他玩家或其他线程的包插到游标前。 */
    val isPreparingInitialization: Boolean get() = production.get() != null

    fun submit(packet: OutgoingPacket): Boolean {
        val start = synchronized(lock) {
            if (closed) return false
            production.get()?.let { source ->
                if (source.retainedPackets >= MAX_INITIALIZATION_PACKETS ||
                    source.retainedBytes + packet.retainedBytes > MAX_INITIALIZATION_BYTES) {
                    val error = RejectedExecutionException("ArcartX initialization callback exceeded its packet budget: " +
                        "packets=${source.retainedPackets}, bytes=${source.retainedBytes}, incomingBytes=${packet.retainedBytes}")
                    // Bukkit 会捕获监听器异常；另外记录失败，防止回调返回后继续触发 End。
                    source.failure = error
                    throw error
                }
                source.packets.addLast(packet)
                source.retainedPackets++
                source.retainedBytes += packet.retainedBytes
                return true
            }
            if (retainedPackets >= MAX_PENDING_PACKETS || retainedBytes + packet.retainedBytes > MAX_PENDING_BYTES) {
                throw RejectedExecutionException("ArcartX outgoing packet queue is full: " +
                    "packets=$retainedPackets, bytes=$retainedBytes, incomingBytes=${packet.retainedBytes}, " +
                    "initializing=${initialization != null}")
            }
            pending.addLast(PacketEntry(packet))
            retainedBytes += packet.retainedBytes
            retainedPackets++
            if (running) false else {
                running = true
                true
            }
        }
        reportBacklog()
        if (start) dispatch(encoderExecutor, ::encodeBatch)
        return true
    }

    /** steps 只在投递线程推进。重复初始化在前一次完成前合并，不堆积额外游标。 */
    fun submitInitialization(steps: Iterator<() -> Unit>, completion: (() -> Unit)? = null): Boolean {
        val start = synchronized(lock) {
            if (closed || initialization != null) return false
            val source = Initialization(steps, completion)
            initialization = source
            pending.addLast(source)
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
            val source = pending.peekFirst() as? Initialization
            if (source != null) {
                if (source.packets.isEmpty()) null else
                    Batch(List(minOf(source.packets.size, ENCODE_BATCH_PACKETS)) { source.packets.removeFirst() }, source)
            } else {
                val packets = ArrayList<OutgoingPacket>()
                while (packets.size < ENCODE_BATCH_PACKETS) {
                    val entry = pending.peekFirst() as? PacketEntry ?: break
                    pending.removeFirst()
                    packets.add(entry.packet)
                }
                Batch(packets, null)
            }
        }
        if (batch == null) {
            dispatch(deliveryExecutor, ::produceInitialization)
            return
        }
        try {
            val frames = ArrayList<ByteArray>()
            for (packet in batch.packets) {
                if (synchronized(lock) { closed }) return
                frames.addAll(encode(packet))
            }
            val bytes = batch.packets.sumOf { it.retainedBytes }
            dispatch(deliveryExecutor) { deliverBatch(frames, 0, batch.packets.size, bytes, batch.initialization) }
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun deliverBatch(
        frames: List<ByteArray>, offset: Int, packetCount: Int, packetBytes: Long, source: Initialization?
    ) {
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
                    if (source == null) {
                        retainedPackets -= packetCount
                        retainedBytes -= packetBytes
                    } else {
                        source.retainedPackets -= packetCount
                        source.retainedBytes -= packetBytes
                    }
                    finishBatch()
                }
            }
            if (next < frames.size) {
                dispatch(deliveryExecutor) { deliverBatch(frames, next, packetCount, packetBytes, source) }
            } else if (continueEncoding) {
                val needsProduction = synchronized(lock) {
                    (pending.peekFirst() as? Initialization)?.packets?.isEmpty() == true
                }
                // 当前已在玩家投递线程，直接准备下一批；压缩仍必须交给编码线程。
                if (needsProduction) produceInitialization() else dispatch(encoderExecutor, ::encodeBatch)
            }
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun produceInitialization() {
        val source = synchronized(lock) {
            if (closed) return
            pending.peekFirst() as? Initialization ?: return
        }
        try {
            production.set(source)
            val completion = synchronized(lock) {
                if (closed) return
                if (source.steps == null) source.completion.also { source.completion = null } else null
            }
            // 完成事件必须等所有实体、槽位包及其分片投递完，再执行一次。
            completion?.invoke()
            source.failure?.let { throw it }
            val started = System.nanoTime()
            var steps = 0
            while (steps < INITIALIZATION_BATCH_STEPS) {
                val iterator = synchronized(lock) {
                    if (closed) return
                    if (source.retainedPackets >= ENCODE_BATCH_PACKETS || source.retainedBytes >= DELIVERY_BATCH_BYTES) null
                    else source.steps
                } ?: break
                if (!iterator.hasNext()) {
                    source.steps = null
                    break
                }
                iterator.next().invoke()
                source.failure?.let { throw it }
                steps++
                if (System.nanoTime() - started >= INITIALIZATION_BATCH_NANOS) break
            }
        } catch (error: Exception) {
            fail(error)
            return
        } finally {
            production.remove()
        }
        reportBacklog()
        val more = synchronized(lock) {
            if (closed) return
            finishBatch()
        }
        if (more) dispatch(encoderExecutor, ::encodeBatch)
    }

    /** 调用方持有 lock；只有游标和它产生的所有分片都完成后才放行后续包。 */
    private fun finishBatch(): Boolean {
        val source = pending.peekFirst() as? Initialization
        if (source != null && source.steps == null && source.completion == null && source.retainedPackets == 0) {
            pending.removeFirst()
            initialization = null
        }
        running = pending.isNotEmpty()
        if (!running) backlogReported = false
        return running
    }

    private fun reportBacklog() {
        val backlog = synchronized(lock) {
            if (closed || backlogReported) return
            val count = retainedPackets + (initialization?.retainedPackets ?: 0)
            val bytes = retainedBytes + (initialization?.retainedBytes ?: 0)
            if (count < MAX_PENDING_PACKETS * 3 / 4 && bytes < MAX_PENDING_BYTES * 3 / 4) return
            backlogReported = true
            Backlog(count, bytes, initialization != null)
        }
        onBacklog(backlog)
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
            initialization?.apply {
                steps = null
                completion = null
                packets.clear()
            }
            initialization = null
            pending.clear()
            retainedBytes = 0
            retainedPackets = 0
            running = false
        }
    }

    companion object {
        internal const val MAX_PENDING_PACKETS = 2048
        internal const val MAX_PENDING_BYTES = 16L * 1024 * 1024
        internal const val MAX_INITIALIZATION_PACKETS = MAX_PENDING_PACKETS * 8
        internal const val MAX_INITIALIZATION_BYTES = MAX_PENDING_BYTES
        internal const val INITIALIZATION_BATCH_STEPS = 32
        private const val INITIALIZATION_BATCH_NANOS = 2_000_000L
        private const val ENCODE_BATCH_PACKETS = 128
        internal const val DELIVERY_BATCH_FRAMES = 128
        internal const val DELIVERY_BATCH_BYTES = 256 * 1024
    }
}
