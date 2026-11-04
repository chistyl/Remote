package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chi.tvremote.RemoteViewModel

@Composable
fun RootScreen(vm: RemoteViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val language by vm.language.collectAsState()
    var languageMenu by remember { mutableStateOf(false) }
    val connected by vm.remote.connected.collectAsState()
    val ready by vm.remote.ready.collectAsState()
    CompositionLocalProvider(LocalAppLanguage provides language) {
    Scaffold(
        containerColor = Color(0xFF080B12),
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0D1420)).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TV REMOTE", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Android TV / Google TV", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FA5B9))
                }
                Box {
                    TextButton(onClick = { languageMenu = true }) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF67D7FF))
                        Spacer(Modifier.width(4.dp))
                        Text(when (language) { "en" -> "EN"; "vi" -> "VI"; else -> "繁中" }, color = Color.White)
                    }
                    DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                        DropdownMenuItem(text = { Text("繁體中文") }, onClick = { vm.setLanguage("zh-TW"); languageMenu = false })
                        DropdownMenuItem(text = { Text("English") }, onClick = { vm.setLanguage("en"); languageMenu = false })
                        DropdownMenuItem(text = { Text("Tiếng Việt") }, onClick = { vm.setLanguage("vi"); languageMenu = false })
                    }
                }
                Surface(color = if (ready) Color(0xFF123D35) else Color(0xFF35212A), shape = RoundedCornerShape(30.dp)) {
                    Text(if (ready) t("connected") else if (connected) t("connecting") else t("disconnected"), Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (ready) Color(0xFF6DE2C0) else Color(0xFFFF9CA8),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1420), contentColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Cast, null) }, label = { Text(t("pair")) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.SettingsRemote, null) }, label = { Text(t("remote")) })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> PairingScreen(vm, onPaired = { vm.rememberCurrentTv(); vm.connect(); tab = 1 }, onConnectSaved = { host -> vm.reconnectTo(host); tab = 1 })
                1 -> ThreePageRemoteScreen(vm)
                else -> ThreePageRemoteScreen(vm)
            }
        }
    }
    }
}
