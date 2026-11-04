package com.chi.tvremote.adb

import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the single inbound ADB socket read loop after the transport handshake.
 * It validates packet framing and routes stream packets without waiting for
 * any individual logical stream consumer.
 */
internal class AdbPacketReaderPump(
    input: InputStream,
    private val registry: AdbStreamRegistry,
    private val onFailure: (IOException) -> Unit,
    private val maxPayload: () -> Int
) : Closeable {
    private val stream = DataInputStream(input)
    private val running = AtomicBoolean(false)
    @Volatile private var reader: Thread? = null

    fun start() {
        check(running.compareAndSet(false, true)) { "ADB reader pump already started" }
        reader = Thread({ readLoop() }, "tvremote-adb-reader").apply {
            isDaemon = true
            start()
        }
    }

    private fun readLoop() {
        try {
            while (running.get()) {
                val header = ByteArray(HEADER_SIZE)
                stream.readFully(header)
                val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val command = fields.getInt(0)
                val arg0 = fields.getInt(4)
                val arg1 = fields.getInt(8)
                val length = fields.getInt(12)
                val limit = maxPayload().coerceIn(0, LOCAL_MAX_PAYLOAD)
                if (length < 0 || length > limit) {
                    throw IOException("ADB inbound payload exceeds negotiated limit: $length/$limit")
                }
                val payload = ByteArray(length)
                stream.readFully(payload)
                AdbPacketCodec.decode(header, payload, limit)
                registry.dispatchTransportPacket(command, arg0, arg1, payload)
            }
        } catch (e: IOException) {
            if (running.get()) onFailure(e)
        } catch (e: Exception) {
            if (running.get()) onFailure(IOException("ADB reader pump failed", e))
        } finally {
            running.set(false)
        }
    }

    override fun close() {
        running.set(false)
        try { stream.close() } catch (_: IOException) { }
        reader?.interrupt()
    }

    companion object {
        private const val HEADER_SIZE = 24
        private const val LOCAL_MAX_PAYLOAD = 1024 * 1024
    }
}
