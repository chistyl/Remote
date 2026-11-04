package com.chi.tvremote.adb

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Classic ADB-over-TCP transport, RSA authorization and single-reader stream routing.
 * Shell commands use independent logical stream IDs. scrcpy UHID input is a separate
 * layer and is not implied by a successful ADB shell connection.
 */
class AdbTransport private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream
) : Closeable {

    data class ConnectResult(val transport: AdbTransport, val authorizationRequested: Boolean)

    @Volatile
    var connected: Boolean = true
        private set

    private data class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    @Volatile
    private var peerMaxPayload: Int = MAX_PAYLOAD
    private val streamRegistry = AdbStreamRegistry()
    @Volatile
    private var readerPump: AdbPacketReaderPump? = null

    @Synchronized
    private fun writePacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        if (payload.size > peerMaxPayload) {
            throw IOException("ADB 封包超過電視允許上限：${payload.size}/$peerMaxPayload")
        }
        output.write(AdbPacketCodec.encode(command, arg0, arg1, payload))
        output.flush()
    }

    private fun readPacket(): Packet {
        val header = ByteArray(24)
        input.readFully(header)
        val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val length = fields.getInt(12)
        if (length < 0 || length > MAX_PAYLOAD) throw IOException("ADB 封包長度不合法：$length")
        val payload = ByteArray(length)
        input.readFully(payload)
        val decoded = AdbPacketCodec.decode(header, payload, MAX_PAYLOAD)
        return Packet(decoded.command, decoded.arg0, decoded.arg1, decoded.payload)
    }

    @Throws(IOException::class)
    fun shell(command: String, timeoutMs: Int = 10000): String {
        if (!connected) throw IOException("ADB 尚未連線")
        require(timeoutMs > 0) { "timeoutMs 必須大於 0" }
        val pump = readerPump ?: throw IOException("ADB 封包讀取器尚未啟動")
        if (!pumpRunning()) throw IOException("ADB 封包讀取器已停止")
        val stream = streamRegistry.open()
        var remoteId = 0
        val result = java.io.ByteArrayOutputStream()
        try {
            writePacket(A_OPEN, stream.localId, 0, ("shell:" + command + "\u0000").toByteArray(Charsets.UTF_8))
            val deadline = System.currentTimeMillis() + timeoutMs
            var opened = false
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) throw java.net.SocketTimeoutException("ADB Shell 指令逾時")
                val packet = stream.take(remaining)
                when (packet.command) {
                    A_OKAY -> {
                        if (!opened) {
                            remoteId = packet.remoteId
                            opened = true
                        } else {
                            // ADB flow-control acknowledgement; no payload is expected.
                        }
                    }
                    A_WRTE -> {
                        if (!opened || packet.remoteId != remoteId) {
                            throw IOException("ADB Shell 收到不屬於此串流的資料")
                        }
                        result.write(packet.payload)
                        writePacket(A_OKAY, stream.localId, remoteId, ByteArray(0))
                    }
                    A_CLSE -> {
                        if (opened && packet.remoteId != remoteId) {
                            throw IOException("ADB Shell 關閉封包的遠端 ID 不符")
                        }
                        writePacket(A_CLSE, stream.localId, packet.remoteId, ByteArray(0))
                        break
                    }
                    else -> throw IOException("ADB Shell 收到非預期封包：0x" + packet.command.toString(16))
                }
            }
            return result.toString("UTF-8").replace("\r\n", "\n").trim()
        } catch (e: java.net.SocketTimeoutException) {
            close()
            throw IOException("ADB Shell 指令逾時，已中斷失效連線", e)
        } catch (e: IOException) {
            close()
            throw e
        } finally {
            streamRegistry.close(stream.localId)
        }
    }

    /**
     * Opens an ADB localabstract service as a full-duplex byte channel.
     * Inbound WRTE packets and outbound A_OKAY flow-control replies are routed
     * to independent queues, so a reader can never consume a writer's ACK.
     */
    @Throws(IOException::class)
    fun openLocalAbstract(name: String, timeoutMs: Int = 10000): AdbDuplexChannel {
        if (!connected) throw IOException("ADB 尚未連線")
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "localabstract 名稱不合法" }
        require(timeoutMs > 0) { "timeoutMs 必須大於 0" }
        val stream = streamRegistry.open()
        stream.enableDuplex()
        try {
            writePacket(A_OPEN, stream.localId, 0,
                ("localabstract:" + name + "\u0000").toByteArray(Charsets.UTF_8))
            val opened = stream.takeDuplexAck(timeoutMs.toLong())
            if (opened.command != A_OKAY || opened.remoteId <= 0) {
                throw IOException("電視拒絕開啟 ADB localabstract:$name")
            }
            return AdbDuplexChannel(
                localId = stream.localId,
                remoteId = opened.remoteId,
                maxPayload = peerMaxPayload,
                readPacket = { waitMs -> stream.takeDuplexData(waitMs) },
                writePacket = { command, local, remote, payload ->
                    this.writePacket(command, local, remote, payload)
                },
                closeStream = { local, remote ->
                    if (connected) this.writePacket(A_CLSE, local, remote, ByteArray(0))
                    streamRegistry.close(local)
                }
            )
        } catch (e: IOException) {
            streamRegistry.close(stream.localId)
            throw e
        }
    }

    /**
     * Pushes a local file through the ADB SYNC service on its own logical stream.
     * The transport reader remains shared, while every WRTE is flow-controlled by
     * its matching A_OKAY before the next chunk is sent.
     */
    @Throws(IOException::class)
    fun pushFile(
        localFile: File,
        remotePath: String,
        mode: Int = 0x1a4,
        timeoutMs: Int = 15000
    ) {
        if (!connected) throw IOException("ADB 尚未連線")
        if (!localFile.isFile || !localFile.canRead()) throw IOException("找不到可讀取的檔案：${localFile.name}")
        require(timeoutMs > 0) { "timeoutMs 必須大於 0" }
        val stream = streamRegistry.open()
        var remoteId = 0
        var opened = false
        val deadline = System.currentTimeMillis() + timeoutMs
        fun remaining(): Long {
            val value = deadline - System.currentTimeMillis()
            if (value <= 0) throw java.net.SocketTimeoutException("ADB SYNC 傳輸逾時")
            return value
        }
        fun awaitAck() {
            while (true) {
                val packet = stream.take(remaining())
                when (packet.command) {
                    A_OKAY -> {
                        if (packet.remoteId != remoteId) throw IOException("ADB SYNC ACK 遠端串流 ID 不符")
                        return
                    }
                    A_CLSE -> throw IOException("ADB SYNC 串流在傳輸期間被電視關閉")
                    else -> throw IOException("ADB SYNC 等待 ACK 時收到非預期封包")
                }
            }
        }
        fun writeFrame(frame: ByteArray) {
            writePacket(A_WRTE, stream.localId, remoteId, frame)
            awaitAck()
        }
        try {
            writePacket(A_OPEN, stream.localId, 0, "sync:\u0000".toByteArray(Charsets.UTF_8))
            val openReply = stream.take(remaining())
            if (openReply.command != A_OKAY) throw IOException("電視拒絕開啟 ADB SYNC 服務")
            remoteId = openReply.remoteId
            if (remoteId <= 0) throw IOException("ADB SYNC 遠端串流 ID 不合法")
            opened = true

            writeFrame(AdbSyncProtocol.sendHeader(remotePath, mode))
            localFile.inputStream().use { source ->
                val buffer = ByteArray(AdbSyncProtocol.MAX_DATA_LENGTH)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    writeFrame(AdbSyncProtocol.dataChunk(if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)))
                }
            }
            val timestamp = System.currentTimeMillis() / 1000L
            writeFrame(AdbSyncProtocol.done(timestamp))

            // The SYNC service returns its final OKAY/FAIL record in A_WRTE.
            val responsePacket = stream.take(remaining())
            if (responsePacket.command != A_WRTE || responsePacket.remoteId != remoteId) {
                throw IOException("ADB SYNC 未收到有效的完成回應")
            }
            writePacket(A_OKAY, stream.localId, remoteId, ByteArray(0))
            val response = AdbSyncProtocol.parseResponse(responsePacket.payload)
            if (!response.success) throw IOException("ADB SYNC 傳輸失敗：${response.message}")
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException("ADB SYNC 傳輸逾時", e)
        } finally {
            if (opened && connected) {
                try { writePacket(A_CLSE, stream.localId, remoteId, ByteArray(0)) } catch (_: Exception) { }
            }
            streamRegistry.close(stream.localId)
        }
    }

    private fun pumpRunning(): Boolean = connected && readerPump != null

    override fun close() {
        connected = false
        try { socket.close() } catch (_: Exception) { }
        try { readerPump?.close() } catch (_: Exception) { }
        readerPump = null
    }

    companion object {
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
        private const val A_SYNC = 0x434e5953
        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private const val VERSION = 0x01000001
        private const val MAX_PAYLOAD = 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val AUTH_TIMEOUT_MS = 120000

        /** Internal socket factory for protocol-level tests; production uses connect(). */
        internal fun fromConnectedSocketForTest(socket: Socket, maxPayload: Int = MAX_PAYLOAD): AdbTransport {
            val transport = AdbTransport(
                socket,
                DataInputStream(socket.getInputStream()),
                DataOutputStream(socket.getOutputStream())
            )
            transport.peerMaxPayload = maxPayload.coerceIn(1024, MAX_PAYLOAD)
            transport.readerPump = AdbPacketReaderPump(
                socket.getInputStream(),
                transport.streamRegistry,
                { error ->
                    transport.connected = false
                    try { socket.close() } catch (_: Exception) { }
                },
                { transport.peerMaxPayload }
            ).also { it.start() }
            return transport
        }

        /**
         * Connects to a TV exposing legacy ADB TCP (normally port 5555).
         * On first use Android TV displays its own "Allow USB debugging?" dialog.
         */
        @Throws(IOException::class)
        fun connect(filesDir: File, host: String, port: Int = 5555, onSocketCreated: (Socket) -> Unit = {}): ConnectResult {
            require(host.isNotBlank()) { "電視 IP 不可空白" }
            val socket = Socket()
            try {
                onSocketCreated(socket)
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = CONNECT_TIMEOUT_MS
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                val transport = AdbTransport(socket, input, output)
                val keys = loadOrCreateKeyPair(File(filesDir, "adb"))
                transport.writePacket(A_CNXN, VERSION, MAX_PAYLOAD, "host::features=shell_v2,cmd\u0000".toByteArray(Charsets.UTF_8))
                var signatureSent = false
                var publicKeySent = false
                var authorizationRequested = false
                val deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS
                socket.soTimeout = AUTH_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    val packet = try { transport.readPacket() } catch (e: java.net.SocketTimeoutException) {
                        val reason = if (publicKeySent) "等待電視確認 ADB 授權逾時" else "等待 ADB 電視回應逾時"
                        throw IOException(reason, e)
                    }
                    when (packet.command) {
                        A_CNXN -> {
                            // CNXN.arg1 is the peer's maximum transport payload size.
                            // Clamp defensively to our local packet decoder limit.
                            transport.peerMaxPayload = packet.arg1.coerceIn(1024, MAX_PAYLOAD)
                            socket.soTimeout = 0
                            transport.readerPump = AdbPacketReaderPump(
                                socket.getInputStream(),
                                transport.streamRegistry,
                                { error ->
                                    transport.connected = false
                                    try { socket.close() } catch (_: Exception) { }
                                },
                                { transport.peerMaxPayload }
                            ).also { it.start() }
                            return ConnectResult(transport, authorizationRequested)
                        }
                        A_AUTH -> {
                            if (packet.arg0 != AUTH_TOKEN || packet.payload.size != 20) {
                                throw IOException("收到不支援的 ADB AUTH 封包")
                            }
                            if (!signatureSent) {
                                val signer = Signature.getInstance("SHA1withRSA")
                                signer.initSign(keys.private)
                                signer.update(packet.payload)
                                transport.writePacket(A_AUTH, AUTH_SIGNATURE, 0, signer.sign())
                                signatureSent = true
                            } else if (!publicKeySent) {
                                transport.writePacket(A_AUTH, AUTH_RSAPUBLICKEY, 0, (androidPublicKey(keys.public as RSAPublicKey) + "\u0000").toByteArray(Charsets.UTF_8))
                                publicKeySent = true
                                authorizationRequested = true
                                socket.soTimeout = AUTH_TIMEOUT_MS
                            } else {
                                throw IOException("ADB RSA 授權未獲接受")
                            }
                        }
                        A_SYNC -> throw IOException("電視要求 ADB 同步，非預期回應")
                        else -> throw IOException("不支援的 ADB 封包：0x" + packet.command.toString(16))
                    }
                }
                throw IOException("等待 ADB 授權逾時")
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) { }
                if (e is IOException) throw e
                throw IOException("ADB 連線失敗：${e.message ?: e.javaClass.simpleName}", e)
            }
        }

        private fun loadOrCreateKeyPair(directory: File): KeyPair {
            if (!directory.exists() && !directory.mkdirs()) throw IOException("無法建立 ADB 金鑰資料夾")
            val privateFile = File(directory, "adbkey.pk8")
            val publicFile = File(directory, "adbkey.x509")
            val factory = KeyFactory.getInstance("RSA")
            if (privateFile.isFile && publicFile.isFile) {
                return KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())),
                    factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes()))
                )
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            privateFile.writeBytes(pair.private.encoded)
            publicFile.writeBytes(pair.public.encoded)
            return pair
        }

        /** Android's adb_keys public-key wire format (not an X.509 PEM key). */
        internal fun androidPublicKey(key: RSAPublicKey): String {
            val words = 64
            val modulus = key.modulus
            val radix = wordsModulus()
            val n0inv = modulus.mod(radix).modInverse(radix).negate().mod(radix).toLong()
            val rr = BigInteger.ONE.shiftLeft(4096).mod(modulus)
                .multiply(BigInteger.ONE.shiftLeft(4096).mod(modulus)).mod(modulus)
            val buffer = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(words)
            buffer.putInt(n0inv.toInt())
            buffer.put(toLittleEndian(modulus, 256))
            buffer.put(toLittleEndian(rr, 256))
            buffer.putInt(key.publicExponent.toInt())
            return Base64.getEncoder().encodeToString(buffer.array()) + " tvremote"
        }

        private fun wordsModulus() = BigInteger.ONE.shiftLeft(32)
        private fun toLittleEndian(value: BigInteger, size: Int): ByteArray {
            val big = value.toByteArray()
            val unsigned = if (big.size > 1 && big[0] == 0.toByte()) big.copyOfRange(1, big.size) else big
            if (unsigned.size > size) throw IllegalArgumentException("RSA 數值超出長度")
            return ByteArray(size).also { out ->
                for (i in unsigned.indices) out[i] = unsigned[unsigned.lastIndex - i]
            }
        }
    }
}
