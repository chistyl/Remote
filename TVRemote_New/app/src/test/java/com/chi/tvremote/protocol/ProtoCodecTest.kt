package com.chi.tvremote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ProtoCodecTest {
    @Test
    fun varintLengthAndPayloadSurviveEveryTcpSplit() {
        val sizes = listOf(0, 1, 127, 128, 300, 16_384)
        for (size in sizes) {
            val payload = ByteArray(size) { (it and 0xFF).toByte() }
            val frame = ProtoCodec.frame(payload)
            for (split in 0 until frame.size) {
                val first = frame.copyOfRange(0, split)
                val second = frame.copyOfRange(split, frame.size)
                assertNull("size=$size split=$split", ProtoCodec.tryUnframe(first))
                val decoded = ProtoCodec.tryUnframe(first + second)
                requireNotNull(decoded)
                assertEquals(frame.size, decoded.first)
                assertArrayEquals(payload, decoded.second)
            }
        }
    }

    @Test
    fun concatenatedFramesCanBeReadInOrder() {
        val first = byteArrayOf(1, 2, 3)
        val second = ByteArray(300) { it.toByte() }
        val joined = ProtoCodec.frame(first) + ProtoCodec.frame(second)
        val one = requireNotNull(ProtoCodec.tryUnframe(joined))
        assertArrayEquals(first, one.second)
        val two = requireNotNull(ProtoCodec.tryUnframe(joined.copyOfRange(one.first, joined.size)))
        assertArrayEquals(second, two.second)
    }

    @Test
    fun imeBatchEditEncodesCountersAndUnicodeText() {
        val text = "搜尋 YouTube 測試"
        val body = ProtoCodec.imeBatchEditBody(text, 12, 34)
        assertEquals(12L, ProtoCodec.firstVarintField(body, 1))
        assertEquals(34L, ProtoCodec.firstVarintField(body, 2))
        assertEquals(12 to 34, ProtoCodec.imeCounters(body))

        // Field 3 is the repeated RemoteEditInfo message.
        val pos = intArrayOf(0)
        var editInfo: ByteArray? = null
        while (pos[0] < body.size) {
            val tag = ProtoCodec.decodeVarint(body, pos)
            val field = (tag shr 3).toInt()
            val wire = (tag and 7L).toInt()
            if (wire == 0) {
                ProtoCodec.decodeVarint(body, pos)
            } else if (wire == 2) {
                val size = ProtoCodec.decodeVarint(body, pos).toInt()
                val value = body.copyOfRange(pos[0], pos[0] + size)
                pos[0] += size
                if (field == 3) editInfo = value
            } else {
                fail("unexpected wire type $wire")
            }
        }
        requireNotNull(editInfo)
        val nestedPos = intArrayOf(0)
        var imeObject: ByteArray? = null
        while (nestedPos[0] < editInfo!!.size) {
            val tag = ProtoCodec.decodeVarint(editInfo!!, nestedPos)
            val field = (tag shr 3).toInt()
            val wire = (tag and 7L).toInt()
            if (wire == 0) ProtoCodec.decodeVarint(editInfo!!, nestedPos)
            else if (wire == 2) {
                val size = ProtoCodec.decodeVarint(editInfo!!, nestedPos).toInt()
                val value = editInfo!!.copyOfRange(nestedPos[0], nestedPos[0] + size)
                nestedPos[0] += size
                if (field == 2) imeObject = value
            } else fail("unexpected nested wire type $wire")
        }
        val objectBytes = requireNotNull(imeObject)
        val textBytes = findLengthDelimitedField(objectBytes, 3)
        assertEquals(text, textBytes.toString(Charsets.UTF_8))
    }

    @Test
    fun imeBatchEditRejectsEmptyText() {
        try {
            ProtoCodec.imeBatchEditBody("", 1, 1)
            fail("expected empty text to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    private fun findLengthDelimitedField(message: ByteArray, wanted: Int): ByteArray {
        val pos = intArrayOf(0)
        while (pos[0] < message.size) {
            val tag = ProtoCodec.decodeVarint(message, pos)
            val field = (tag shr 3).toInt()
            val wire = (tag and 7L).toInt()
            if (wire == 0) ProtoCodec.decodeVarint(message, pos)
            else if (wire == 2) {
                val size = ProtoCodec.decodeVarint(message, pos).toInt()
                val value = message.copyOfRange(pos[0], pos[0] + size)
                pos[0] += size
                if (field == wanted) return value
            } else fail("unexpected wire type $wire")
        }
        fail("field $wanted not found")
        return byteArrayOf()
    }

    @Test
    fun parsesTvVolumeLevelAndMuteState() {
        val body = ProtoCodec.varintField(6, 100) +
            ProtoCodec.varintField(7, 37) +
            ProtoCodec.varintField(8, 0)
        val info = requireNotNull(ProtoCodec.volumeSnapshot(body))
        assertEquals(37, info.level)
        assertEquals(100, info.max)
        assertEquals(37, info.percent)
        assertEquals(false, info.muted)
    }

    @Test
    fun clampsVolumeLevelAndRejectsMissingMaximum() {
        val clamped = requireNotNull(ProtoCodec.volumeSnapshot(
            ProtoCodec.varintField(6, 20) + ProtoCodec.varintField(7, 99) + ProtoCodec.varintField(8, 1)
        ))
        assertEquals(20, clamped.level)
        assertEquals(100, clamped.percent)
        assertEquals(true, clamped.muted)
        assertNull(ProtoCodec.volumeSnapshot(ProtoCodec.varintField(7, 3)))
    }

    @Test
    fun parsesPowerStateWithoutInventingMissingState() {
        assertEquals(true, ProtoCodec.powerState(ProtoCodec.varintField(1, 1)))
        assertEquals(false, ProtoCodec.powerState(ProtoCodec.varintField(1, 0)))
        assertNull(ProtoCodec.powerState(byteArrayOf()))
    }

    @Test
    fun voiceSessionAndPayloadUseExpectedFieldsAndChunkBounds() {
        val session = ProtoCodec.voiceSessionBody(77)
        assertEquals(77L, ProtoCodec.firstVarintField(session, 1))
        assertEquals(77, ProtoCodec.voiceSessionId(session))
        val pcm = ByteArray(8_192) { (it and 0x7F).toByte() }
        val payload = ProtoCodec.voicePayloadBody(77, pcm)
        assertEquals(77L, ProtoCodec.firstVarintField(payload, 1))
        val data = findLengthDelimitedField(payload, 2)
        assertArrayEquals(pcm, data)
        try {
            ProtoCodec.voicePayloadBody(77, ByteArray(100))
            fail("expected undersized voice chunk to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

}
