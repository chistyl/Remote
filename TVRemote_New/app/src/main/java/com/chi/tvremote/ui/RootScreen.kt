package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chi.tvremote.RemoteViewModel
import com.chi.tvremote.protocol.KeyCodes

private val AppBackground = Color(0xFF080D16)
private val HeaderSurface = Color(0xFF121B29)
private val HeaderBorder = Color(0xFF26364A)
private val AccentBlue = Color(0xFF67D7FF)

@Composable
fun RootScreen(vm: RemoteViewModel) {
    var tab by remember { mutableIntStateOf(1) }
    var settingsMenu by remember { mutableStateOf(false) }
    val language by vm.language.collectAsState()
    val connected by vm.remote.connected.collectAsState()
    val ready by vm.remote.ready.collectAsState()
    val host by vm.host.collectAsState()
    val devices by vm.devices.collectAsState()
    val savedDevices by vm.savedDevices.collectAsState()
    val deviceName = devices.firstOrNull { it.host == host }?.name
        ?: savedDevices.firstOrNull { it.host == host }?.name
        ?: "Android TV"

    CompositionLocalProvider(LocalAppLanguage provides language) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().height(72.dp).background(AppBackground)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        Surface(
                            onClick = { settingsMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            color = HeaderSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, HeaderBorder),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, contentDescription = "設定",
                                    tint = Color(0xFFDCE8F5), modifier = Modifier.size(23.dp))
                            }
                        }
                        DropdownMenu(expanded = settingsMenu, onDismissRequest = { settingsMenu = false }) {
                            DropdownMenuItem(text = { Text("配對／連線設定") }, onClick = { tab = 0; settingsMenu = false })
                            DropdownMenuItem(text = { Text("繁體中文") }, onClick = { vm.setLanguage("zh-TW"); settingsMenu = false })
                            DropdownMenuItem(text = { Text("English") }, onClick = { vm.setLanguage("en"); settingsMenu = false })
                            DropdownMenuItem(text = { Text("Tiếng Việt") }, onClick = { vm.setLanguage("vi"); settingsMenu = false })
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = HeaderSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, HeaderBorder)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(9.dp).background(
                                    if (ready) Color(0xFF43D17A) else if (connected) Color(0xFFFFC45C) else Color(0xFFE85B52),
                                    CircleShape
                                )
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                Text(deviceName, color = Color.White,
                                    style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    if (ready) t("connected") else if (connected) t("connecting") else t("disconnected"),
                                    color = if (ready) Color(0xFF75DFA0) else Color(0xFF9AA9BA),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1
                                )
                            }
                            Text("TV", color = AccentBlue, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(
                        onClick = { if (ready) vm.sendKey(KeyCodes.POWER) },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF321D25),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF67313D)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "電源",
                                tint = Color(0xFFFF7777), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> PairingScreen(vm, onPaired = { vm.rememberCurrentTv(); vm.connect(); tab = 1 },
                        onConnectSaved = { address -> vm.reconnectTo(address); tab = 1 })
                    else -> TwoPageRemoteScreen(vm)
                }
            }
        }
    }
}
