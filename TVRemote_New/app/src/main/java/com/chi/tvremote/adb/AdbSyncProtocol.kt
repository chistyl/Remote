package com.chi.tvremote.adb

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB SYNC v1 message framing used by the scrcpy server file-push stage.
 * This codec only builds/parses SYNC payloads; transport stream I/O is handled
 * separately by AdbTransport so the protocol can be tested without a TV.
 */
internal object AdbSyncProtocol {
    private const val ID_SEND = 0x444e4553 // SEND
    private const val ID_DATA = 0x41544144 // DATA
    private const val ID_DONE = 0x454e4f44 // DONE
    private const val ID_OKAY = 0x59414b4f // OKAY
    private const val ID_FAIL = 0x4c494146 // FAIL
    const val MAX_DATA_LENGTH = 64 * 1024

    data class Response(val success: Boolean, val message: String)

    fun sendHeader(remotePath: String, mode: Int = 0x1a4): ByteArray {
        require(remotePath.isNotBlank()) { "遠端檔案路徑不可空白" }
        require(!remotePath.contains('\u0000')) { "遠端檔案路徑不可包含 NUL" }
        val destination = "$remotePath,$mode".toByteArray(Charsets.UTF_8)
        return frame(ID_SEND, destination)
    }

    fun dataChunk(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "SYNC DATA 不可為空" }
        require(data.size <= MAX_DATA_LENGTH) { "SYNC DATA 超過 64 KiB" }
        val output = ByteArrayOutputStream(8 + data.size)
        val stream = DataOutputStream(output)
        writeIntLe(stream, ID_DATA)
        writeIntLe(stream, data.size)
        stream.write(data)
        return output.toByteArray()
    }

    fun done(timestampSeconds: Long): ByteArray {
        require(timestampSeconds in 0..0xffffffffL) { "SYNC DONE 時間戳超出範圍" }
        val output = ByteArrayOutputStream(8)
        val stream = DataOutputStream(output)
        writeIntLe(stream, ID_DONE)
        writeIntLe(stream, timestampSeconds.toInt())
        return output.toByteArray()
    }

    @Throws(IOException::class)
    fun parseResponse(bytes: ByteArray): Response {
        if (bytes.size < 4) throw IOException("ADB SYNC 回應長度不足")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return when (buffer.int) {
            ID_OKAY -> {
                if (buffer.hasRemaining()) throw IOException("ADB SYNC OKAY 回應格式不合法")
                Response(true, "")
            }
            ID_FAIL -> {
                val length = buffer.int
                if (length < 0 || length > buffer.remaining()) {
                    throw IOException("ADB SYNC FAIL 訊息長度不合法")
                }
                val message = ByteArray(length)
                buffer.get(message)
                Response(false, String(message, Charsets.UTF_8))
            }
            else -> throw IOException("不支援的 ADB SYNC 回應")
        }
    }

    private fun frame(id: Int, payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(8 + payload.size)
        val stream = DataOutputStream(output)
        writeIntLe(stream, id)
        writeIntLe(stream, payload.size)
        stream.write(payload)
        return output.toByteArray()
    }

    private fun writeIntLe(output: DataOutputStream, value: Int) {
        output.writeInt(Integer.reverseBytes(value))
    }
}
