package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val adbStatus by vm.adbStatus.collectAsState()
    val connecting by vm.adbConnecting.collectAsState()
    val host by vm.host.collectAsState()
    val ready by vm.remote.ready.collectAsState()
    Column(
        Modifier.fillMaxSize().background(RemoteBg).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("滑鼠控制", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        Surface(
            color = Color(0xFF353535),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color(0xFF202126),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .pointerInput(vm) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var previousPosition = down.position
                                var previousCenterY: Float? = null
                                var scrollRemainder = 0f
                                var moved = false
                                var longPress = false
                                var multiTouch = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        if (longPress) {
                                            vm.sendScrcpyMouseReport(buttons = 0)
                                            longPress = false
                                        }
                                        multiTouch = true
                                        val centerY = pressed.map { it.position.y }.average().toFloat()
                                        val previous = previousCenterY
                                        if (previous != null) {
                                            scrollRemainder += -(centerY - previous) / 24f
                                            val wheel = scrollRemainder.toInt().coerceIn(-127, 127)
                                            if (wheel != 0) {
                                                vm.sendScrcpyMouseReport(wheel = wheel)
                                                scrollRemainder -= wheel
                                            }
                                        }
                                        previousCenterY = centerY
                                        pressed.forEach { it.consume() }
                                    } else if (!multiTouch) {
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null) {
                                            val delta = change.position - previousPosition
                                            val elapsed = change.uptimeMillis - down.uptimeMillis
                                            if (!moved && !longPress && elapsed >= viewConfiguration.longPressTimeoutMillis) {
                                                longPress = true
                                                vm.sendScrcpyMouseReport(buttons = 1)
                                            }
                                            val totalDelta = change.position - down.position
                                            val distanceSquared = totalDelta.x * totalDelta.x + totalDelta.y * totalDelta.y
                                            if (!moved && distanceSquared > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                                moved = true
                                            }
                                            if (moved && (delta.x != 0f || delta.y != 0f)) {
                                                vm.sendScrcpyMouseReport(
                                                    buttons = if (longPress) 1 else 0,
                                                    dx = (delta.x * 1.35f).toInt().coerceIn(-127, 127),
                                                    dy = (delta.y * 1.35f).toInt().coerceIn(-127, 127)
                                                )
                                                change.consume()
                                            }
                                            previousPosition = change.position
                                            if (!change.pressed) {
                                                if (longPress) {
                                                    vm.sendScrcpyMouseReport(buttons = 0)
                                                } else if (!moved) {
                                                    vm.sendScrcpyMouseReport(buttons = 1)
                                                    vm.sendScrcpyMouseReport(buttons = 0)
                                                }
                                                change.consume()
                                                break
                                            }
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Mouse, contentDescription = null, tint = RemoteAccent, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("觸控板", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text("滑動移動｜點一下左鍵｜長按後拖曳",
                            color = Color(0xFFB8BBC4), style = MaterialTheme.typography.bodySmall)
                        Text("雙指上下滑動：捲動",
                            color = Color(0xFF92959D), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("ADB 裝置授權", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(if (host.isBlank()) "請先在遙控器頁面選擇電視" else "目標電視：$host:5555",
                    color = Color(0xFFB8BBC4), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.connectAdb() },
                        enabled = !connecting && host.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = RemoteAccent)
                    ) { Text(if (connecting) "連線中…" else "連線／授權") }
                    OutlinedButton(onClick = { vm.disconnectAdb() }, enabled = !connecting) { Text("中斷") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.installScrcpyServer() }, enabled = !connecting && host.isNotBlank()) {
                        Text("部署元件")
                    }
                    Button(
                        onClick = { vm.startScrcpyMouseService() },
                        enabled = !connecting && host.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = RemoteAccent)
                    ) { Text("啟動滑鼠") }
                    OutlinedButton(onClick = { vm.stopScrcpyMouseService() }) { Text("停止") }
                }
                Spacer(Modifier.height(10.dp))
                Text(adbStatus, color = Color(0xFFD5D6DC), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ready) "遙控器已連線；滑鼠仍需 ADB 授權" else "請先完成電視配對與連線",
                    color = Color(0xFF92959D), style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
