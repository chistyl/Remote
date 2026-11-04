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
                else -> MousePage()
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
        listOf("7", "8", "9"), listOf("*", "0", "#")
    )
    Column(
        Modifier.fillMaxSize().background(RemoteBg).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("數字鍵盤", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { label ->
                    Surface(
                        onClick = { if (ready && label.length == 1 && label[0].isDigit()) vm.sendKey(KeyCodes.digit(label[0].digitToInt())) },
                        enabled = ready && label[0].isDigit(),
                        color = RemoteCard,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MousePage() {
    Column(
        Modifier.fillMaxSize().background(RemoteBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = RemoteCard,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Mouse, contentDescription = null, tint = RemoteAccent, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(18.dp))
                Text("滑鼠控制", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    "尚未建立電視端 ADB／虛擬 HID 連線",
                    color = Color(0xFFB8BBC4),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "此頁暫不傳送模擬方向鍵，避免誤認為真正滑鼠。",
                    color = Color(0xFF858994),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
