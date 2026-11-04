package com.chi.tvremote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
