package com.chi.tvremote.protocol

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.net.ssl.SSLSocket

/** Android TV Remote v2 client with reconnect/backoff and TV-driven session setup. */
class RemoteClient(private val tls: TlsFactory) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _textInputReady = MutableStateFlow(false)
    val textInputReady: StateFlow<Boolean> = _textInputReady
    data class VolumeInfo(val level: Int, val max: Int, val muted: Boolean) {
        val percent: Int get() = ProtoCodec.VolumeSnapshot(level, max, muted).percent
    }
    private val _volumeInfo = MutableStateFlow<VolumeInfo?>(null)
    val volumeInfo: StateFlow<VolumeInfo?> = _volumeInfo
    private val _powerOn = MutableStateFlow<Boolean?>(null)
    val powerOn: StateFlow<Boolean?> = _powerOn
    private val _voiceSupported = MutableStateFlow(false)
    val voiceSupported: StateFlow<Boolean> = _voiceSupported
    @Volatile private var imeCounter = -1
    @Volatile private var imeFieldCounter = -1
    @Volatile private var pendingVoice: CompletableDeferred<Int>? = null
    @Volatile private var activeVoiceSession: Int? = null

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var shouldReconnect = false
    @Volatile private var connectionHost: String? = null
    @Volatile private var connectionGeneration = 0
    private var connectionJob: Job? = null
    private val writeMutex = Mutex()
    // Serializes connection generations, socket publication and disconnect cleanup.
    private val connectionLock = Any()
    // PING | KEY | IME | VOICE | POWER | VOLUME. Only implemented features are requested.
    private val wantedFeatures = 1L or 2L or 4L or 8L or 32L or 64L
    @Volatile private var activeFeatures = wantedFeatures

    fun connect(scope: CoroutineScope, host: String) {
        val targetHost = host.trim()
        if (targetHost.isBlank()) return

        val generation = synchronized(connectionLock) {
            if (connectionJob?.isActive == true && connectionHost == targetHost) return
            disconnectLocked()
            val next = connectionGeneration
            connectionHost = targetHost
            shouldReconnect = true
            _error.value = null
            next
        }

        val newJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (currentCoroutineContext().isActive && isCurrentConnection(generation)) {
                var current: SSLSocket? = null
                try {
                    setErrorIfCurrent(generation,
                        if (attempt == 0) "正在連接電視…" else "連線中斷，正在重新連接（第 $attempt 次）…")
                    current = tls.createSocket(targetHost, 6466)

                    // A disconnect or TV switch may happen while TLS connect/handshake
                    // is blocking. Never publish a socket from an obsolete generation.
                    val accepted = synchronized(connectionLock) {
                        if (generation != connectionGeneration || !shouldReconnect) false
                        else {
                            socket = current
                            _connected.value = true
                            _ready.value = false
                            _textInputReady.value = false
                            imeCounter = -1
                            imeFieldCounter = -1
                            true
                        }
                    }
                    if (!accepted) {
                        try { current.close() } catch (_: Throwable) {}
                        break
                    }

                    readLoop(current)
                    if (isCurrentConnection(generation) && currentCoroutineContext().isActive) {
                        setErrorIfCurrent(generation, "電視已中斷連線，準備重新連接…")
                    }
                } catch (t: Throwable) {
                    if (currentCoroutineContext().isActive && isCurrentConnection(generation)) {
                        Log.w("TVRemote", "remote connection attempt failed", t)
                        setErrorIfCurrent(generation,
                            t.message?.takeIf { it.isNotBlank() } ?: "連線失敗，正在重試…")
                    }
                } finally {
                    try { current?.close() } catch (_: Throwable) {}
                    synchronized(connectionLock) {
                        if (socket === current) socket = null
                        if (generation == connectionGeneration) resetConnectionStateLocked()
                    }
                }
                if (!isCurrentConnection(generation) || !currentCoroutineContext().isActive) break
                attempt++
                val waitSeconds = minOf(30L, 1L shl minOf(attempt - 1, 5))
                delay(waitSeconds * 1000L)
            }
        }
        synchronized(connectionLock) {
            if (generation == connectionGeneration && shouldReconnect) connectionJob = newJob
            else newJob.cancel()
        }
    }

    fun disconnect() {
        synchronized(connectionLock) { disconnectLocked() }
    }

    private fun disconnectLocked() {
        connectionGeneration++
        shouldReconnect = false
        connectionHost = null
        connectionJob?.cancel()
        connectionJob = null
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        resetConnectionStateLocked()
        _error.value = null
    }

    private fun resetConnectionStateLocked() {
        _connected.value = false
        _ready.value = false
        _textInputReady.value = false
        _volumeInfo.value = null
        _powerOn.value = null
        _voiceSupported.value = false
        // A newly connected TV must renegotiate capabilities; never carry the
        // previous TV's feature mask across disconnects or reconnects.
        activeFeatures = wantedFeatures
        pendingVoice?.cancel()
        pendingVoice = null
        activeVoiceSession = null
        imeCounter = -1
        imeFieldCounter = -1
    }

    private fun isCurrentConnection(generation: Int): Boolean =
        synchronized(connectionLock) {
            generation == connectionGeneration && shouldReconnect
        }

    private fun setErrorIfCurrent(generation: Int, value: String) {
        synchronized(connectionLock) {
            if (generation == connectionGeneration && shouldReconnect) _error.value = value
        }
    }

    suspend fun sendKey(code: Int) = sendKeyWithDirection(code, 3)

    suspend fun sendLongKey(code: Int) {
        sendKeyWithDirection(code, 1)
        delay(550)
        sendKeyWithDirection(code, 2)
    }

    /** Sends text to the currently focused TV input field via Android TV Remote IME. */
    suspend fun sendText(text: String): Boolean {
        val value = text
        if (value.isEmpty() || !_ready.value || !_textInputReady.value) return false
        val ime = imeCounter
        val field = imeFieldCounter
        if (ime < 0 || field < 0) return false
        return try {
            writeMessage(21, ProtoCodec.imeBatchEditBody(value, ime, field))
            true
        } catch (t: Throwable) {
            _error.value = t.message?.takeIf { it.isNotBlank() } ?: "文字傳送失敗"
            false
        }
    }

    /** Starts the TV's native voice-assistant session. The TV supplies the session id. */
    suspend fun beginVoice(): Int {
        check(_ready.value) { "遙控器尚未連線完成" }
        check(activeFeatures and 8L != 0L) { "電視不支援語音控制" }
        check(activeVoiceSession == null && pendingVoice == null) { "語音工作階段已啟動" }
        val waiter = CompletableDeferred<Int>()
        pendingVoice = waiter
        try {
            sendKey(KeyCodes.SEARCH)
            val sessionId = withTimeout(2_500L) { waiter.await() }
            writeMessage(30, ProtoCodec.voiceSessionBody(sessionId))
            activeVoiceSession = sessionId
            return sessionId
        } finally {
            if (pendingVoice === waiter) pendingVoice = null
        }
    }

    /** Sends 8–20 KB of 16-bit mono PCM samples at 8 kHz to the TV. */
    suspend fun sendVoiceChunk(sessionId: Int, samples: ByteArray) {
        check(activeVoiceSession == sessionId) { "語音工作階段已結束" }
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(offset + 20_480, samples.size)
            val data = samples.copyOfRange(offset, end)
            val padded = if (data.size < 8_192) data.copyOf(8_192) else data
            writeMessage(31, ProtoCodec.voicePayloadBody(sessionId, padded))
            offset = end
        }
    }

    suspend fun endVoice(sessionId: Int) {
        if (activeVoiceSession != sessionId) return
        try { writeMessage(32, ProtoCodec.voiceSessionBody(sessionId)) }
        finally { activeVoiceSession = null }
    }

    private suspend fun sendKeyWithDirection(code: Int, direction: Int) {
        if (!_ready.value) return
        val body = ProtoCodec.varintField(1, code.toLong()) +
            ProtoCodec.varintField(2, direction.toLong())
        writeMessage(10, body)
    }

    private suspend fun readLoop(s: SSLSocket) {
        val input = s.inputStream
        var buffer = ByteArray(0)
        val chunk = ByteArray(4096)
        while (currentCoroutineContext().isActive && shouldReconnect) {
            val n = withContext(Dispatchers.IO) { input.read(chunk) }
            if (n < 0) break
            buffer += chunk.copyOf(n)
            if (buffer.size > 1_048_586) throw IllegalArgumentException("遙控封包超過安全上限")
            while (true) {
                val frame = ProtoCodec.tryUnframe(buffer) ?: break
                buffer = buffer.copyOfRange(frame.first, buffer.size)
                handleMessage(frame.second, s)
            }
        }
    }

    private suspend fun handleMessage(payload: ByteArray, sourceSocket: SSLSocket) {
        if (socket !== sourceSocket) return
        val cursor = intArrayOf(0)
        val tag = ProtoCodec.decodeVarint(payload, cursor)
        val field = (tag shr 3).toInt()
        if ((tag and 7L).toInt() != 2) return
        val length = ProtoCodec.decodeVarint(payload, cursor).toInt()
        if (length < 0 || cursor[0] + length > payload.size) return
        val body = payload.copyOfRange(cursor[0], cursor[0] + length)
        when (field) {
            1 -> { // RemoteConfigure: TV advertises its supported feature bitmask.
                val tvFeatures = ProtoCodec.firstVarintField(body, 1).let { if (it < 0) 0L else it }
                activeFeatures = tvFeatures and wantedFeatures
                _voiceSupported.value = activeFeatures and 8L != 0L
                if (activeFeatures and 2L == 0L) {
                    _error.value = "電視未提供按鍵控制功能"
                    return
                }
                val deviceInfo = ProtoCodec.varintField(3, 1) +
                    ProtoCodec.stringField(4, "1") +
                    ProtoCodec.stringField(5, "atvremote") +
                    ProtoCodec.stringField(6, "1.1.1")
                val response = ProtoCodec.varintField(1, activeFeatures) +
                    ProtoCodec.messageField(2, deviceInfo)
                writeMessageFor(sourceSocket, 1, response)
                _error.value = "電視已回應，正在啟用遙控功能…"
            }
            2 -> { // RemoteSetActive
                writeMessageFor(sourceSocket, 2, ProtoCodec.varintField(1, activeFeatures))
            }
            8 -> { // RemotePingRequest; echo the TV's token.
                val token = ProtoCodec.firstVarintField(body, 1).let { if (it < 0) 1L else it }
                writeMessageFor(sourceSocket, 9, ProtoCodec.varintField(1, token))
            }
            30 -> { // RemoteVoiceBegin: TV grants a voice session after KEYCODE_SEARCH.
                val sessionId = ProtoCodec.voiceSessionId(body)
                if (sessionId != null) pendingVoice?.complete(sessionId)
            }
            21 -> { // RemoteImeBatchEdit: TV supplies counters for subsequent text edits.
                val counters = ProtoCodec.imeCounters(body)
                if (counters != null) {
                    imeCounter = counters.first
                    imeFieldCounter = counters.second
                    _textInputReady.value = true
                }
            }
            20 -> { // RemoteImeKeyInject: a text field / app context is active.
                _textInputReady.value = imeCounter >= 0 && imeFieldCounter >= 0
            }
            22 -> { // RemoteImeShowRequest: keyboard is requested; wait for batch-edit counters.
                _textInputReady.value = imeCounter >= 0 && imeFieldCounter >= 0
            }
            3 -> {
                Log.w("TVRemote", "TV remote error: ${body.size} bytes")
                _error.value = "電視回報遙控通道錯誤"
            }
            40 -> { // RemoteStart: the control session is now usable.
                _ready.value = true
                _powerOn.value = ProtoCodec.powerState(body)
                _error.value = null
            }
            50 -> { // RemoteSetVolumeLevel: volume information is sent by the TV.
                ProtoCodec.volumeSnapshot(body)?.let { snapshot ->
                    _volumeInfo.value = VolumeInfo(snapshot.level, snapshot.max, snapshot.muted)
                }
            }
            else -> Log.d("TVRemote", "TV message field=$field")
        }
    }

    private suspend fun writeMessage(field: Int, body: ByteArray) {
        val s = socket ?: return
        writeFrame(s.outputStream, ProtoCodec.messageField(field, body))
    }

    private suspend fun writeMessageFor(sourceSocket: SSLSocket, field: Int, body: ByteArray) {
        // Responses to an old read loop must never be redirected to a newly
        // connected TV's socket after a rapid disconnect/reconnect.
        if (socket !== sourceSocket) return
        writeFrame(sourceSocket.outputStream, ProtoCodec.messageField(field, body))
    }

    private suspend fun writeFrame(out: OutputStream, bytes: ByteArray) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) { out.write(ProtoCodec.frame(bytes)); out.flush() }
        }
    }
}
