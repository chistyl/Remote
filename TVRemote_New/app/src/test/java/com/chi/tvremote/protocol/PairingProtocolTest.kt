package com.chi.tvremote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingProtocolTest {
    @Test fun pairingEnvelopeUsesProtocolVersionTwoAndStatus200() {
        val bytes = PairingProtocol.envelope(10, PairingProtocol.request("CHI TV Remote"))
        assertEquals(2L, ProtoCodec.firstVarintField(bytes, 1))
        assertEquals(200L, ProtoCodec.firstVarintField(bytes, 2))
        assertEquals(-1L, ProtoCodec.firstVarintField(bytes, 3))
        assertEquals(true, PairingProtocol.parseAck(bytes).hasMessage(10))
    }

    @Test fun optionsMatchReferenceProtocolShape() {
        val options = PairingProtocol.options()
        assertEquals(1L, ProtoCodec.firstVarintField(options, 3))
        // Encoding is nested under field 1; field 2 must not be duplicated at top level.
        assertEquals(-1L, ProtoCodec.firstVarintField(options, 2))
    }

    @Test fun parsesSuccessfulAckAndRejectsNon200Status() {
        val ok = ProtoCodec.varintField(1, 2) +
            ProtoCodec.varintField(2, 200) +
            ProtoCodec.messageField(11, byteArrayOf())
        assertEquals(true, PairingProtocol.parseAck(ok).hasMessage(11))

        val rejected = ProtoCodec.varintField(1, 2) +
            ProtoCodec.varintField(2, 400) +
            ProtoCodec.messageField(11, byteArrayOf())
        assertThrows(IllegalArgumentException::class.java) { PairingProtocol.parseAck(rejected) }
    }

    @Test fun pinSecretIncludesRequiredZeroSeparators() {
        val secret = PairingProtocol.pinSecret(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            byteArrayOf(1, 0, 1),
            byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte()),
            byteArrayOf(3),
            "F4B2C3"
        )
        assertEquals(
            "f44ec492c676c346b57e86f216f530a2523bc6648e7e3f645eac5267796f3923",
            secret.joinToString("") { "%02x".format(it) }
        )
    }

    @Test fun pinSecretRejectsIncorrectCheckByte() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingProtocol.pinSecret(
                byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), byteArrayOf(1, 0, 1),
                byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte()),
                byteArrayOf(3), "00B2C3"
            )
        }
    }
}
