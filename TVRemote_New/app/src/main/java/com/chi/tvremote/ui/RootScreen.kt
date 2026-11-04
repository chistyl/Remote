package com.chi.tvremote.ui

import androidx.compose.foundation.background
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
            containerColor = Color(0xFF1F1F1F),
            topBar = {
                Row(
                    Modifier.fillMaxWidth().height(76.dp).background(Color(0xFF1F1F1F)).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        IconButton(onClick = { settingsMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        DropdownMenu(expanded = settingsMenu, onDismissRequest = { settingsMenu = false }) {
                            DropdownMenuItem(text = { Text("配對／連線設定") }, onClick = { tab = 0; settingsMenu = false })
                            DropdownMenuItem(text = { Text("繁體中文") }, onClick = { vm.setLanguage("zh-TW"); settingsMenu = false })
                            DropdownMenuItem(text = { Text("English") }, onClick = { vm.setLanguage("en"); settingsMenu = false })
                            DropdownMenuItem(text = { Text("Tiếng Việt") }, onClick = { vm.setLanguage("vi"); settingsMenu = false })
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(deviceName, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (ready) Color(0xFF43D17A) else Color(0xFFE85B52), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(if (ready) t("connected") else if (connected) t("connecting") else t("disconnected"),
                                color = Color(0xFFBDBDBD), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { if (ready) vm.sendKey(KeyCodes.POWER) }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "電源", tint = Color(0xFFE64B43), modifier = Modifier.size(34.dp))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> PairingScreen(vm, onPaired = { vm.rememberCurrentTv(); vm.connect(); tab = 1 }, onConnectSaved = { address -> vm.reconnectTo(address); tab = 1 })
                    else -> ThreePageRemoteScreen(vm)
                }
            }
        }
    }
}
