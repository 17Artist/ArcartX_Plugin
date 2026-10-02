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
import priv.seventeen.artist.arcartx.util.ByteArrayUtils
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OutgoingPacketLoadTest {
    @Test
    fun `concurrent producers cannot lose packets or reorder an individual producer`() {
        val workers = Executors.newFixedThreadPool(2)
        val delivery = Executors.newSingleThreadExecutor()
        val producers = Executors.newFixedThreadPool(4)
        val errors = ConcurrentLinkedQueue<Exception>()
        val received = mutableListOf<Int>()
        val done = CountDownLatch(2000)
        val queue = OutgoingPacketQueue(workers, delivery, {
            received += ByteBuffer.wrap(it).int
            done.countDown()
        }, errors::add, { listOf(ByteBuffer.allocate(4).putInt(it.id).array()) })
        try {
            val futures = (0 until 4).map { producer ->
                producers.submit {
                    repeat(500) { sequence ->
                        assertTrue(queue.submit(OutgoingPacket(producer * 1000 + sequence, 1, DecodeType.NORMAL, "{}")))
                    }
                }
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), errors.toString())
            assertEquals(2000, received.size)
            repeat(4) { producer ->
                assertEquals((0 until 500).map { producer * 1000 + it }, received.filter { it / 1000 == producer })
            }
        } finally {
            queue.close()
            producers.shutdownNow()
            workers.shutdownNow()
            delivery.shutdownNow()
        }
    }

    @Test
    fun `100 simulated connections move encoding cpu off the delivery thread`() {
        val ownerThread = Thread.currentThread()
        val cpu = ManagementFactory.getThreadMXBean()
        val errors = ConcurrentLinkedQueue<Exception>()
        val wrongThread = AtomicInteger()
        val deliveries = LinkedBlockingQueue<Runnable>()
        val delivered = AtomicInteger()
        val workers = Executors.newFixedThreadPool(2) { task ->
            Thread({
                try { task.run() } finally { ByteArrayUtils.releaseCompressionResources() }
            }, "packet-load-worker")
        }
        val payloads = List(50) { number ->
            val json = "{\"id\":$number,\"elements\":[" +
                (0 until if (number % 10 == 0) 160 else 16).joinToString(",") {
                    "{\"name\":\"slot_$it\",\"x\":$it,\"value\":\"装备_$number\"}"
                } + "]}"
            OutgoingPacket(number, 25, DecodeType.NORMAL, json)
        }
        val queues = List(100) {
            OutgoingPacketQueue(workers, { deliveries.add(it) }, {
                if (Thread.currentThread() !== ownerThread) wrongThread.incrementAndGet()
                delivered.incrementAndGet()
            }, errors::add, {
                if (Thread.currentThread() === ownerThread) wrongThread.incrementAndGet()
                OutgoingPacketEncoder.encode(it)
            })
        }
        try {
            repeat(10) { payloads.forEach(OutgoingPacketEncoder::encode) }
            val baselineStart = cpu.currentThreadCpuTime
            val baselineWallStart = System.nanoTime()
            var baselineFrames = 0
            repeat(100) { payloads.forEach { baselineFrames += OutgoingPacketEncoder.encode(it).size } }
            val baselineActive = System.nanoTime() - baselineWallStart
            val baselineCpu = cpu.currentThreadCpuTime - baselineStart

            val mainStart = cpu.currentThreadCpuTime
            val wallStart = System.nanoTime()
            queues.forEach { queue -> payloads.forEach { assertTrue(queue.submit(it)) } }
            var mainActive = System.nanoTime() - wallStart
            val deadline = wallStart + TimeUnit.SECONDS.toNanos(20)
            while (delivered.get() < baselineFrames && System.nanoTime() < deadline && errors.isEmpty()) {
                deliveries.poll(100, TimeUnit.MILLISECONDS)?.let { task ->
                    val activeStart = System.nanoTime()
                    task.run()
                    mainActive += System.nanoTime() - activeStart
                }
            }
            val mainCpu = cpu.currentThreadCpuTime - mainStart
            assertTrue(errors.isEmpty(), errors.toString())
            assertEquals(0, wrongThread.get())
            assertEquals(baselineFrames, delivered.get())
            println("PACKET_LOAD connections=100 packets=5000 frames=${delivered.get()} " +
                "inlineActiveMs=${baselineActive / 1_000_000.0} enqueueAndDeliveryActiveMs=${mainActive / 1_000_000.0} " +
                "inlineEncodingCpuMs=${baselineCpu / 1_000_000.0} queueAndDeliveryCpuMs=${mainCpu / 1_000_000.0} " +
                "pipelineWallMs=${(System.nanoTime() - wallStart) / 1_000_000.0}")
        } finally {
            queues.forEach(OutgoingPacketQueue::close)
            workers.shutdownNow()
            workers.awaitTermination(5, TimeUnit.SECONDS)
            ByteArrayUtils.releaseCompressionResources()
        }
    }
}
