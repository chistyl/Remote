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
 * Minimal classic ADB-over-TCP transport and RSA authorization.
 *
 * This intentionally implements only the ADB transport handshake. Shell streams and
 * scrcpy UHID input are separate protocol layers and are not implied by a successful
 * connection here.
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

    @Synchronized
    private fun writePacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val checksum = payload.fold(0L) { sum, byte -> sum + (byte.toInt() and 0xff) }.toInt()
        output.writeInt(command)
        output.writeInt(arg0)
        output.writeInt(arg1)
        output.writeInt(payload.size)
        output.writeInt(checksum)
        output.writeInt(command xor -1)
        output.write(payload)
        output.flush()
    }

    private data class Packet(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    private fun readPacket(): Packet {
        val command = input.readInt()
        val arg0 = input.readInt()
        val arg1 = input.readInt()
        val length = input.readInt()
        val checksum = input.readInt()
        val magic = input.readInt()
        if (magic != (command xor -1)) throw IOException("ADB 封包標記錯誤")
        if (length < 0 || length > MAX_PAYLOAD) throw IOException("ADB 封包長度不合法：$length")
        val payload = ByteArray(length)
        input.readFully(payload)
        val actual = payload.fold(0L) { sum, byte -> sum + (byte.toInt() and 0xff) }.toInt()
        if (actual != checksum) throw IOException("ADB 封包 checksum 錯誤")
        return Packet(command, arg0, arg1, payload)
    }

    override fun close() {
        connected = false
        try { socket.close() } catch (_: Exception) { }
    }

    companion object {
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

        /**
         * Connects to a TV exposing legacy ADB TCP (normally port 5555).
         * On first use Android TV displays its own "Allow USB debugging?" dialog.
         */
        @Throws(IOException::class)
        fun connect(filesDir: File, host: String, port: Int = 5555): ConnectResult {
            require(host.isNotBlank()) { "電視 IP 不可空白" }
            val socket = Socket()
            try {
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
                while (System.currentTimeMillis() < deadline) {
                    val packet = try { transport.readPacket() } catch (e: java.net.SocketTimeoutException) {
                        if (publicKeySent) throw IOException("等待電視確認 ADB 授權逾時", e)
                        throw IOException("等待 ADB 電視回應逾時", e)
                    }
                    when (packet.command) {
                        A_CNXN -> {
                            socket.soTimeout = 0
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
                                socket.soTimeout = 1000
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
            val n0inv = BigInteger.ONE.shiftLeft(32).subtract(modulus.mod(wordsBits()))
                .mod(wordsModulus()).mod(wordsModulus()).let { BigInteger.ONE.shiftLeft(32).subtract(it).toLong() }
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

        private fun wordsBits() = BigInteger.ONE.shiftLeft(32)
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
