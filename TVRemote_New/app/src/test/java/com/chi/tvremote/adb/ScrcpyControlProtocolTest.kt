package com.chi.tvremote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScrcpyControlProtocolTest {
    @Test fun uhidCreateUsesScrcpyFieldOrderAndBigEndianLengths() {
        val actual = ScrcpyControlProtocol.uhidCreate(
            id = 1, vendorId = 0x1234, productId = 0x5678,
            name = "Mouse", reportDescriptor = byteArrayOf(0x05, 0x01, 0x09)
        )
        assertArrayEquals(
            byteArrayOf(12, 0, 1, 0x12, 0x34, 0x56, 0x78, 5,
                77, 111, 117, 115, 101, 0, 3, 5, 1, 9),
            actual
        )
    }

    @Test fun uhidInputAndDestroyEncodeUnsignedIdsAndPayloadLength() {
        assertArrayEquals(
            byteArrayOf(13, 0, 2, 0, 3, 1, 2, 3),
            ScrcpyControlProtocol.uhidInput(2, byteArrayOf(1, 2, 3))
        )
        assertArrayEquals(
            byteArrayOf(14, 0, 2),
            ScrcpyControlProtocol.uhidDestroy(2)
        )
    }

    @Test fun touchMessageEncodesCoordinatesAndDisplaySize() {
        val bytes = ScrcpyControlProtocol.injectTouch(
            action = ScrcpyControlProtocol.ACTION_HOVER_MOVE,
            pointerId = -2L, x = 320, y = 240,
            screenWidth = 1920, screenHeight = 1080, pressure = 0.5f
        )
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(2, buffer.get().toInt())
        assertEquals(7, buffer.get().toInt())
        assertEquals(-2L, buffer.long)
        assertEquals(320, buffer.int)
        assertEquals(240, buffer.int)
        assertEquals(1920, buffer.short.toInt() and 0xffff)
        assertEquals(1080, buffer.short.toInt() and 0xffff)
        assertEquals(32767, buffer.short.toInt() and 0xffff)
    }

    @Test fun scrollMessageEncodesSignedFixedPointValues() {
        val bytes = ScrcpyControlProtocol.injectScroll(
            x = 10, y = 20, screenWidth = 1280, screenHeight = 720,
            horizontal = -1f, vertical = 2f
        )
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(3, buffer.get().toInt())
        buffer.int
        buffer.int
        buffer.short
        buffer.short
        assertEquals(-2048, buffer.short.toInt())
        assertEquals(4096, buffer.short.toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedUhidName() {
        ScrcpyControlProtocol.uhidCreate(1, 0, 0, "x".repeat(256), byteArrayOf(1))
    }
}
