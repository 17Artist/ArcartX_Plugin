/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.nms.AsteroidScheduler
import priv.seventeen.artist.arcartx.util.ByteArrayUtils
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 工作线程仅编码快照；插件消息通过玩家调度器批量投递。 */
internal object OutgoingPacketDispatcher {
    private val sessions = HashMap<UUID, Connection>()
    private val lock = Any()
    private var workers: ThreadPoolExecutor? = null

    fun start() {
        synchronized(lock) {
            if (workers != null) return
            workers = createWorkers()
        }
        Bukkit.getOnlinePlayers().forEach(::connect)
    }

    private fun createWorkers(): ThreadPoolExecutor {
        val threadNumber = AtomicInteger()
        val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
        return ThreadPoolExecutor(
            threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1024),
            { task ->
                Thread({
                    try {
                        task.run()
                    } finally {
                        ByteArrayUtils.releaseCompressionResources()
                    }
                }, "ArcartX-packet-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    fun connect(player: Player) {
        val previous = synchronized(lock) {
            val executor = workers ?: return
            val current = sessions[player.uniqueId]
            if (current?.player === player) return
            sessions.put(player.uniqueId, Connection(player, executor))
        }
        previous?.close()
    }

    fun connection(player: Player): Connection? = synchronized(lock) {
        sessions[player.uniqueId]?.takeIf { it.player === player }
    }

    fun disconnect(player: Player) {
        val connection = synchronized(lock) {
            val current = sessions[player.uniqueId] ?: return
            if (current.player !== player) return
            sessions.remove(player.uniqueId)
        }
        connection?.close()
    }

    fun stop() {
        val (executor, connections) = synchronized(lock) {
            val previous = workers to sessions.values.toList()
            workers = null
            sessions.clear()
            previous
        }
        connections.forEach(Connection::close)
        executor?.shutdownNow()
    }

    internal class Connection(val player: Player, workers: Executor) : AutoCloseable {
        private val queue = OutgoingPacketQueue(
            encoderExecutor = workers,
            deliveryExecutor = Executor(::scheduleDelivery),
            send = { frame -> player.sendPluginMessage(bukkitPlugin, NetworkManager.CHANNEL, frame) },
            onFailure = { error -> BlinkLog.error("向玩家 ${player.name} 发送数据失败", error) },
            onBacklog = { backlog ->
                BlinkLog.warn("玩家 ${player.name} 的待发送数据较多（${backlog.packets} 个包，" +
                    "约 ${backlog.bytes / 1024} KiB），正在分批发送。")
            }
        )

        fun submit(packet: OutgoingPacket) = queue.submit(packet)

        val isPreparingInitialization: Boolean get() = queue.isPreparingInitialization

        fun submitInitialization(steps: Iterator<() -> Unit>, completion: () -> Unit) =
            queue.submitInitialization(steps, completion)

        private fun scheduleDelivery(task: Runnable) {
            if (!bukkitPlugin.isEnabled) {
                close()
                return
            }
            val handle = AsteroidScheduler.runEntityTask(bukkitPlugin, player, Runnable {
                if (bukkitPlugin.isEnabled && player.isOnline) task.run() else close()
            })
            if (handle == null) close()
        }

        override fun close() = queue.close()
    }
}
