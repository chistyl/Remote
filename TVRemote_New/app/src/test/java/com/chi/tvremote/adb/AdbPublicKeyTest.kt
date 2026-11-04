package com.chi.tvremote.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class AdbPublicKeyTest {
    @Test
    fun publicKeyUsesAndroidAdbWireFormat() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encoded = AdbTransport.androidPublicKey(pair.public as RSAPublicKey)
        val base64 = encoded.substringBefore(' ')
        val bytes = Base64.getDecoder().decode(base64)

        assertEquals(524, bytes.size)
        val fields = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, fields.int)
        val n0inv = fields.int.toLong() and 0xffffffffL
        val modulusBytes = ByteArray(256).also { fields.get(it) }
        val rrBytes = ByteArray(256).also { fields.get(it) }
        val exponent = fields.int

        val modulus = (pair.public as RSAPublicKey).modulus
        val radix = BigInteger.ONE.shiftLeft(32)
        val expectedN0inv = modulus.mod(radix).modInverse(radix).negate().mod(radix).toLong()
        assertEquals(expectedN0inv, n0inv)
        assertEquals(modulus, BigInteger(1, modulusBytes.reversedArray()))
        assertEquals(
            BigInteger.ONE.shiftLeft(4096).mod(modulus).pow(2).mod(modulus),
            BigInteger(1, rrBytes.reversedArray())
        )
        assertEquals((pair.public as RSAPublicKey).publicExponent.toInt(), exponent)
        assertTrue(encoded.endsWith(" tvremote"))
    }
}
