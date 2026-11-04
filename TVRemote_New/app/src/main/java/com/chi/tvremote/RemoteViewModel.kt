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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
        _host.value = value.trim()
        prefs.edit().putString("host", value.trim()).apply()
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
        pairing.cancel()
        stopDiscovery()
        super.onCleared()
    }
}
