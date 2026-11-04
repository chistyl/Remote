package com.chi.tvremote.adb

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * ADB logical stream adapted to independent blocking input/output streams.
 * Each inbound WRTE is acknowledged only after its complete payload is consumed;
 * each outbound WRTE waits for its own A_OKAY before the next chunk is sent.
 */
internal class AdbDuplexChannel(
    private val localId: Int,
    private val remoteId: Int,
    private val maxPayload: Int,
    private val readPacket: (Long) -> AdbStreamRegistry.StreamPacket,
    private val readAck: (Long) -> AdbStreamRegistry.StreamPacket,
    private val writePacket: (Int, Int, Int, ByteArray) -> Unit,
    private val closeStream: (Int, Int) -> Unit
) : Closeable {
    @Volatile private var closed = false
    private var inbound = ByteArray(0)
    private var inboundOffset = 0
    private var peerClosed = false

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || length > buffer.size - offset) {
                throw IndexOutOfBoundsException()
            }
            if (length == 0) return 0
            while (inboundOffset >= inbound.size && !peerClosed) {
                ensureInbound()
            }
            if (peerClosed) return -1
            val count = minOf(length, inbound.size - inboundOffset)
            if (count == 0) return read(buffer, offset, length)
            System.arraycopy(inbound, inboundOffset, buffer, offset, count)
            inboundOffset += count
            if (inboundOffset == inbound.size) {
                writePacket(A_OKAY, localId, remoteId, ByteArray(0))
                inbound = ByteArray(0)
                inboundOffset = 0
            }
            return count
        }

        override fun close() = this@AdbDuplexChannel.close()
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || length > buffer.size - offset) {
                throw IndexOutOfBoundsException()
            }
            checkOpen()
            var sent = 0
            while (sent < length) {
                val count = minOf(maxPayload, length - sent)
                val payload = buffer.copyOfRange(offset + sent, offset + sent + count)
                writePacket(A_WRTE, localId, remoteId, payload)
                awaitWriteAck()
                sent += count
            }
        }

        override fun flush() = Unit
        override fun close() = this@AdbDuplexChannel.close()
    }

    private fun ensureInbound() {
        checkOpen()
        if (inboundOffset < inbound.size || peerClosed) return
        val packet = readPacket(DEFAULT_TIMEOUT_MS)
        if (packet.remoteId != remoteId) throw IOException("ADB localabstract 遠端串流 ID 不符")
        when (packet.command) {
            A_WRTE -> {
                inbound = packet.payload
                inboundOffset = 0
                if (inbound.isEmpty()) {
                    writePacket(A_OKAY, localId, remoteId, ByteArray(0))
                }
            }
            A_CLSE -> peerClosed = true
            else -> throw IOException("ADB localabstract 收到非預期資料封包")
        }
    }

    private fun awaitWriteAck() {
        val packet = readAck(DEFAULT_TIMEOUT_MS)
        if (packet.command != A_OKAY || packet.remoteId != remoteId) {
            throw IOException("ADB localabstract 傳送 ACK 不符")
        }
    }

    private fun checkOpen() {
        if (closed) throw IOException("ADB localabstract 通道已關閉")
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        closeStream(localId, remoteId)
    }

    private companion object {
        const val A_OKAY = 0x59414b4f
        const val A_WRTE = 0x45545257
        const val A_CLSE = 0x45534c43
        const val DEFAULT_TIMEOUT_MS = 15000L
    }
}
