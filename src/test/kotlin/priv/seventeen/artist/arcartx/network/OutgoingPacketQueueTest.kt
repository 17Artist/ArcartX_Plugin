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
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class OutgoingPacketQueueTest {
    @Test
    fun `encoding never runs on submission or delivery and packets retain fifo order`() {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val encoded = mutableListOf<Int>()
        val sent = mutableListOf<Int>()
        val queue = queue(encoder, delivery, sent) { packet ->
            encoded += packet.id
            listOf(byteArrayOf(packet.id.toByte()), byteArrayOf((packet.id + 10).toByte()))
        }
        queue.submit(packet(1))
        queue.submit(packet(2))
        assertTrue(encoded.isEmpty())
        assertEquals(1, encoder.tasks.size)
        encoder.runNext()
        queue.submit(packet(3))
        assertEquals(listOf(1, 2), encoded)
        assertTrue(sent.isEmpty())
        assertTrue(encoder.tasks.isEmpty())
        delivery.runNext()
        assertEquals(listOf(1, 11, 2, 12), sent)
        assertEquals(listOf(1, 2), encoded)
        encoder.runNext()
        delivery.runNext()
        assertEquals(listOf(1, 11, 2, 12, 3, 13), sent)
        assertTrue(encoder.tasks.isEmpty())
        queue.close()
    }

    @Test
    fun `large batches yield between channel writes without interleaving the next packet`() {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val sent = mutableListOf<Int>()
        val queue = queue(encoder, delivery, sent) { packet ->
            List(if (packet.id == 1) 150 else 1) { ByteArray(16 * 1024) { packet.id.toByte() } }
        }
        queue.submit(packet(1))
        encoder.runNext()
        queue.submit(packet(2))
        while (delivery.tasks.isNotEmpty()) {
            val before = sent.size
            delivery.runNext()
            assertTrue(sent.size - before <= OutgoingPacketQueue.DELIVERY_BATCH_BYTES / (16 * 1024))
        }
        assertEquals(List(150) { 1 }, sent)
        encoder.runNext()
        delivery.runNext()
        assertEquals(2, sent.last())
        assertEquals(151, sent.size)
        queue.close()
    }

    @Test
    fun `tiny frames also obey the delivery count budget`() {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val sent = mutableListOf<Int>()
        val queue = queue(encoder, delivery, sent) { List(300) { byteArrayOf(1) } }
        queue.submit(packet(1))
        encoder.runNext()
        delivery.runNext()
        assertEquals(OutgoingPacketQueue.DELIVERY_BATCH_FRAMES, sent.size)
        while (delivery.tasks.isNotEmpty()) delivery.runNext()
        assertEquals(300, sent.size)
        queue.close()
    }

    @Test
    fun `disconnect discards prepared frames and the reconnected queue remains independent`() {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val sent = mutableListOf<Int>()
        val oldQueue = queue(encoder, delivery, sent)
        oldQueue.submit(packet(1))
        encoder.runNext()
        oldQueue.submit(packet(2))
        oldQueue.close()
        assertFalse(oldQueue.submit(packet(3)))
        val newQueue = queue(encoder, delivery, sent)
        newQueue.submit(packet(4))
        encoder.runNext()
        while (delivery.tasks.isNotEmpty()) delivery.runNext()
        assertEquals(listOf(4), sent)
        newQueue.close()
    }

    @Test
    fun `queue bytes remain bounded while awaiting delivery and capacity recovers afterwards`() {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val sent = mutableListOf<Int>()
        val queue = queue(encoder, delivery, sent)
        val large = packet(1).copy(json = "x".repeat((OutgoingPacketQueue.MAX_PENDING_BYTES / 2).toInt()))
        queue.submit(large)
        encoder.runNext()
        assertThrows(RejectedExecutionException::class.java) { queue.submit(packet(2)) }
        delivery.runNext()
        assertTrue(queue.submit(packet(3)))
        encoder.runNext()
        delivery.runNext()
        assertEquals(listOf(1, 3), sent)
        queue.close()
    }

    @Test
    fun `many small packets hit the count bound without spawning a task per packet`() {
        val encoder = ManualExecutor()
        val delivery = ManualExecutor()
        val queue = queue(encoder, delivery, mutableListOf())
        repeat(OutgoingPacketQueue.MAX_PENDING_PACKETS) { assertTrue(queue.submit(packet(it))) }
        assertThrows(RejectedExecutionException::class.java) { queue.submit(packet(9000)) }
        assertEquals(1, encoder.tasks.size)
        queue.close()
        encoder.runNext()
        assertTrue(delivery.tasks.isEmpty())
    }

    @Test
    fun `executor rejection never falls back to inline encoding`() {
        var encodes = 0
        val errors = mutableListOf<Exception>()
        val queue = OutgoingPacketQueue(
            Executor { throw RejectedExecutionException("stopped") }, ManualExecutor(), {}, errors::add,
            { encodes++; emptyList() }
        )
        queue.submit(packet(1))
        assertEquals(0, encodes)
        assertEquals(1, errors.size)
        assertFalse(queue.submit(packet(2)))
    }

    @Test
    fun `shutdown during encoding suppresses the late delivery`() {
        val encoder = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val deliveries = java.util.concurrent.atomic.AtomicInteger()
        val queue = OutgoingPacketQueue(encoder, { deliveries.incrementAndGet() }, {}, { throw AssertionError(it) }, {
            started.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            listOf(byteArrayOf(1))
        })
        try {
            queue.submit(packet(1))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            queue.close()
            finish.countDown()
            encoder.shutdown()
            assertTrue(encoder.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(0, deliveries.get())
        } finally {
            finish.countDown()
            queue.close()
            encoder.shutdownNow()
        }
    }

    @Test
    fun `encoding and delivery failures close the affected queue without repeated callbacks`() {
        for (failEncoding in listOf(true, false)) {
            val encoder = ManualExecutor()
            val delivery = ManualExecutor()
            val errors = mutableListOf<Exception>()
            val queue = OutgoingPacketQueue(encoder, delivery,
                { throw IllegalStateException("delivery") }, errors::add,
                { if (failEncoding) throw IllegalStateException("encoding") else listOf(byteArrayOf(1)) }
            )
            queue.submit(packet(1))
            queue.submit(packet(2))
            encoder.runNext()
            if (!failEncoding) delivery.runNext()
            assertEquals(1, errors.size)
            assertFalse(queue.submit(packet(3)))
        }
    }

    private fun packet(id: Int) = OutgoingPacket(id, 1, DecodeType.NORMAL, "{}")

    private fun queue(
        encoder: Executor,
        delivery: Executor,
        sent: MutableList<Int>,
        encode: (OutgoingPacket) -> List<ByteArray> = { listOf(byteArrayOf(it.id.toByte())) }
    ) = OutgoingPacketQueue(encoder, delivery, { sent += it[0].toInt() }, { throw AssertionError(it) }, encode)

    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() = tasks.removeFirst().run()
    }
}
