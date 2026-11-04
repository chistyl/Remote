package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chi.tvremote.RemoteViewModel
import com.chi.tvremote.protocol.KeyCodes
import kotlinx.coroutines.launch

private val RemoteBg = Color(0xFF101114)
private val RemoteCard = Color(0xFF24262B)
private val RemoteAccent = Color(0xFF8B78FF)

@Composable
fun ThreePageRemoteScreen(vm: RemoteViewModel) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(RemoteBg)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> RemoteScreen(vm)
                1 -> NumberPadPage(vm)
                else -> MousePage(vm)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Surface(
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    shape = CircleShape,
                    color = if (pager.currentPage == index) RemoteAccent else Color(0xFF555860),
                    modifier = Modifier.padding(horizontal = 5.dp).size(if (pager.currentPage == index) 9.dp else 7.dp)
                ) {}
            }
        }
    }
}

@Composable
private fun NumberPadPage(vm: RemoteViewModel) {
    val ready by vm.remote.ready.collectAsState()
    val keys = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"),
        listOf("7", "8", "9"), listOf("TV", "0", "Menu")
    )
    Column(
        Modifier.fillMaxSize().background(RemoteBg).padding(horizontal = 26.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(0.15f))
        keys.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { label ->
                    val outlined = label == "TV" || label == "Menu"
                    Surface(
                        onClick = {
                            if (ready) vm.sendKey(when (label) {
                                "TV" -> 170
                                "Menu" -> KeyCodes.MENU
                                else -> 7 + label.toInt()
                            })
                        },
                        enabled = ready,
                        color = if (outlined) Color.Transparent else Color(0xFF353535),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = if (outlined) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF484848)) else null,
                        modifier = Modifier.weight(1f).aspectRatio(1.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, style = if (label.length > 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
            if (rowIndex != keys.lastIndex) Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.weight(0.45f))
    }
}


@Composable
private fun MousePage(vm: RemoteViewModel) {
    val status by vm.adbProbeStatus.collectAsState()
    val checking by vm.adbProbeRunning.collectAsState()
    Column(
        Modifier.fillMaxSize().background(RemoteBg).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("滑鼠控制", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Surface(
            color = Color(0xFF353535),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Mouse, contentDescription = null, tint = Color(0xFF8B78FF), modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(16.dp))
                Text("滑鼠觸控區", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("先確認電視是否開啟 ADB 網路除錯",
                    color = Color(0xFFB8BBC4), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { vm.checkAdbTcpPort() },
                    enabled = !checking,
                    colors = ButtonDefaults.buttonColors(containerColor = RemoteAccent)
                ) {
                    Text(if (checking) "檢查中…" else "檢查 ADB 連線")
                }
                Spacer(Modifier.height(12.dp))
                Text(status, color = Color(0xFFD5D6DC), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("檢查 TCP 5555 僅代表連接埠可連線，不等於 ADB 已授權；真正滑鼠仍需完成 ADB／虛擬 HID。",
                    color = Color(0xFF92959D), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
