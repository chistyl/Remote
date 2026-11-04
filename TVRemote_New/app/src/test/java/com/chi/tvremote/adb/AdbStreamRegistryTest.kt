package com.chi.tvremote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AdbStreamRegistryTest {
    @Test
    fun routesPacketsToCorrectIndependentStreams() {
        val registry = AdbStreamRegistry()
        val first = registry.open()
        val second = registry.open()
        assertTrue(registry.dispatch(second.localId, AdbStreamRegistry.StreamPacket(3, 22, byteArrayOf(2))))
        assertTrue(registry.dispatch(first.localId, AdbStreamRegistry.StreamPacket(3, 11, byteArrayOf(1))))

        val firstPacket = first.take(100)
        val secondPacket = second.take(100)
        assertEquals(11, firstPacket.remoteId)
        assertArrayEquals(byteArrayOf(1), firstPacket.payload)
        assertEquals(22, secondPacket.remoteId)
        assertArrayEquals(byteArrayOf(2), secondPacket.payload)
        assertEquals(2, registry.activeCount())
    }

    @Test
    fun transportPacketsRouteUsingAdbArg1LocalId() {
        val registry = AdbStreamRegistry()
        val first = registry.open()
        val second = registry.open()

        val okay = 0x59414b4f
        val write = 0x45545257
        assertTrue(registry.dispatchTransportPacket(okay, 41, first.localId, byteArrayOf()))
        assertTrue(registry.dispatchTransportPacket(write, 42, second.localId, byteArrayOf(9)))
        assertEquals(41, first.take(100).remoteId)
        val secondPacket = second.take(100)
        assertEquals(42, secondPacket.remoteId)
        assertArrayEquals(byteArrayOf(9), secondPacket.payload)
        assertFalse(registry.dispatchTransportPacket(write, 1, 9999, byteArrayOf()))
        assertFalse(registry.dispatchTransportPacket(0x12345678, 1, first.localId, byteArrayOf()))
    }

    @Test
    fun duplexStreamRoutesInboundDataAndWriteAcknowledgementsSeparately() {
        val registry = AdbStreamRegistry()
        val stream = registry.open()
        stream.enableDuplex()

        val okay = 0x59414b4f
        val write = 0x45545257
        assertTrue(registry.dispatchTransportPacket(write, 77, stream.localId, byteArrayOf(1, 2)))
        assertTrue(registry.dispatchTransportPacket(okay, 77, stream.localId, byteArrayOf()))

        val data = stream.takeDuplexData(100)
        val ack = stream.takeDuplexAck(100)
        assertEquals(write, data.command)
        assertArrayEquals(byteArrayOf(1, 2), data.payload)
        assertEquals(okay, ack.command)
        assertEquals(77, ack.remoteId)
    }

    @Test
    fun closingStreamWakesBlockedConsumerImmediately() {
        val registry = AdbStreamRegistry()
        val stream = registry.open()
        val started = CountDownLatch(1)
        val result = AtomicReference<Throwable?>(null)
        val worker = Thread {
            started.countDown()
            try {
                stream.take(30000)
            } catch (error: Throwable) {
                result.set(error)
            }
        }
        worker.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        registry.close(stream.localId)
        worker.join(1000)
        assertFalse(worker.isAlive)
        assertTrue(result.get() is IOException)
    }

    @Test
    fun closeRemovesStreamAndRejectsFurtherPackets() {
        val registry = AdbStreamRegistry()
        val stream = registry.open()
        assertTrue(registry.close(stream.localId))
        assertFalse(registry.dispatch(stream.localId, AdbStreamRegistry.StreamPacket(5, 0)))
        assertEquals(0, registry.activeCount())
        try {
            stream.take(1)
            throw AssertionError("closed stream should fail")
        } catch (_: IOException) {
            // expected
        }
    }
}
