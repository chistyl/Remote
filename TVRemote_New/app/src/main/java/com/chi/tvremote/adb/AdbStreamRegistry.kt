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

    /**
     * Routes protocol packets from the single socket reader. For A_OKAY,
     * A_WRTE and A_CLSE, ADB places the host's local stream ID in arg1.
     */
    fun dispatchTransportPacket(
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray
    ): Boolean {
        if (arg1 <= 0) return false
        if (command != A_OKAY && command != A_WRTE && command != A_CLSE) return false
        return dispatch(arg1, StreamPacket(command, arg0, payload))
    }

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

    private companion object {
        const val A_OKAY = 0x59414b4f
        const val A_WRTE = 0x45545257
        const val A_CLSE = 0x45534c43
    }
}
