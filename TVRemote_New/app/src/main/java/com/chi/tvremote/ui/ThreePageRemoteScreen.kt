package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chi.tvremote.RemoteViewModel
import com.chi.tvremote.protocol.KeyCodes
import kotlinx.coroutines.launch
import kotlin.math.abs

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
                else -> TouchpadPage(vm)
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
private fun TouchpadPage(vm: RemoteViewModel) {
    val ready by vm.remote.ready.collectAsState()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 34.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxSize().background(RemoteBg).padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("觸控板", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(5.dp))
        Text(
            if (ready) "滑動移動焦點・點擊確認・長按持續操作" else "請先連線電視",
            color = Color(0xFF9DA0AA),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        Surface(
            color = RemoteCard,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF373941)),
            modifier = Modifier.fillMaxWidth().weight(1f)
                .pointerInput(ready, swipeThresholdPx) {
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragX += amount.x
                            dragY += amount.y
                        },
                        onDragEnd = {
                            if (ready && (abs(dragX) >= swipeThresholdPx || abs(dragY) >= swipeThresholdPx)) {
                                if (abs(dragX) > abs(dragY)) {
                                    vm.sendKey(if (dragX > 0) KeyCodes.DPAD_RIGHT else KeyCodes.DPAD_LEFT)
                                } else {
                                    vm.sendKey(if (dragY > 0) KeyCodes.DPAD_DOWN else KeyCodes.DPAD_UP)
                                }
                            }
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragCancel = { dragX = 0f; dragY = 0f }
                    )
                }
                .pointerInput(ready) {
                    detectTapGestures(
                        onTap = { if (ready) vm.sendKey(KeyCodes.DPAD_CENTER) },
                        onDoubleTap = { if (ready) vm.sendKey(KeyCodes.DPAD_CENTER) },
                        onLongPress = { if (ready) vm.sendLongKey(KeyCodes.DPAD_CENTER) }
                    )
                }
        ) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("觸 控 操 作 區", color = Color(0xFFBFC2CC), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text("↑   ↓   ←   →", color = RemoteAccent, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text("單指滑動控制方向", color = Color(0xFF9296A1), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(5.dp))
                Text("點一下：確認　雙擊：確認　長按：持續確認", color = Color(0xFF9296A1), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchpadActionButton("返回", KeyCodes.BACK, ready, Modifier.weight(1f), vm)
            TouchpadActionButton("首頁", KeyCodes.HOME, ready, Modifier.weight(1f), vm)
        }
    }
}

@Composable
private fun TouchpadActionButton(
    label: String,
    keyCode: Int,
    enabled: Boolean,
    modifier: Modifier,
    vm: RemoteViewModel
) {
    Surface(
        onClick = { vm.sendKey(keyCode) },
        enabled = enabled,
        color = Color(0xFF292B31),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF41434B)),
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
