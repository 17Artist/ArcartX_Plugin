/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import priv.seventeen.artist.arcartx.network.message.DecodeType
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class OutgoingInitializationTest {


    @Test
    fun `a full ordinary queue does not reject the initialization cursor or starve it`() {
        val h = Harness()
        repeat(OutgoingPacketQueue.MAX_PENDING_PACKETS) { h.queue.submit(packet(it)) }
        var calls = 0
        assertTrue(h.queue.submitInitialization(listOf<() -> Unit>({ calls++; h.queue.submit(packet(9000)) }).iterator()))
        h.encoder.runNext()
        assertEquals(0, calls)
        h.drain()
        assertEquals((0 until OutgoingPacketQueue.MAX_PENDING_PACKETS).toList() + 9000, h.sent)
        assertEquals(1, calls)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun `later packets cannot consume the capacity reserved for the active initialization`() {
        val h = Harness()
        h.queue.submitInitialization(listOf<() -> Unit>({ repeat(5000) { h.queue.submit(packet(it)) } }).iterator())
        repeat(OutgoingPacketQueue.MAX_PENDING_PACKETS) { h.queue.submit(packet(10000 + it)) }
        h.drain()
        assertEquals((0 until 5000).toList() + (10000 until 10000 + OutgoingPacketQueue.MAX_PENDING_PACKETS), h.sent)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun `blocked delivery stops production and resumes without replaying the callback`() {
        val h = Harness()
        val calls = mutableListOf<Int>()
        h.queue.submitInitialization(listOf<() -> Unit>(
            { calls += 1; repeat(5000) { h.queue.submit(packet(it)) } },
            { calls += 2; h.queue.submit(packet(5000)) }
        ).iterator())
        h.encoder.runNext()
        assertTrue(calls.isEmpty(), "编码线程不能执行 Bukkit 回调")
        h.delivery.runNext()
        assertEquals(listOf(1), calls)
        h.encoder.runNext()
        assertTrue(h.encoder.tasks.isEmpty())
        assertEquals(1, h.delivery.tasks.size)
        assertTrue(h.sent.isEmpty())
        assertEquals(listOf(1), calls, "延迟发送期间不能继续制造数据")
        h.drain()
        assertEquals(listOf(1, 2), calls)
        assertEquals((0..5000).toList(), h.sent)
    }

    @Test
    fun `empty steps also yield instead of executing the whole entity list in one tick`() {
        val h = Harness()
        var calls = 0
        h.queue.submitInitialization(List<() -> Unit>(1000) { { calls++ } }.iterator())
        h.encoder.runNext()
        h.delivery.runNext()
        assertTrue(calls in 1..OutgoingPacketQueue.INITIALIZATION_BATCH_STEPS)
        h.drain()
        assertEquals(1000, calls)
        assertTrue(h.sent.isEmpty())
    }

    @Test
    fun `snapshot byte budget pauses before the next callback and preserves fragmented order`() {
        val h = Harness { packet -> List(if (packet.id == 1) 300 else 1) { frame(packet.id) } }
        var calls = 0
        h.queue.submitInitialization(listOf<() -> Unit>(
            { calls++; h.queue.submit(packet(1).copy(json = "x".repeat(OutgoingPacketQueue.DELIVERY_BATCH_BYTES))) },
            { calls++; h.queue.submit(packet(2)) }
        ).iterator())
        h.queue.submit(packet(3))
        h.encoder.runNext()
        h.delivery.runNext()
        assertEquals(1, calls)
        h.encoder.runNext()
        h.delivery.runNext()
        assertEquals(OutgoingPacketQueue.DELIVERY_BATCH_FRAMES, h.sent.size)
        assertEquals(1, calls)
        h.drain()
        assertEquals(List(300) { 1 } + listOf(2, 3), h.sent)
        assertEquals(2, calls)
    }

    @Test
    fun `concurrent submissions cannot enter another threads initialization capture`() {
        val h = Harness()
        val other = Executors.newSingleThreadExecutor()
        try {
            h.queue.submitInitialization(listOf<() -> Unit>({
                assertTrue(h.queue.isPreparingInitialization)
                h.queue.submit(packet(1))
                other.submit {
                    assertFalse(h.queue.isPreparingInitialization)
                    h.queue.submit(packet(9))
                }.get(5, TimeUnit.SECONDS)
                h.queue.submit(packet(2))
            }, { h.queue.submit(packet(3)) }).iterator())
            h.drain()
            assertEquals(listOf(1, 2, 3, 9), h.sent)
            assertFalse(h.queue.isPreparingInitialization)
        } finally {
            other.shutdownNow()
        }
    }

    @Test
    fun `capture is also isolated between players on the same delivery thread`() {
        val first = Harness()
        val second = Harness()
        first.queue.submitInitialization(listOf<() -> Unit>({
            assertTrue(first.queue.isPreparingInitialization)
            assertFalse(second.queue.isPreparingInitialization)
            second.queue.submit(packet(2))
            first.queue.submit(packet(1))
        }).iterator())
        first.drain()
        second.drain()
        assertEquals(listOf(1), first.sent)
        assertEquals(listOf(2), second.sent)
    }

    @Test
    fun `duplicate initialization is coalesced until its final fragments have drained`() {
        val h = Harness()
        var end = 0
        fun steps() = listOf<() -> Unit>({ end++; h.queue.submit(packet(end)) }).iterator()
        assertTrue(h.queue.submitInitialization(steps()))
        assertFalse(h.queue.submitInitialization(steps()))
        h.encoder.runNext()
        h.delivery.runNext()
        h.encoder.runNext()
        assertFalse(h.queue.submitInitialization(steps()))
        h.drain()
        assertEquals(1, end)
        assertTrue(h.queue.submitInitialization(steps()))
        h.drain()
        assertEquals(2, end)
        assertEquals(listOf(1, 2), h.sent)
    }

    @Test
    fun `disconnect before production or during delivery never runs the unfinished End`() {
        for (phase in 0..2) {
            val h = Harness()
            var end = 0
            h.queue.submitInitialization(listOf<() -> Unit>(
                { repeat(5000) { h.queue.submit(packet(it)) } }
            ).iterator()) { end++ }
            h.encoder.runNext()
            if (phase > 0) {
                h.delivery.runNext()
                h.encoder.runNext()
                h.delivery.runNext()
            }
            if (phase == 2) {
                // 前序分片恰好发完、End 尚未执行时离线，同样不能产生旧会话的完成事件。
                while (h.sent.size < 5000) {
                    if (h.encoder.tasks.isNotEmpty()) h.encoder.runNext()
                    if (h.delivery.tasks.isNotEmpty()) h.delivery.runNext()
                }
            }
            val before = h.sent.toList()
            h.queue.close()
            h.drain()
            assertEquals(before, h.sent)
            assertEquals(0, end)
            assertFalse(h.queue.submitInitialization(emptyList<() -> Unit>().iterator()))
            assertFalse(h.queue.isPreparingInitialization)
            val reconnected = Harness()
            reconnected.queue.submitInitialization(listOf<() -> Unit>({ reconnected.queue.submit(packet(9)) }).iterator())
            reconnected.drain()
            assertEquals(listOf(9), reconnected.sent)
        }
    }

    @Test
    fun `callback failure stops the cursor clears capture and never signals End`() {
        val h = Harness()
        var end = 0
        h.queue.submitInitialization(listOf<() -> Unit>(
            { throw IllegalStateException("failed slot") }
        ).iterator()) { end++ }
        h.drain()
        assertEquals(1, h.errors.size)
        assertEquals(0, end)
        assertFalse(h.queue.isPreparingInitialization)
        assertFalse(h.queue.submit(packet(1)))
    }

    @Test
    fun `even an event bus swallowing a hard limit exception cannot resume an incomplete initialization`() {
        val h = Harness()
        var end = 0
        h.queue.submitInitialization(listOf<() -> Unit>({
            try {
                repeat(OutgoingPacketQueue.MAX_INITIALIZATION_PACKETS + 1) { h.queue.submit(packet(it)) }
            } catch (_: RejectedExecutionException) {
                // 模拟 Bukkit 对监听器异常的隔离。
            }
        }).iterator()) { end++ }
        h.drain()
        assertEquals(1, h.errors.size)
        assertTrue(h.errors.single().message!!.contains("packets="))
        assertEquals(0, end)
        assertTrue(h.sent.isEmpty())
    }

    @Test
    fun `single callback byte limit remains bounded`() {
        val h = Harness()
        val large = packet(1).copy(json = "x".repeat((OutgoingPacketQueue.MAX_INITIALIZATION_BYTES / 2).toInt()))
        h.queue.submitInitialization(listOf<() -> Unit>({
            h.queue.submit(large)
            h.queue.submit(packet(2))
        }).iterator())
        h.drain()
        assertEquals(1, h.errors.size)
        assertTrue(h.errors.single().message!!.contains("incomingBytes="))
        assertTrue(h.sent.isEmpty())
    }

    @Test
    fun `End waits for every fragment and is skipped if encoding or delivery fails`() {
        for (failure in listOf("none", "encode", "deliver")) {
            val encoder = ManualExecutor()
            val delivery = ManualExecutor()
            val errors = mutableListOf<Exception>()
            var sent = 0
            var end = 0
            val queue = OutgoingPacketQueue(encoder, delivery, {
                check(failure != "deliver") { "delivery failed" }
                sent++
            }, errors::add, {
                check(failure != "encode") { "encoding failed" }
                List(300) { frame(it) }
            })
            queue.submitInitialization(listOf<() -> Unit>({ queue.submit(packet(1)) }).iterator()) {
                assertEquals(300, sent)
                end++
            }
            while (encoder.tasks.isNotEmpty() || delivery.tasks.isNotEmpty()) {
                if (encoder.tasks.isNotEmpty()) encoder.runNext()
                if (delivery.tasks.isNotEmpty()) delivery.runNext()
            }
            assertEquals(if (failure == "none") 1 else 0, end)
            assertEquals(if (failure == "none") 0 else 1, errors.size)
        }
    }

    private class Harness(encode: (OutgoingPacket) -> List<ByteArray> = { listOf(frame(it.id)) }) {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val sent = mutableListOf<Int>()
        val errors = mutableListOf<Exception>()
        val backlogs = mutableListOf<OutgoingPacketQueue.Backlog>()
        val queue = OutgoingPacketQueue(encoder, delivery, { sent += ByteBuffer.wrap(it).int }, errors::add, encode, backlogs::add)

        fun drain() {
            var turns = 0
            while (encoder.tasks.isNotEmpty() || delivery.tasks.isNotEmpty()) {
                check(turns++ < 100_000) { "queue did not finish" }
                if (encoder.tasks.isNotEmpty()) encoder.runNext()
                if (delivery.tasks.isNotEmpty()) {
                    val before = sent.size
                    delivery.runNext()
                    assertTrue(sent.size - before <= OutgoingPacketQueue.DELIVERY_BATCH_FRAMES)
                }
            }
        }
    }

    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() = tasks.removeFirst().run()
    }

    companion object {
        private fun packet(id: Int) = OutgoingPacket(id, 1, DecodeType.NORMAL, "{}")
        private fun frame(id: Int) = ByteBuffer.allocate(4).putInt(id).array()
    }
}
