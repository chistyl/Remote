package com.chi.tvremote.protocol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Android TV Remote v2 (Polo) pairing over mutual TLS, TCP 6467. */
class TvPairingClient(context: Context) : Closeable {
    // Defer RSA identity creation until begin() runs on Dispatchers.IO.
    private val identity by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ClientIdentity.get(context.applicationContext) }
    private var socket: SSLSocket? = null
    private var serverKey: RSAPublicKey? = null
    private var phaseReady = false

    suspend fun begin(host: String): String = withContext(Dispatchers.IO) {
        close()
        val ssl = SSLContext.getInstance("TLSv1.2")
        ssl.init(identity.keyManagers(), ClientIdentity.trustManagers(), java.security.SecureRandom())
        val raw = ssl.socketFactory.createSocket() as SSLSocket
        raw.soTimeout = 15_000
        raw.enabledProtocols = arrayOf("TLSv1.2")
        raw.connect(InetSocketAddress(host, 6467), 8_000)
        raw.startHandshake()
        socket = raw
        serverKey = (raw.session.peerCertificates.firstOrNull()?.publicKey as? RSAPublicKey)
            ?: throw IOException("電視未提供可用的 RSA 憑證")

        sendEnvelope(raw, 10, ProtoWire.concat(
            ProtoWire.string(1, "atvremote"), ProtoWire.string(2, "CHI TV Remote")
        ))
        expectEnvelope(raw, 11)

        val encoding = ProtoWire.concat(ProtoWire.uint(1, 3), ProtoWire.uint(2, 6))
        val options = ProtoWire.concat(ProtoWire.bytes(1, encoding), ProtoWire.uint(3, 1))
        sendEnvelope(raw, 20, options)
        val optionsReply = readEnvelope(raw)
        if (optionsReply.type != 20) throw IOException("電視配對選項回應異常（${optionsReply.type}）")

        val configuration = ProtoWire.concat(ProtoWire.bytes(1, encoding), ProtoWire.uint(2, 1))
        sendEnvelope(raw, 30, configuration)
        expectEnvelope(raw, 31)
        phaseReady = true
        "已與 $host 建立配對通道，請查看電視畫面上的 6 位數十六進位驗證碼。"
    }

    suspend fun finish(pinInput: String): String = withContext(Dispatchers.IO) {
        val raw = socket ?: throw IOException("配對通道已中斷，請重新開始")
        if (!phaseReady) throw IOException("尚未完成配對初始化")
        val pin = pinInput.trim().uppercase()
        if (!pin.matches(Regex("[0-9A-F]{6}"))) throw IOException("請輸入電視顯示的 6 位數十六進位驗證碼")
        val digest = pairingDigest(pin, identity.keyPair.public as RSAPublicKey, serverKey ?: error("Missing TV key"))
        val expectedPrefix = pin.substring(0, 2).toInt(16)
        if ((digest[0].toInt() and 0xff) != expectedPrefix) throw IOException("驗證碼不正確，請確認電視畫面上的代碼")
        sendEnvelope(raw, 40, ProtoWire.bytes(1, digest))
        expectEnvelope(raw, 41)
        phaseReady = false
        close()
        "配對成功！此手機憑證已儲存，可用於後續連線。"
    }

    private fun pairingDigest(pin: String, client: RSAPublicKey, server: RSAPublicKey): ByteArray {
        val input = ProtoWire.concat(
            unsigned(client.modulus), byteArrayOf(0), unsigned(client.publicExponent),
            unsigned(server.modulus), byteArrayOf(0), unsigned(server.publicExponent),
            byteArrayOf(pin.substring(2).toInt(16).toByte(), pin.substring(4).toInt(16).toByte())
        )
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun unsigned(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private data class Envelope(val type: Int, val status: Int)

    private fun sendEnvelope(raw: SSLSocket, type: Int, payload: ByteArray) {
        val outer = ProtoWire.concat(
            ProtoWire.uint(1, 2), ProtoWire.uint(2, 200), ProtoWire.uint(3, type.toLong()), ProtoWire.bytes(4, payload)
        )
        ProtoWire.writeFrame(raw.outputStream, outer)
    }

    private fun readEnvelope(raw: SSLSocket): Envelope {
        val fields = ProtoWire.fields(ProtoWire.readFrame(raw.inputStream))
        val status = fields.firstOrNull { it.number == 2 }?.numberValue?.toInt() ?: -1
        val type = fields.firstOrNull { it.number == 3 }?.numberValue?.toInt() ?: -1
        if (status != 200) throw IOException("電視回報配對狀態 $status")
        return Envelope(type, status)
    }

    private fun expectEnvelope(raw: SSLSocket, expected: Int) {
        val reply = readEnvelope(raw)
        if (reply.type != expected) throw IOException("配對協定回應不符：預期 $expected，收到 ${reply.type}")
    }

    override fun close() {
        phaseReady = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        serverKey = null
    }
}
