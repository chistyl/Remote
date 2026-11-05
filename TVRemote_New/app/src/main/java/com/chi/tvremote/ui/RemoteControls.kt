@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chi.tvremote.protocol.KeyCodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val RemotePanel = Color(0xFF141C28)
private val RemoteButton = Color(0xFF202C3B)
private val RemoteAccent = Color(0xFF67D7FF)

/** Physical-remote style D-pad with repeat-on-hold key injection. */
@Composable
fun CircularDPad(
    enabled: Boolean,
    repeatEnabled: Boolean,
    onKey: (Int) -> Unit,
    onLongKey: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 340.dp
) {
    val scale = size.value / 340f
    Box(
        modifier = modifier
            .size(size)
            .background(Color(0xFF353535), CircleShape)
            .then(Modifier),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.Transparent,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8B5AA8)),
            modifier = Modifier.fillMaxSize()
        ) {}
        Surface(
            color = Color.Transparent,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF477DB5)),
            modifier = Modifier.size((94f * scale).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).background(Color(0xFF92979D), CircleShape))
            }
        }
        @Composable
        fun Direction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, code: Int) {
            Surface(
                modifier = modifier.size((76f * scale).dp).combinedClickable(
                    enabled = enabled,
                    onClick = { onKey(code) },
                    onLongClick = { if (repeatEnabled) onLongKey(code) }
                ),
                color = Color.Transparent,
                contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size((36f * scale).dp))
                }
            }
        }
        Direction(Modifier.align(Alignment.TopCenter).padding(top = (22f * scale).dp), Icons.Default.KeyboardArrowUp, "上", KeyCodes.DPAD_UP)
        Direction(Modifier.align(Alignment.CenterStart).padding(start = (22f * scale).dp), Icons.Default.KeyboardArrowLeft, "左", KeyCodes.DPAD_LEFT)
        Direction(Modifier.align(Alignment.CenterEnd).padding(end = (22f * scale).dp), Icons.Default.KeyboardArrowRight, "右", KeyCodes.DPAD_RIGHT)
        Direction(Modifier.align(Alignment.BottomCenter).padding(bottom = (22f * scale).dp), Icons.Default.KeyboardArrowDown, "下", KeyCodes.DPAD_DOWN)
        Surface(
            modifier = Modifier.size((76f * scale).dp).combinedClickable(enabled = enabled, onClick = { onKey(KeyCodes.DPAD_CENTER) }, onLongClick = { onLongKey(KeyCodes.DPAD_CENTER) }),
            shape = CircleShape, color = Color.Transparent
        ) {}
    }
}

@Composable
private fun RepeatDirectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    keyCode: Int,
    enabled: Boolean,
    repeatEnabled: Boolean,
    onKey: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.size(76.dp)
            .repeatKeyPress(scope, enabled, repeatEnabled) { onKey(keyCode) }
            .semantics { role = Role.Button },
        shape = CircleShape,
        color = if (enabled) RemoteButton else RemoteButton.copy(alpha = 0.35f),
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(34.dp))
        }
    }
}

/** Sends once on touch-down, then repeats while the finger remains pressed. */
fun Modifier.repeatKeyPress(
    scope: CoroutineScope,
    enabled: Boolean,
    repeatEnabled: Boolean,
    onPress: () -> Unit
): Modifier = pointerInput(scope, enabled, repeatEnabled) {
    detectTapGestures(onPress = {
        if (enabled) {
            onPress()
            if (repeatEnabled) {
                val repeatJob = scope.launch {
                    delay(HoldRepeatTiming.INITIAL_DELAY_MS)
                    while (isActive) {
                        onPress()
                        delay(HoldRepeatTiming.INTERVAL_MS)
                    }
                }
                try {
                    tryAwaitRelease()
                } finally {
                    repeatJob.cancel()
                }
            } else {
                tryAwaitRelease()
            }
        } else {
            tryAwaitRelease()
        }
    })
}

@Composable
fun RepeatActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    repeatEnabled: Boolean,
    onPress: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier.height(58.dp)
            .repeatKeyPress(scope, enabled, repeatEnabled, onPress)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) RemotePanel else RemotePanel.copy(alpha = 0.4f),
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RemoteActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.height(76.dp).combinedClickable(
            enabled = enabled, onClick = onClick, onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(16.dp), color = RemotePanel,
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = RemoteAccent, modifier = Modifier.size(27.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = Color(0xFF9AA3B1))
        }
    }
}
