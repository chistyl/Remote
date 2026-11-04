package com.chi.tvremote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AdbPacketReaderPumpTest {
    @Test
    fun singleReaderDispatchesInterleavedPacketsToSeparateStreams() {
        val input = PipedInputStream(8192)
        val output = PipedOutputStream(input)
        val registry = AdbStreamRegistry()
        val first = registry.open()
        val second = registry.open()
        val failure = AtomicReference<Throwable?>(null)
        val pump = AdbPacketReaderPump(input, registry, { failure.set(it) }, { 4096 })
        pump.start()
        try {
            output.write(AdbPacketCodec.encode(WRTE, 101, second.localId, byteArrayOf(2)))
            output.write(AdbPacketCodec.encode(WRTE, 100, first.localId, byteArrayOf(1)))
            output.flush()

            val firstPacket = first.take(1000)
            val secondPacket = second.take(1000)
            assertEquals(100, firstPacket.remoteId)
            assertArrayEquals(byteArrayOf(1), firstPacket.payload)
            assertEquals(101, secondPacket.remoteId)
            assertArrayEquals(byteArrayOf(2), secondPacket.payload)
            assertEquals(null, failure.get())
        } finally {
            pump.close()
            output.close()
        }
    }

    @Test
    fun invalidPayloadLengthFailsReaderPump() {
        val input = PipedInputStream(8192)
        val output = PipedOutputStream(input)
        val registry = AdbStreamRegistry()
        val failed = CountDownLatch(1)
        val pump = AdbPacketReaderPump(input, registry, { failed.countDown() }, { 64 })
        pump.start()
        try {
            // Valid ADB header with a payload length beyond the negotiated peer limit.
            val header = AdbPacketCodec.encode(WRTE, 1, 1, ByteArray(1)).copyOfRange(0, 24)
            java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(12, 65)
            output.write(header)
            output.flush()
            assertTrue(failed.await(1, TimeUnit.SECONDS))
        } finally {
            pump.close()
            output.close()
        }
    }

    companion object {
        private const val WRTE = 0x45545257
    }
}
