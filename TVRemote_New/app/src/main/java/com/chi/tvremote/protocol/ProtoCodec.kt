package com.chi.tvremote.protocol

import java.io.ByteArrayOutputStream

object ProtoCodec {
    fun encodeVarint(value: Long): ByteArray {
        var v = value
        val out = ByteArrayOutputStream()
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7FL).toInt())
        return out.toByteArray()
    }

    fun decodeVarint(buf: ByteArray, posRef: IntArray): Long {
        var result = 0L
        var shift = 0
        var pos = posRef[0]
        while (pos < buf.size && shift <= 63) {
            val b = buf[pos++].toInt() and 0xFF
            if (shift == 63 && (b and 0xFE) != 0) {
                throw IllegalArgumentException("Invalid protobuf varint overflow")
            }
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) {
                posRef[0] = pos
                return result
            }
            shift += 7
        }
        posRef[0] = pos
        throw IllegalArgumentException("Invalid protobuf varint")
    }

    fun tag(field: Int, wire: Int) = encodeVarint(((field.toLong() shl 3) or wire.toLong()))
    fun varintField(field: Int, value: Long) = tag(field, 0) + encodeVarint(value)
    fun bytesField(field: Int, data: ByteArray) = tag(field, 2) + encodeVarint(data.size.toLong()) + data
    fun stringField(field: Int, value: String) = bytesField(field, value.toByteArray(Charsets.UTF_8))
    fun messageField(field: Int, value: ByteArray) = bytesField(field, value)
    fun frame(payload: ByteArray) = encodeVarint(payload.size.toLong()) + payload

    fun tryUnframe(buffer: ByteArray): Pair<Int, ByteArray>? {
        if (buffer.isEmpty()) return null
        // TCP may split even the varint length prefix; incomplete prefixes are not malformed.
        var length = 0L
        var shift = 0
        var header = 0
        var complete = false
        while (header < buffer.size && header < 10) {
            val b = buffer[header++].toInt() and 0xFF
            if (shift == 63 && (b and 0xFE) != 0) {
                throw IllegalArgumentException("Invalid frame length prefix")
            }
            length = length or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) { complete = true; break }
            shift += 7
        }
        if (!complete) {
            if (header >= 10) throw IllegalArgumentException("Invalid frame length prefix")
            return null
        }
        if (length < 0L || length > 1_048_576L) throw IllegalArgumentException("Invalid frame length")
        val size = length.toInt()
        if (buffer.size - header < size) return null
        return (header + size) to buffer.copyOfRange(header, header + size)
    }


    /** Build RemoteImeBatchEdit body (outer RemoteMessage field 21). */
    fun imeBatchEditBody(text: String, imeCounter: Int, fieldCounter: Int): ByteArray {
        require(text.isNotEmpty()) { "Text cannot be empty" }
        val cursor = (text.length - 1).coerceAtLeast(0).toLong()
        val imeObject = varintField(1, cursor) +
            varintField(2, cursor) +
            stringField(3, text)
        val editInfo = varintField(1, 1) + messageField(2, imeObject)
        return varintField(1, imeCounter.toLong()) +
            varintField(2, fieldCounter.toLong()) +
            messageField(3, editInfo)
    }

    fun imeCounters(body: ByteArray): Pair<Int, Int>? {
        val ime = firstVarintField(body, 1)
        val field = firstVarintField(body, 2)
        if (ime < 0 || field < 0 || ime > Int.MAX_VALUE || field > Int.MAX_VALUE) return null
        return ime.toInt() to field.toInt()
    }


    data class VolumeSnapshot(val level: Int, val max: Int, val muted: Boolean) {
        val percent: Int get() = if (max > 0) ((level.coerceIn(0, max) * 100f) / max).toInt() else 0
    }

    /** Build RemoteVoicePayload body (outer RemoteMessage field 31). */
    fun voicePayloadBody(sessionId: Int, pcmSamples: ByteArray): ByteArray {
        require(sessionId >= 0) { "Voice session id is invalid" }
        require(pcmSamples.size in 8_192..20_480) { "Voice chunk must be between 8 KB and 20 KB" }
        return varintField(1, sessionId.toLong()) + bytesField(2, pcmSamples)
    }

    /** Build RemoteVoiceBegin / RemoteVoiceEnd body using the TV-issued session id. */
    fun voiceSessionBody(sessionId: Int): ByteArray {
        require(sessionId >= 0) { "Voice session id is invalid" }
        return varintField(1, sessionId.toLong())
    }

    fun voiceSessionId(body: ByteArray): Int? {
        val value = firstVarintField(body, 1)
        return value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }

    /** Parses RemoteSetVolumeLevel (outer RemoteMessage field 50). */
    fun volumeSnapshot(body: ByteArray): VolumeSnapshot? {
        val maxValue = firstVarintField(body, 6)
        val levelValue = firstVarintField(body, 7)
        val mutedValue = firstVarintField(body, 8)
        if (maxValue <= 0L || maxValue > Int.MAX_VALUE || levelValue < 0L || levelValue > Int.MAX_VALUE) return null
        val max = maxValue.toInt()
        return VolumeSnapshot(levelValue.toInt().coerceIn(0, max), max, mutedValue > 0L)
    }

    /** Parses RemoteStart (outer RemoteMessage field 40); null means no valid state field. */
    fun powerState(body: ByteArray): Boolean? = when (firstVarintField(body, 1)) {
        0L -> false
        1L -> true
        else -> null
    }

    fun firstVarintField(msg: ByteArray, wanted: Int): Long {
        val pos = intArrayOf(0)
        while (pos[0] < msg.size) {
            val tag = decodeVarint(msg, pos)
            val field = (tag shr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> {
                    val value = decodeVarint(msg, pos)
                    if (field == wanted) return value
                }
                2 -> {
                    val len = decodeVarint(msg, pos).toInt()
                    if (len < 0 || pos[0] + len > msg.size) return -1
                    pos[0] += len
                }
                1 -> {
                    if (pos[0] + 8 > msg.size) return -1
                    pos[0] += 8
                }
                5 -> {
                    if (pos[0] + 4 > msg.size) return -1
                    pos[0] += 4
                }
                else -> return -1
            }
        }
        return -1
    }
}
