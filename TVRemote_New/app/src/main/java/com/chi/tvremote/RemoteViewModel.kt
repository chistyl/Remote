package com.chi.tvremote

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chi.tvremote.protocol.CertManager
import com.chi.tvremote.protocol.PairingClient
import com.chi.tvremote.protocol.RemoteClient
import com.chi.tvremote.protocol.ServiceResolveEpoch
import com.chi.tvremote.protocol.TlsFactory
import com.chi.tvremote.adb.AdbTransport
import com.chi.tvremote.adb.AdbDuplexChannel
import com.chi.tvremote.adb.ScrcpyControlProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.asStateFlow


class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tvremote", 0)
    private val certs = CertManager(app).also { it.ensureLoaded() }
    private val tls = TlsFactory(certs)
    val pairing = PairingClient(certs, tls)
    val remote = RemoteClient(tls)
    private val textEditMutex = Mutex()

    private var adbTransport: AdbTransport? = null
    private var scrcpyMouseChannel: AdbDuplexChannel? = null
    private val adbMutex = Mutex()
    @Volatile private var adbGeneration = 0L
    private val adbPendingSocket = AtomicReference<Socket?>(null)
    private val _adbStatus = MutableStateFlow("尚未授權")
    val adbStatus: StateFlow<String> = _adbStatus.asStateFlow()
    private val _adbConnecting = MutableStateFlow(false)
    val adbConnecting: StateFlow<Boolean> = _adbConnecting.asStateFlow()

    /** Authenticates with classic ADB TCP. This is separate from Android TV Remote pairing. */
    fun connectAdb() {
        val address = _host.value.trim()
        if (address.isBlank()) { _adbStatus.value = "請先選擇電視"; return }
        if (_adbConnecting.value) return
        val generation = ++adbGeneration
        viewModelScope.launch {
            _adbConnecting.value = true
            _adbStatus.value = "正在連線並等待電視授權…"
            try {
                val result = withContext(Dispatchers.IO) {
                    adbMutex.withLock {
                        adbTransport?.close()
                        adbTransport = null
                        AdbTransport.connect(getApplication<Application>().filesDir, address) { socket ->
                            adbPendingSocket.set(socket)
                            if (generation != adbGeneration) socket.close()
                        }
                    }
                }
                adbMutex.withLock {
                    adbPendingSocket.set(null)
                    if (generation == adbGeneration) adbTransport = result.transport
                    else result.transport.close()
                }
                if (generation == adbGeneration) {
                    _adbStatus.value = if (result.authorizationRequested) "ADB 已授權連線" else "ADB 已連線"
                }
            } catch (e: Exception) {
                if (generation == adbGeneration) _adbStatus.value = e.message ?: "ADB 連線失敗"
            } finally {
                if (generation == adbGeneration) _adbConnecting.value = false
            }
        }
    }

    fun disconnectAdb() {
        ++adbGeneration
        try { adbPendingSocket.getAndSet(null)?.close() } catch (_: Exception) {}
        viewModelScope.launch(Dispatchers.IO) {
            adbMutex.withLock {
                try { adbTransport?.close() } catch (_: Exception) {}
                adbTransport = null
            }
            _adbConnecting.value = false
            _adbStatus.value = "ADB 已中斷"
        }
    }
    /** Verifies the bundled official scrcpy server and pushes it to the authorized TV. */
    fun installScrcpyServer() {
        if (_adbConnecting.value) return
        viewModelScope.launch {
            _adbStatus.value = "正在驗證並部署 scrcpy Server…"
            try {
                withContext(Dispatchers.IO) {
                    adbMutex.withLock {
                        val transport = adbTransport ?: throw java.io.IOException("請先完成 ADB 連線與電視授權")
                        val server = com.chi.tvremote.adb.ScrcpyServerAsset.extractVerified(getApplication<Application>())
                        transport.pushFile(server, com.chi.tvremote.adb.ScrcpyServerAsset.REMOTE_PATH, mode = 0x1a4, timeoutMs = 30000)
                    }
                }
                _adbStatus.value = "scrcpy Server 5.0.1 已部署；尚未啟動滑鼠服務"
            } catch (e: Exception) {
                _adbStatus.value = e.message ?: "scrcpy Server 部署失敗"
            }
        }
    }
    /**
     * Starts scrcpy in control-only mode and creates its official UHID mouse.
     * Requires ADB authorization and a TV kernel exposing /dev/uhid.
     */
    fun startScrcpyMouseService() {
        if (_adbConnecting.value) return
        viewModelScope.launch {
            _adbStatus.value = "正在啟動電視端滑鼠服務…"
            try {
                withContext(Dispatchers.IO) {
                    adbMutex.withLock {
                        val transport = adbTransport ?: throw java.io.IOException("請先完成 ADB 連線與電視授權")
                        scrcpyMouseChannel?.close()
                        scrcpyMouseChannel = null
                        val scid = ((System.currentTimeMillis() and 0x7fffffffL).toInt()).coerceAtLeast(1)
                        val scidHex = "%08x".format(java.util.Locale.ROOT, scid)
                        val command = "CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 5.0.1 scid=$scidHex log_level=info video=false audio=false control=true tunnel_forward=false send_dummy_byte=false send_device_meta=false send_stream_meta=false cleanup=true >/dev/null 2>&1 &"
                        transport.shell(command, timeoutMs = 10000)
                        val socketName = "scrcpy_$scidHex"
                        var channel: AdbDuplexChannel? = null
                        var lastError: Exception? = null
                        repeat(8) {
                            if (channel == null) {
                                try { channel = transport.openLocalAbstract(socketName, timeoutMs = 2500) }
                                catch (e: Exception) { lastError = e; Thread.sleep(350) }
                            }
                        }
                        val opened = channel ?: throw java.io.IOException("scrcpy 控制通道無法建立：${lastError?.message ?: "電視端服務未回應"}", lastError)
                        try {
                            opened.output.write(ScrcpyControlProtocol.mouseUhidCreate())
                            opened.output.flush()
                            scrcpyMouseChannel = opened
                        } catch (e: Exception) { opened.close(); throw e }
                    }
                }
                _adbStatus.value = "滑鼠服務已啟動；若電視核心不支援 UHID，將無法建立滑鼠裝置"
            } catch (e: Exception) { _adbStatus.value = e.message ?: "滑鼠服務啟動失敗" }
        }
    }

    fun stopScrcpyMouseService() {
        viewModelScope.launch(Dispatchers.IO) {
            adbMutex.withLock {
                try {
                    scrcpyMouseChannel?.output?.write(ScrcpyControlProtocol.uhidDestroy(ScrcpyControlProtocol.HID_ID_MOUSE))
                    scrcpyMouseChannel?.close()
                } catch (_: Exception) {
                } finally { scrcpyMouseChannel = null }
            }
            _adbStatus.value = "滑鼠服務已停止"
        }
    }

    /** Sends a relative HID mouse report after the UHID device has been created. */
    fun sendScrcpyMouseReport(buttons: Int = 0, dx: Int = 0, dy: Int = 0, wheel: Int = 0, horizontal: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            adbMutex.withLock {
                val channel = scrcpyMouseChannel ?: return@withLock
                try {
                    channel.output.write(ScrcpyControlProtocol.mouseUhidInput(buttons, dx, dy, wheel, horizontal))
                    channel.output.flush()
                } catch (e: Exception) {
                    _adbStatus.value = e.message ?: "滑鼠資料傳送失敗"
                    try { channel.close() } catch (_: Exception) {}
                    scrcpyMouseChannel = null
                }
            }
        }
    }
    /** Runs a diagnostic shell command only after successful ADB authorization. */
    fun runAdbDiagnostic(command: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    adbMutex.withLock {
                        val transport = adbTransport ?: throw java.io.IOException("ADB 尚未授權連線")
                        transport.shell(command)
                    }
                }
            } catch (e: Exception) {
                _adbStatus.value = e.message ?: "ADB 指令失敗"
                null
            }
            onResult(result)
        }
    }

    private val _adbProbeStatus = MutableStateFlow("尚未檢查")
    val adbProbeStatus: StateFlow<String> = _adbProbeStatus.asStateFlow()
    private val _adbProbeRunning = MutableStateFlow(false)
    val adbProbeRunning: StateFlow<Boolean> = _adbProbeRunning.asStateFlow()

    /** Checks whether the selected TV exposes the classic ADB TCP port. This does not authenticate or start ADB. */
    fun checkAdbTcpPort() {
        val address = _host.value.trim()
        if (address.isBlank()) {
            _adbProbeStatus.value = "請先選擇或連線電視"
            return
        }
        if (_adbProbeRunning.value) return
        viewModelScope.launch {
            _adbProbeRunning.value = true
            _adbProbeStatus.value = "正在檢查 $address:5555…"
            val reachable = withContext(Dispatchers.IO) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(address, 5555), 1800)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            _adbProbeStatus.value = if (reachable) {
                "TCP 5555 可連線；尚需確認 ADB 授權與裝置相容性"
            } else {
                "無法連線 TCP 5555；請確認電視已開啟 ADB 網路除錯，且手機與電視在同一網路"
            }
            _adbProbeRunning.value = false
        }
    }

    private val _language = MutableStateFlow(prefs.getString("language", "zh-TW") ?: "zh-TW")
    val language: StateFlow<String> = _language

    fun setLanguage(value: String) {
        val normalized = value.takeIf { it in setOf("zh-TW", "en", "vi") } ?: "zh-TW"
        _language.value = normalized
        prefs.edit().putString("language", normalized).apply()
    }

    private val _host = MutableStateFlow(prefs.getString("host", "") ?: "")
    val host: StateFlow<String> = _host

    private val _sensitivity = MutableStateFlow(1.0f)
    val sensitivity: StateFlow<Float> = _sensitivity

    private val _holdRepeatEnabled = MutableStateFlow(prefs.getBoolean("hold_repeat", true))
    val holdRepeatEnabled: StateFlow<Boolean> = _holdRepeatEnabled.asStateFlow()

    fun setHoldRepeatEnabled(value: Boolean) {
        _holdRepeatEnabled.value = value
        prefs.edit().putBoolean("hold_repeat", value).apply()
    }

    private val _remoteControlMode = MutableStateFlow(prefs.getInt("remote_control_mode", 0).coerceIn(0, 1))
    val remoteControlMode: StateFlow<Int> = _remoteControlMode.asStateFlow()

    fun setRemoteControlMode(value: Int) {
        val normalized = value.coerceIn(0, 1)
        _remoteControlMode.value = normalized
        prefs.edit().putInt("remote_control_mode", normalized).apply()
    }

    data class TvDevice(val name: String, val host: String)
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices

    private fun decodeSavedDevices(): List<TvDevice> = prefs.getStringSet("saved_hosts", emptySet())
        .orEmpty().mapNotNull { address ->
            val name = prefs.getString("tv_name_$address", null) ?: "Android TV"
            if (address.isBlank()) null else TvDevice(name, address)
        }.sortedBy { it.name.lowercase() }

    private val _savedDevices = MutableStateFlow(decodeSavedDevices())
    val savedDevices: StateFlow<List<TvDevice>> = _savedDevices

    fun rememberCurrentTv() {
        val address = _host.value.trim()
        if (address.isBlank()) return
        val discoveredName = _devices.value.firstOrNull { it.host == address }?.name
        val existingName = _savedDevices.value.firstOrNull { it.host == address }?.name
        val name = discoveredName ?: existingName ?: "Android TV ($address)"
        val hosts = prefs.getStringSet("saved_hosts", emptySet()).orEmpty().toMutableSet()
        hosts.add(address)
        prefs.edit().putStringSet("saved_hosts", hosts)
            .putString("tv_name_$address", name).apply()
        _savedDevices.value = decodeSavedDevices()
    }

    fun forgetTv(host: String) {
        val hosts = prefs.getStringSet("saved_hosts", emptySet()).orEmpty().toMutableSet()
        hosts.remove(host)
        prefs.edit().putStringSet("saved_hosts", hosts).remove("tv_name_$host").apply()
        _savedDevices.value = decodeSavedDevices()
    }

    fun reconnectTo(host: String) {
        setHost(host)
        connect()
    }
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    // Android NSD resolves one service at a time on Android 9; queue discoveries
    // instead of silently dropping every service found during an active resolve.
    private val pendingServices = ArrayDeque<NsdServiceInfo>()
    // Prevent a late resolve callback from resurrecting a service already lost.
    private val lostServiceNames = mutableSetOf<String>()
    // A loss epoch prevents an old resolve callback from becoming valid again
    // if the same mDNS service name is rediscovered before that callback returns.
    private val serviceResolveEpoch = ServiceResolveEpoch()
    private var resolvingService = false
    private var discoveryGeneration = 0

    @Suppress("DEPRECATION")
    fun startDiscovery() {
        if (discovery != null) return
        _devices.value = emptyList()
        multicast = wifi.createMulticastLock("tvremote-mdns").apply { setReferenceCounted(false); acquire() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("androidtvremote", ignoreCase = true)) return
                lostServiceNames.remove(serviceInfo.serviceName)
                val alreadyQueued = pendingServices.any { it.serviceName == serviceInfo.serviceName }
                val alreadyResolved = _devices.value.any { it.name == serviceInfo.serviceName }
                if (!alreadyQueued && !alreadyResolved) {
                    pendingServices.addLast(serviceInfo)
                    resolveNextService()
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                serviceResolveEpoch.markLost(serviceInfo.serviceName)
                lostServiceNames.add(serviceInfo.serviceName)
                pendingServices.removeAll { it.serviceName == serviceInfo.serviceName }
                _devices.value = _devices.value.filterNot { it.name == serviceInfo.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { stopDiscovery() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stopDiscovery() }
        }
        discoveryGeneration++
        pendingServices.clear()
        lostServiceNames.clear()
        serviceResolveEpoch.clear()
        resolvingService = false
        discovery = listener
        try { nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { stopDiscovery() }
    }

    @Suppress("DEPRECATION")
    fun stopDiscovery() {
        val listener = discovery
        discovery = null
        discoveryGeneration++
        pendingServices.clear()
        lostServiceNames.clear()
        serviceResolveEpoch.clear()
        resolvingService = false
        if (listener != null) try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        try { multicast?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        multicast = null
    }


    @Suppress("DEPRECATION")
    private fun resolveNextService() {
        if (resolvingService || discovery == null || pendingServices.isEmpty()) return
        val service = pendingServices.removeFirst()
        val generation = discoveryGeneration
        val serviceEpoch = serviceResolveEpoch.snapshot(service.serviceName)
        resolvingService = true
        try {
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    if (generation != discoveryGeneration) return
                    resolvingService = false
                    resolveNextService()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    if (generation != discoveryGeneration) return
                    val stillDiscovered = discovery != null &&
                        info.serviceName !in lostServiceNames &&
                        serviceResolveEpoch.isCurrent(info.serviceName, serviceEpoch)
                    val address = info.host?.hostAddress
                    if (stillDiscovered && !address.isNullOrBlank()) {
                        val device = TvDevice(info.serviceName, address)
                        _devices.value = (_devices.value.filterNot { it.host == address } + device)
                    }
                    resolvingService = false
                    resolveNextService()
                }
            })
        } catch (_: Exception) {
            if (generation != discoveryGeneration) return
            resolvingService = false
            resolveNextService()
        }
    }

    fun setHost(value: String) {
        val normalized = value.trim()
        if (normalized != _host.value && (adbTransport != null || _adbConnecting.value)) {
            disconnectAdb()
        }
        _host.value = normalized
        prefs.edit().putString("host", normalized).apply()
    }

    fun startPairing() {
        val h = _host.value.trim()
        if (h.isNotEmpty()) pairing.startPairing(viewModelScope, h)
    }

    fun submitCode(code: String) {
        pairing.submitCode(code)
    }

    fun cancelPairing() = pairing.cancel()

    fun connect() {
        val h = _host.value.trim()
        if (h.isNotEmpty()) remote.connect(viewModelScope, h)
    }

    fun disconnect() = remote.disconnect()

    fun sendKey(code: Int) {
        viewModelScope.launch { remote.sendKey(code) }
    }

    fun sendLongKey(code: Int) {
        viewModelScope.launch { remote.sendLongKey(code) }
    }

    fun sendText(value: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(remote.sendText(value)) }
    }

    /** Sends only committed text changes from the phone IME, preserving TV-side cursor order. */
    fun sendRealtimeTextDelta(previous: String, current: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val sent = textEditMutex.withLock {
                if (!remote.ready.value || !remote.textInputReady.value) return@withLock false
                when {
                    current.startsWith(previous) -> {
                        val added = current.substring(previous.length)
                        added.isEmpty() || remote.sendText(added)
                    }
                    previous.startsWith(current) -> {
                        val removed = previous.codePointCount(0, previous.length) - current.codePointCount(0, current.length)
                        repeat(removed) { remote.sendKey(com.chi.tvremote.protocol.KeyCodes.DEL) }
                        true
                    }
                    else -> {
                        repeat(previous.codePointCount(0, previous.length)) { remote.sendKey(com.chi.tvremote.protocol.KeyCodes.DEL) }
                        current.isEmpty() || remote.sendText(current)
                    }
                }
            }
            onResult(sent)
        }
    }

    fun setSensitivity(value: Float) { _sensitivity.value = value.coerceIn(0.5f, 2.0f) }

    override fun onCleared() {
        remote.disconnect()
        try { adbPendingSocket.getAndSet(null)?.close() } catch (_: Exception) {}
        try { scrcpyMouseChannel?.close() } catch (_: Exception) {}
        scrcpyMouseChannel = null
        try { adbTransport?.close() } catch (_: Exception) {}
        adbTransport = null
        pairing.cancel()
        stopDiscovery()
        super.onCleared()
    }
}
