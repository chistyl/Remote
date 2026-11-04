package com.chi.tvremote.protocol

import java.security.MessageDigest

/** Pure Android TV Remote v2 pairing wire helpers, kept separate for regression tests. */
internal object PairingProtocol {
    data class Ack(val fields: Set<Int>, val status: Int) {
        fun hasMessage(type: Int): Boolean = type in fields
    }

    fun envelope(type: Int, body: ByteArray): ByteArray =
        ProtoCodec.varintField(1, 2L) + // Pairing protocol version (must be 2)
            ProtoCodec.varintField(2, 200L) +
            ProtoCodec.messageField(type, body)

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
        val rawStatus = ProtoCodec.firstVarintField(payload, 2)
        // Pairing envelopes identify their response by the presence of a
        // length-delimited message field (11, 21, 31 or 41), not a field-3 type.
        val fields = topLevelFields(payload)
        // The reference implementation treats an omitted status as 200.
        val status = if (rawStatus < 0) 200L else rawStatus
        require(status == 200L) { "電視拒絕配對要求（狀態 $status）" }
        return Ack(fields, status.toInt())
    }

    private fun topLevelFields(payload: ByteArray): Set<Int> {
        val fields = linkedSetOf<Int>()
        val cursor = intArrayOf(0)
        while (cursor[0] < payload.size) {
            val tag = ProtoCodec.decodeVarint(payload, cursor)
            val field = (tag ushr 3).toInt()
            require(field > 0) { "配對回應包含無效欄位標籤" }
            fields += field
            when ((tag and 7L).toInt()) {
                0 -> ProtoCodec.decodeVarint(payload, cursor)
                1 -> {
                    require(cursor[0] + 8 <= payload.size) { "配對回應資料不完整" }
                    cursor[0] += 8
                }
                2 -> {
                    val length = ProtoCodec.decodeVarint(payload, cursor)
                    require(length in 0..(payload.size - cursor[0]).toLong()) { "配對回應長度錯誤" }
                    cursor[0] += length.toInt()
                }
                5 -> {
                    require(cursor[0] + 4 <= payload.size) { "配對回應資料不完整" }
                    cursor[0] += 4
                }
                else -> throw IllegalArgumentException("配對回應包含不支援的 protobuf wire type")
            }
        }
        return fields
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
