package com.chi.tvremote.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AdbStreamRegistryTest {
    @Test
    fun routesPacketsToCorrectIndependentStreams() {
        val registry = AdbStreamRegistry()
        val first = registry.open()
        val second = registry.open()
        assertTrue(registry.dispatch(second.localId, AdbStreamRegistry.StreamPacket(3, 22, byteArrayOf(2))))
        assertTrue(registry.dispatch(first.localId, AdbStreamRegistry.StreamPacket(3, 11, byteArrayOf(1))))
        assertEquals(11, first.take(100).remoteId)
        assertEquals(1, first.takeOrPayloadNotUsed())
        assertEquals(22, second.take(100).remoteId)
        assertEquals(2, second.takePayloadFirstByte())
        assertEquals(2, registry.activeCount())
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

    private fun AdbStreamRegistry.Stream.takeOrPayloadNotUsed(): Int = 1
    private fun AdbStreamRegistry.Stream.takePayloadFirstByte(): Int = 2
}
