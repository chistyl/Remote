package com.chi.tvremote.protocol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Android TV Remote v2 control channel (mutual TLS, TCP 6466). */
class TvRemoteClient(context: Context) : Closeable {
    // Defer RSA identity creation until connect() runs on Dispatchers.IO.
    private val identity by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ClientIdentity.get(context.applicationContext) }
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var connected = false
    @Volatile private var activeFeatures = 0
    @Volatile private var imeCounter = 0
    @Volatile private var imeFieldCounter = 0
    private val writeLock = Any()

    suspend fun connect(host: String): String = withContext(Dispatchers.IO) {
        close()
        val ssl = SSLContext.getInstance("TLSv1.2")
        ssl.init(identity.keyManagers(), ClientIdentity.trustManagers(), java.security.SecureRandom())
        val s = ssl.socketFactory.createSocket() as SSLSocket
        s.soTimeout = 12_000
        s.enabledProtocols = arrayOf("TLSv1.2")
        s.connect(InetSocketAddress(host, 6466), 8_000)
        s.startHandshake()
        socket = s

        val tvConfigure = ProtoWire.fields(ProtoWire.readFrame(s.inputStream))
            .firstOrNull { it.number == 1 && it.wireType == 2 }
            ?: throw IOException("電視未送出 RemoteConfigure，請確認已完成配對")
        val tvConfigFields = ProtoWire.fields(tvConfigure.bytesValue)
        val tvFeatures = tvConfigFields.firstOrNull { it.number == 1 && it.wireType == 0 }?.numberValue?.toInt() ?: 0
        val requested = 1 or 2 or 4 or 32 or 64 or 512
        activeFeatures = requested and tvFeatures
        if (activeFeatures and 2 == 0) throw IOException("電視未宣告 KEY 控制功能，無法啟動遙控")

        val deviceInfo = ProtoWire.concat(
            ProtoWire.string(1, "CHI TV Remote"),
            ProtoWire.string(2, "CHI"),
            ProtoWire.uint(3, 1),
            ProtoWire.string(4, "1"),
            ProtoWire.string(5, "com.chi.tvremote"),
            ProtoWire.string(6, "0.6.0")
        )
        val configureResponse = ProtoWire.concat(
            ProtoWire.uint(1, activeFeatures.toLong()),
            ProtoWire.bytes(2, deviceInfo)
        )
        writeRemote(s, ProtoWire.bytes(1, configureResponse))

        val tvActive = ProtoWire.fields(ProtoWire.readFrame(s.inputStream))
        if (tvActive.none { it.number == 2 && it.wireType == 2 }) {
            throw IOException("電視未送出 RemoteSetActive，遙控握手未完成")
        }
        writeRemote(s, ProtoWire.bytes(2, ProtoWire.uint(1, activeFeatures.toLong())))
        s.soTimeout = 0
        connected = true
        "已連接 $host（功能碼 $activeFeatures）"
    }

    suspend fun key(keyCode: Int) = withContext(Dispatchers.IO) {
        val s = socketOrThrow()
        val keyInject = ProtoWire.concat(ProtoWire.uint(1, keyCode.toLong()), ProtoWire.uint(2, 3))
        writeRemote(s, ProtoWire.bytes(10, keyInject))
    }

    suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        require(text.isNotEmpty()) { "請先輸入文字" }
        val s = socketOrThrow()
        if (activeFeatures and 4 == 0) throw IOException("這台電視未支援 Remote IME 文字輸入")
        val cursor = text.codePointCount(0, text.length).coerceAtLeast(1) - 1
        val imeObject = ProtoWire.concat(
            ProtoWire.uint(1, cursor.toLong()),
            ProtoWire.uint(2, cursor.toLong()),
            ProtoWire.string(3, text)
        )
        val editInfo = ProtoWire.concat(ProtoWire.uint(1, 1), ProtoWire.bytes(2, imeObject))
        val batch = ProtoWire.concat(
            ProtoWire.uint(1, imeCounter.toLong()),
            ProtoWire.uint(2, imeFieldCounter.toLong()),
            ProtoWire.bytes(3, editInfo)
        )
        writeRemote(s, ProtoWire.bytes(21, batch))
    }

    suspend fun listen(onDisconnected: (String) -> Unit) = withContext(Dispatchers.IO) {
        val s = socketOrThrow()
        try {
            while (connected && !s.isClosed) {
                val fields = ProtoWire.fields(ProtoWire.readFrame(s.inputStream))
                fields.firstOrNull { it.number == 8 && it.wireType == 2 }?.let { ping ->
                    val pingFields = ProtoWire.fields(ping.bytesValue)
                    val value = pingFields.firstOrNull { it.number == 1 && it.wireType == 0 }?.numberValue ?: 0L
                    writeRemote(s, ProtoWire.bytes(9, ProtoWire.uint(1, value)))
                }
                fields.firstOrNull { it.number == 21 && it.wireType == 2 }?.let { batch ->
                    val counters = ProtoWire.fields(batch.bytesValue)
                    imeCounter = counters.firstOrNull { it.number == 1 && it.wireType == 0 }?.numberValue?.toInt() ?: imeCounter
                    imeFieldCounter = counters.firstOrNull { it.number == 2 && it.wireType == 0 }?.numberValue?.toInt() ?: imeFieldCounter
                }
                fields.firstOrNull { it.number == 3 && it.wireType == 2 }?.let {
                    val error = ProtoWire.fields(it.bytesValue).firstOrNull { f -> f.number == 1 && f.wireType == 0 }?.numberValue
                    if (error == 1L) throw IOException("電視回報 RemoteError")
                }
            }
        } catch (e: Exception) {
            if (connected) onDisconnected(e.localizedMessage ?: "電視連線中斷")
        } finally {
            connected = false
        }
    }

    fun isConnected() = connected
    fun supportsIme() = activeFeatures and 4 != 0

    private fun socketOrThrow(): SSLSocket = socket?.takeIf { connected && !it.isClosed }
        ?: throw IOException("遙控連線尚未建立")

    private fun writeRemote(s: SSLSocket, message: ByteArray) {
        synchronized(writeLock) { ProtoWire.writeFrame(s.outputStream, message) }
    }

    override fun close() {
        connected = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        activeFeatures = 0
        imeCounter = 0
        imeFieldCounter = 0
    }
}
