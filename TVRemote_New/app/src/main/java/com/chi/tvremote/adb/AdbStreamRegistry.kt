package com.chi.tvremote.adb

import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Thread-safe routing table for independent ADB logical streams. */
internal class AdbStreamRegistry {
    internal data class StreamPacket(
        val command: Int,
        val remoteId: Int,
        val payload: ByteArray = ByteArray(0)
    )

    internal class Stream internal constructor(val localId: Int) {
        private val incoming = LinkedBlockingQueue<StreamPacket>()
        private val duplexData = LinkedBlockingQueue<StreamPacket>()
        private val duplexAcks = LinkedBlockingQueue<StreamPacket>()
        @Volatile private var closed = false
        @Volatile private var duplex = false

        /** Must be enabled before sending A_OPEN. */
        internal fun enableDuplex() {
            check(!closed)
            duplex = true
        }

        internal fun offer(packet: StreamPacket) {
            if (closed) return
            if (!duplex) {
                incoming.offer(packet)
                return
            }
            when (packet.command) {
                A_WRTE -> duplexData.offer(packet)
                A_OKAY -> duplexAcks.offer(packet)
                A_CLSE -> {
                    duplexData.offer(packet)
                    duplexAcks.offer(packet)
                    incoming.offer(packet)
                }
                else -> incoming.offer(packet)
            }
        }

        @Throws(IOException::class)
        fun take(timeoutMs: Long): StreamPacket = poll(incoming, timeoutMs)

        @Throws(IOException::class)
        fun takeDuplexData(timeoutMs: Long): StreamPacket = poll(duplexData, timeoutMs)

        @Throws(IOException::class)
        fun takeDuplexAck(timeoutMs: Long): StreamPacket = poll(duplexAcks, timeoutMs)

        private fun poll(queue: LinkedBlockingQueue<StreamPacket>, timeoutMs: Long): StreamPacket {
            if (closed) throw IOException("ADB 串流已關閉")
            val packet = try {
                queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("等待 ADB 串流封包遭中斷", e)
            } ?: throw IOException("等待 ADB 串流回應逾時")
            if (packet.command == CLOSED_COMMAND || closed) throw IOException("ADB 串流已關閉")
            return packet
        }

        internal fun markClosed() {
            closed = true
            val signal = StreamPacket(CLOSED_COMMAND, 0)
            incoming.clear()
            duplexData.clear()
            duplexAcks.clear()
            incoming.offer(signal)
            duplexData.offer(signal)
            duplexAcks.offer(signal)
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

    /** ADB uses arg1 for the host's local stream ID in stream packets. */
    fun dispatchTransportPacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray): Boolean {
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
        const val CLOSED_COMMAND = -1
        const val A_OKAY = 0x59414b4f
        const val A_WRTE = 0x45545257
        const val A_CLSE = 0x45534c43
    }
}
