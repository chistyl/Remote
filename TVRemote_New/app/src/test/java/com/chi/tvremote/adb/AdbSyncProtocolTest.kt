package com.chi.tvremote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbSyncProtocolTest {
    @Test
    fun sendHeaderUsesLittleEndianLengthAndPathMode() {
        val bytes = AdbSyncProtocol.sendHeader("/data/local/tmp/scrcpy-server.jar")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x444e4553, buffer.int)
        val length = buffer.int
        val payload = ByteArray(length)
        buffer.get(payload)
        assertEquals("/data/local/tmp/scrcpy-server.jar,420", String(payload))
    }

    @Test
    fun dataChunkCarriesLengthAndPayload() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val bytes = AdbSyncProtocol.dataChunk(payload)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x41544144, buffer.int)
        assertEquals(payload.size, buffer.int)
        val actual = ByteArray(buffer.remaining())
        buffer.get(actual)
        assertArrayEquals(payload, actual)
    }

    @Test
    fun doneCarriesLittleEndianTimestamp() {
        val bytes = AdbSyncProtocol.done(1234)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x454e4f44, buffer.int)
        assertEquals(1234, buffer.int)
    }

    @Test
    fun parsesOkayAndFailResponses() {
        val okay = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x59414b4f).array()
        assertTrue(AdbSyncProtocol.parseResponse(okay).success)

        val message = "permission denied".toByteArray()
        val fail = ByteBuffer.allocate(8 + message.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x4c494146).putInt(message.size).put(message).array()
        val response = AdbSyncProtocol.parseResponse(fail)
        assertFalse(response.success)
        assertEquals("permission denied", response.message)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDataChunk() {
        AdbSyncProtocol.dataChunk(ByteArray(AdbSyncProtocol.MAX_DATA_LENGTH + 1))
    }

    @Test(expected = IOException::class)
    fun rejectsMalformedFailLength() {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x4c494146).putInt(100).array()
        AdbSyncProtocol.parseResponse(bytes)
    }
}
