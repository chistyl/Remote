package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chi.tvremote.RemoteViewModel
import com.chi.tvremote.protocol.KeyCodes

/** Main remote touch surface, matching the original app's touchpad-first workflow. */
@Composable
fun TouchpadControl(vm: RemoteViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val connected by vm.remote.ready.collectAsState()
    val sensitivity by vm.sensitivity.collectAsState()
    val motion = remember { TouchpadMotion() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(if (compact) 170.dp else 224.dp)
                .background(Color(0xFF171B22), RoundedCornerShape(24.dp))
                .pointerInput(connected, sensitivity) {
                    detectDragGestures(
                        onDragStart = { motion.reset() },
                        onDragEnd = { motion.reset() },
                        onDragCancel = { motion.reset() }
                    ) { change, drag ->
                        change.consume()
                        if (connected) {
                            val steps = motion.consume(
                                deltaX = drag.x * sensitivity,
                                deltaY = drag.y * sensitivity,
                                thresholdPx = 34.dp.toPx(),
                                left = KeyCodes.DPAD_LEFT,
                                right = KeyCodes.DPAD_RIGHT,
                                up = KeyCodes.DPAD_UP,
                                down = KeyCodes.DPAD_DOWN
                            )
                            steps.forEach(vm::sendKey)
                        } else motion.reset()
                    }
                }
                .pointerInput(connected) {
                    detectTapGestures(
                        onTap = { if (connected) vm.sendKey(KeyCodes.DPAD_CENTER) },
                        onDoubleTap = { if (connected) { vm.sendKey(KeyCodes.DPAD_CENTER); vm.sendKey(KeyCodes.DPAD_CENTER) } },
                        onLongPress = { if (connected) vm.sendLongKey(KeyCodes.DPAD_CENTER) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(t("touch_surface"), color = Color(0xFF67D7FF), style = MaterialTheme.typography.titleLarge)
                Text(t("swipe_directions"), color = Color(0xFF9EB0C2))
                Text(t("tap_confirm"), color = Color(0xFF71869B), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (connected) vm.sendKey(KeyCodes.BACK) }, modifier = Modifier.weight(1f).height(if (compact) 34.dp else 44.dp), enabled = connected) {
                Text(t("back"))
            }
            OutlinedButton(onClick = { if (connected) vm.sendKey(KeyCodes.DPAD_CENTER) }, modifier = Modifier.weight(1f).height(44.dp), enabled = connected) {
                Text("OK")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = if (compact) Modifier.height(30.dp) else Modifier) {
            Text(t("sensitivity"), modifier = Modifier.width(66.dp), style = MaterialTheme.typography.labelMedium)
            Slider(value = sensitivity, onValueChange = vm::setSensitivity, valueRange = 0.5f..2f, modifier = Modifier.weight(1f))
            Text("${(sensitivity * 100).toInt()}%", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
