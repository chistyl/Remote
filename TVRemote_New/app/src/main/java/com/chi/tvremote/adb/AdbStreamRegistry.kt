package com.chi.tvremote.adb

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Thread-safe routing table for independent ADB logical streams.
 * A single transport reader can dispatch incoming packets without blocking
 * unrelated streams while each consumer waits on its own queue.
 */
internal class AdbStreamRegistry {
    internal data class StreamPacket(
        val command: Int,
        val remoteId: Int,
        val payload: ByteArray = ByteArray(0)
    )

    internal class Stream internal constructor(val localId: Int) {
        private val incoming = LinkedBlockingQueue<StreamPacket>()
        @Volatile private var closed = false

        internal fun offer(packet: StreamPacket) {
            if (!closed) incoming.offer(packet)
        }

        @Throws(IOException::class)
        fun take(timeoutMs: Long): StreamPacket {
            if (closed) throw IOException("ADB 串流已關閉")
            val packet = try {
                incoming.poll(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("等待 ADB 串流封包遭中斷", e)
            } ?: throw IOException("等待 ADB 串流回應逾時")
            return packet
        }

        internal fun markClosed() {
            closed = true
            incoming.clear()
        }

        fun isClosed(): Boolean = closed
    }

    private val lock = Any()
    private val ids = AdbStreamIdAllocator()
    private val streams = HashMap<Int, Stream>()

    fun open(): Stream = synchronized(lock) {
        val id = ids.acquire()
        Stream(id).also { streams[id] = it }
    }

    fun dispatch(localId: Int, packet: StreamPacket): Boolean =
        synchronized(lock) { streams[localId] }?.let {
            it.offer(packet)
            true
        } ?: false

    fun close(localId: Int): Boolean {
        val stream = synchronized(lock) {
            val removed = streams.remove(localId) ?: return false
            ids.release(localId)
            removed
        }
        stream.markClosed()
        return true
    }

    fun activeCount(): Int = synchronized(lock) { streams.size }
}
