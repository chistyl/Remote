package com.chi.tvremote.protocol

import java.security.MessageDigest

/** Pure Android TV Remote v2 pairing wire helpers, kept separate for regression tests. */
internal object PairingProtocol {
    data class Ack(val type: Int, val status: Int) {
        fun hasMessage(expectedType: Int): Boolean = type == expectedType
    }

    /** OuterMessage: protocol_version=2, status=200, type, and serialized payload. */
    fun envelope(type: Int, body: ByteArray): ByteArray =
        ProtoCodec.varintField(1, 2L) +
            ProtoCodec.varintField(2, 200L) +
            ProtoCodec.varintField(3, type.toLong()) +
            ProtoCodec.bytesField(4, body)

    fun request(clientName: String): ByteArray =
        ProtoCodec.stringField(1, "atvremote") + ProtoCodec.stringField(2, clientName)

    fun options(): ByteArray {
        val encoding = ProtoCodec.varintField(1, 3L) + ProtoCodec.varintField(2, 6L)
        // PairingOptions: preferred role field 3 = input device.
        return ProtoCodec.messageField(1, encoding) + ProtoCodec.varintField(3, 1L)
    }

    fun configuration(): ByteArray {
        val encoding = ProtoCodec.varintField(1, 3L) + ProtoCodec.varintField(2, 6L)
        return ProtoCodec.messageField(1, encoding) + ProtoCodec.varintField(2, 1L)
    }

    fun parseAck(payload: ByteArray): Ack {
        val version = ProtoCodec.firstVarintField(payload, 1)
        val rawStatus = ProtoCodec.firstVarintField(payload, 2)
        val rawType = ProtoCodec.firstVarintField(payload, 3)
        require(version == 2L) { "配對協定版本不支援：$version" }
        val status = if (rawStatus < 0) 200L else rawStatus
        require(status == 200L) { "電視拒絕配對要求（狀態 $status）" }
        require(rawType in 0..Int.MAX_VALUE.toLong()) { "電視配對回應缺少訊息類型" }
        require(hasBytesField(payload, 4)) { "電視配對回應缺少訊息內容" }
        return Ack(rawType.toInt(), status.toInt())
    }

    private fun hasBytesField(payload: ByteArray, wanted: Int): Boolean {
        val cursor = intArrayOf(0)
        while (cursor[0] < payload.size) {
            val tag = ProtoCodec.decodeVarint(payload, cursor)
            val field = (tag ushr 3).toInt()
            when ((tag and 7L).toInt()) {
                0 -> ProtoCodec.decodeVarint(payload, cursor)
                1 -> {
                    require(cursor[0] + 8 <= payload.size) { "配對回應資料不完整" }
                    cursor[0] += 8
                }
                2 -> {
                    val length = ProtoCodec.decodeVarint(payload, cursor)
                    require(length in 0..(payload.size - cursor[0]).toLong()) { "配對回應長度錯誤" }
                    if (field == wanted) return true
                    cursor[0] += length.toInt()
                }
                5 -> {
                    require(cursor[0] + 4 <= payload.size) { "配對回應資料不完整" }
                    cursor[0] += 4
                }
                else -> throw IllegalArgumentException("配對回應包含不支援的 protobuf wire type")
            }
        }
        return false
    }

    /**
     * Android TV Remote v2 PIN proof. The zero-byte separators are required
     * between each RSA modulus and its public exponent. Omitting them makes
     * otherwise-correct six-character PINs fail on real televisions.
     */
    fun pinSecret(
        clientModulus: ByteArray,
        clientExponent: ByteArray,
        serverModulus: ByteArray,
        serverExponent: ByteArray,
        pin: String
    ): ByteArray {
        require(Regex("^[0-9A-Fa-f]{6}$").matches(pin)) { "配對碼必須是 6 位十六進位數字" }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(clientModulus)
        digest.update(0)
        digest.update(clientExponent)
        digest.update(serverModulus)
        digest.update(0)
        digest.update(serverExponent)
        digest.update(pin.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val secret = digest.digest()
        val expected = pin.substring(0, 2).toInt(16)
        require((secret[0].toInt() and 0xFF) == expected) {
            "配對碼驗證失敗，請確認電視上的 6 位配對碼"
        }
        return secret
    }
}
