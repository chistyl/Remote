package com.chi.tvremote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbPacketCodecTest {
    @Test
    fun roundTripsLittleEndianHeaderAndPayload() {
        val payload = byteArrayOf(0, 1, 0x7f, 0x80.toByte(), 0xff.toByte())
        val encoded = AdbPacketCodec.encode(0x4e45504f, 7, 0, payload)
        val header = encoded.copyOfRange(0, 24)
        val body = encoded.copyOfRange(24, encoded.size)
        val packet = AdbPacketCodec.decode(header, body)

        val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x4e45504f, fields.int)
        assertEquals(7, fields.int)
        assertEquals(0, fields.int)
        assertEquals(payload.size, fields.int)
        assertArrayEquals(payload, packet.payload)
        assertEquals(7, packet.arg0)
    }

    @Test
    fun rejectsInvalidCommandMagic() {
        val encoded = AdbPacketCodec.encode(0x4e45504f, 1, 0, byteArrayOf())
        val header = encoded.copyOfRange(0, 24)
        header[20] = (header[20].toInt() xor 1).toByte()
        try {
            AdbPacketCodec.decode(header, byteArrayOf())
            fail("Expected invalid magic to be rejected")
        } catch (_: IOException) { }
    }

    @Test
    fun rejectsPayloadChecksumMismatch() {
        val encoded = AdbPacketCodec.encode(0x4e45504f, 1, 0, byteArrayOf(1, 2, 3))
        val header = encoded.copyOfRange(0, 24)
        try {
            AdbPacketCodec.decode(header, byteArrayOf(1, 2, 4))
            fail("Expected checksum mismatch to be rejected")
        } catch (_: IOException) { }
    }

    @Test
    fun rejectsHeaderPayloadLengthMismatch() {
        val encoded = AdbPacketCodec.encode(0x4e45504f, 1, 0, byteArrayOf(1, 2))
        val header = encoded.copyOfRange(0, 24)
        try {
            AdbPacketCodec.decode(header, byteArrayOf(1))
            fail("Expected payload length mismatch to be rejected")
        } catch (_: IOException) { }
    }

    @Test
    fun rejectsPayloadAboveNegotiatedPeerLimit() {
        val payload = ByteArray(32) { it.toByte() }
        val encoded = AdbPacketCodec.encode(0x4e45504f, 1, 0, payload)
        val header = encoded.copyOfRange(0, 24)
        try {
            AdbPacketCodec.decode(header, payload, maxPayload = 16)
            fail("Expected payload above negotiated limit to be rejected")
        } catch (_: IOException) { }
    }
}
