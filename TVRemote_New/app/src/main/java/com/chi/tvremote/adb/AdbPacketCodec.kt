package com.chi.tvremote.adb

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB packet framing: six little-endian 32-bit header fields followed by payload bytes.
 * Stream routing and command semantics intentionally remain in AdbTransport.
 */
internal object AdbPacketCodec {
    private const val HEADER_SIZE = 24
    private const val MAX_PAYLOAD = 1024 * 1024

    fun encode(command: Int, arg0: Int, arg1: Int, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "ADB payload too large: ${payload.size}" }
        val checksum = payload.fold(0L) { sum, byte -> sum + (byte.toInt() and 0xff) }.toInt()
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1)
            .putInt(payload.size).putInt(checksum).putInt(command xor -1)
        return header.array() + payload
    }

    data class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    fun decode(header: ByteArray, payload: ByteArray, maxPayload: Int = MAX_PAYLOAD): Packet {
        if (header.size != HEADER_SIZE) throw IOException("ADB 封包標頭長度錯誤")
        val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = fields.int
        val arg0 = fields.int
        val arg1 = fields.int
        val length = fields.int
        val checksum = fields.int
        val magic = fields.int
        if (magic != (command xor -1)) throw IOException("ADB 封包標記錯誤")
        if (length < 0 || length > maxPayload || payload.size != length) {
            throw IOException("ADB 封包長度不合法：$length")
        }
        val actual = payload.fold(0L) { sum, byte -> sum + (byte.toInt() and 0xff) }.toInt()
        if (actual != checksum) throw IOException("ADB 封包 checksum 錯誤")
        return Packet(command, arg0, arg1, payload)
    }
}
