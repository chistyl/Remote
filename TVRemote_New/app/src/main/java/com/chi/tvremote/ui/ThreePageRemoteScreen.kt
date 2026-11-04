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
    val pager = rememberPagerState(pageCount = { 2 })
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
                else -> NumberPadPage(vm)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
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

