package com.chi.tvremote.protocol

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.math.BigInteger
import javax.net.ssl.SSLSocket

class PairingClient(private val certs: CertManager, private val tls: TlsFactory) {
    enum class State {
        Idle, Connecting, WaitRequestAck, WaitOptionAck, WaitConfigAck,
        WaitCode, WaitSecretAck, Paired, Failed
    }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val pairingLock = Any()
    @Volatile private var socket: SSLSocket? = null
    private var job: Job? = null
    // Invalidates callbacks and cleanup from an older pairing attempt.
    @Volatile private var pairingGeneration = 0
    // A code belongs to exactly one pairing attempt; never carry it into a retry.
    private var codeWaiter: CompletableDeferred<String>? = null

    fun startPairing(scope: CoroutineScope, host: String) {
        val generation = synchronized(pairingLock) {
            if (_state.value in setOf(State.Connecting, State.WaitRequestAck, State.WaitOptionAck,
                    State.WaitConfigAck, State.WaitCode, State.WaitSecretAck)) return
            val next = pairingGeneration + 1
            pairingGeneration = next
            _error.value = null
            _state.value = State.Connecting
            next
        }
        val pairingJob = scope.launch(Dispatchers.IO) {
            var attemptSocket: SSLSocket? = null
            try {
                val s = tls.createSocket(host, 6467, allowUnpinned = true)
                attemptSocket = s
                val accepted = synchronized(pairingLock) {
                    if (generation != pairingGeneration) false else {
                        socket = s
                        true
                    }
                }
                if (!accepted) return@launch

                val serverCert = s.session.peerCertificates.first() as X509Certificate
                val clientPublicKey = certs.certificate!!.publicKey as? RSAPublicKey
                    ?: error("手機憑證不是 RSA 公鑰")
                val serverPublicKey = serverCert.publicKey as? RSAPublicKey
                    ?: error("電視憑證不是 RSA 公鑰")
                val out = s.outputStream
                val input = s.inputStream

                updateState(generation, State.WaitRequestAck)
                write(out, PairingProtocol.envelope(10, PairingProtocol.request("CHI TV Remote")))
                readAck(input, expectedType = 11, failureMessage = "電視未回應配對要求")
                ensureCurrent(generation)

                updateState(generation, State.WaitOptionAck)
                write(out, PairingProtocol.envelope(20, PairingProtocol.options()))
                readAck(input, expectedType = 21, failureMessage = "電視不支援配對選項")
                ensureCurrent(generation)

                updateState(generation, State.WaitConfigAck)
                write(out, PairingProtocol.envelope(30, PairingProtocol.configuration()))
                readAck(input, expectedType = 31, failureMessage = "電視配對設定失敗")
                ensureCurrent(generation)

                val code = awaitPairingCode(generation).trim().uppercase()
                require(code.length == 6 && code.all { it in "0123456789ABCDEF" }) {
                    "配對碼必須是 6 位十六進位數字"
                }
                ensureCurrent(generation)

                updateState(generation, State.WaitSecretAck)
                val clientModulus = unsignedBytes(clientPublicKey.modulus)
                val clientExponent = unsignedBytes(clientPublicKey.publicExponent)
                val serverModulus = unsignedBytes(serverPublicKey.modulus)
                val serverExponent = unsignedBytes(serverPublicKey.publicExponent)
                val digest = PairingProtocol.pinSecret(
                    clientModulus, clientExponent, serverModulus, serverExponent, code
                )
                write(out, PairingProtocol.envelope(40, ProtoCodec.bytesField(1, digest)))
                readAck(input, expectedType = 41, failureMessage = "配對碼錯誤或電視拒絕配對")
                ensureCurrent(generation)

                certs.pinServer(host, serverCert)
                updateState(generation, State.Paired)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                synchronized(pairingLock) {
                    if (generation == pairingGeneration) {
                        Log.e("TVRemote", "pairing failed", t)
                        _error.value = t.message ?: "配對失敗"
                        _state.value = State.Failed
                    }
                }
            } finally {
                try { attemptSocket?.close() } catch (_: Throwable) {}
                synchronized(pairingLock) {
                    if (socket === attemptSocket) socket = null
                    if (generation == pairingGeneration && _state.value == State.Connecting) {
                        _state.value = State.Idle
                    }
                }
            }
        }
        synchronized(pairingLock) {
            if (generation == pairingGeneration) job = pairingJob
            else pairingJob.cancel()
        }
    }

    private fun updateState(generation: Int, state: State) {
        synchronized(pairingLock) {
            if (generation == pairingGeneration) _state.value = state
            else throw CancellationException("配對工作已取消")
        }
    }

    private fun ensureCurrent(generation: Int) {
        if (generation != pairingGeneration) throw CancellationException("配對工作已取消")
    }

    fun submitCode(code: String) {
        synchronized(pairingLock) {
            if (_state.value == State.WaitCode) codeWaiter?.complete(code)
        }
    }

    private suspend fun awaitPairingCode(generation: Int): String {
        val waiter = synchronized(pairingLock) {
            if (generation != pairingGeneration) throw CancellationException("配對工作已取消")
            CompletableDeferred<String>().also {
                codeWaiter = it
                _state.value = State.WaitCode
            }
        }
        return try {
            waiter.await()
        } finally {
            synchronized(pairingLock) {
                if (codeWaiter === waiter) codeWaiter = null
            }
        }
    }

    fun cancel() {
        synchronized(pairingLock) {
            pairingGeneration++
            job?.cancel()
            job = null
            codeWaiter?.cancel()
            codeWaiter = null
            try { socket?.close() } catch (_: Throwable) {}
            socket = null
            _error.value = null
            _state.value = State.Idle
        }
    }

    private suspend fun write(out: OutputStream, bytes: ByteArray) = withContext(Dispatchers.IO) {
        out.write(ProtoCodec.frame(bytes)); out.flush()
    }

    private fun readVarint(input: InputStream): Long {
        var value = 0L
        var shift = 0
        while (shift <= 63) {
            val b = input.read()
            if (b < 0) error("電視已中斷連線")
            value = value or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
        }
        error("配對封包格式錯誤")
    }

    private suspend fun readAck(input: InputStream, expectedType: Int, failureMessage: String) =
        withContext(Dispatchers.IO) {
            val length = readVarint(input).toInt()
            require(length in 1..65536) { "配對回應長度錯誤" }
            val payload = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(payload, offset, length - offset)
                if (count < 0) error("電視已中斷連線")
                offset += count
            }
            val ack = PairingProtocol.parseAck(payload)
            check(ack.hasMessage(expectedType)) { failureMessage }
        }

    private fun unsignedBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }
}
