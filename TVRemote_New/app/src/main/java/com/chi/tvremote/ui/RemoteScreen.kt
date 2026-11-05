package com.chi.tvremote.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chi.tvremote.RemoteViewModel
import com.chi.tvremote.audio.VoiceRecorder
import com.chi.tvremote.protocol.KeyCodes
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun RemoteScreen(vm: RemoteViewModel) {
    val transportConnected by vm.remote.connected.collectAsState()
    val ready by vm.remote.ready.collectAsState()
    val error by vm.remote.error.collectAsState()
    val textInputReady by vm.remote.textInputReady.collectAsState()
    val volumeInfo by vm.remote.volumeInfo.collectAsState()
    val powerOn by vm.remote.powerOn.collectAsState()
    val voiceSupported by vm.remote.voiceSupported.collectAsState()
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var textMode by remember { mutableIntStateOf(0) } // 0 = live sync, 1 = send whole text
    var lastRealtimeText by remember { mutableStateOf("") }
    var textStatus by remember { mutableStateOf<String?>(null) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var voiceRecording by remember { mutableStateOf(false) }
    val controlMode by vm.remoteControlMode.collectAsState()
    val repeatEnabled by vm.holdRepeatEnabled.collectAsState()
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    val latestVoiceJob = rememberUpdatedState(voiceJob)
    val stopVoice = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Resolve localized labels during composition; callbacks and coroutines must
    // not invoke composable localization helpers directly.
    val voiceConnectingLabel = t("voice_connecting")
    val voiceListeningLabel = t("voice_listening")
    val voiceDoneLabel = t("voice_done")
    val voiceFailedLabel = t("voice_failed")
    val micPermissionLabel = t("mic_permission")
    val voiceNotSupportedLabel = t("voice_not_supported")
    val textSentLabel = t("text_sent")
    val textFailedLabel = t("text_failed")
    val textSyncFailedLabel = t("text_sync_failed")

    fun startVoiceCapture() {
        if (voiceJob?.isActive == true || !ready || !voiceSupported) return
        stopVoice.set(false)
        voiceRecording = true
        voiceStatus = voiceConnectingLabel
        voiceJob = scope.launch {
            var sessionId: Int? = null
            try {
                val activeSession = vm.remote.beginVoice()
                sessionId = activeSession
                voiceStatus = voiceListeningLabel
                VoiceRecorder().capture(stopVoice) { chunk ->
                    vm.remote.sendVoiceChunk(activeSession, chunk)
                }
                voiceStatus = voiceDoneLabel
            } catch (failure: Throwable) {
                if (failure !is kotlinx.coroutines.CancellationException) {
                    voiceStatus = failure.message?.takeIf { it.isNotBlank() } ?: voiceFailedLabel
                }
            } finally {
                sessionId?.let { id -> runCatching { vm.remote.endVoice(id) } }
                voiceRecording = false
            }
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceCapture() else voiceStatus = micPermissionLabel
    }

    DisposableEffect(Unit) {
        onDispose {
            stopVoice.set(true)
            latestVoiceJob.value?.cancel()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val padSize = if (compact) (maxHeight * 0.31f).coerceIn(190.dp, 240.dp) else 260.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().height(58.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaControlAction("⏮", "上一首", ready, Modifier.weight(1f), 42.dp) { vm.sendKey(KeyCodes.MEDIA_PREV) }
                MediaControlAction("◀◀", "倒轉", ready, Modifier.weight(1f), 42.dp) { vm.sendKey(KeyCodes.MEDIA_REWIND) }
                MediaControlAction("▶Ⅱ", "播放／暫停", ready, Modifier.weight(1.12f), 50.dp, emphasized = true) { vm.sendKey(KeyCodes.MEDIA_PLAY_PAUSE) }
                MediaControlAction("▶▶", "快轉", ready, Modifier.weight(1f), 42.dp) { vm.sendKey(KeyCodes.MEDIA_FAST_FORWARD) }
                MediaControlAction("⏭", "下一首", ready, Modifier.weight(1f), 42.dp) { vm.sendKey(KeyCodes.MEDIA_NEXT) }
            }

            Row(
                Modifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (error != null) error!! else if (ready) {
                        powerOn?.let { if (it) t("tv_power_on") else t("tv_power_off") } ?: t("connected")
                    } else if (transportConnected) t("connecting") else t("disconnected"),
                    color = if (error != null) MaterialTheme.colorScheme.error else Color(0xFF9EA4AE),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                volumeInfo?.let { info ->
                    Spacer(Modifier.width(8.dp))
                    Text(if (info.muted) t("muted") else "${info.percent}%", color = Color(0xFF9EA4AE), style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { vm.setRemoteControlMode(0) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(11.dp),
                    color = if (controlMode == 0) Color(0xFF4B455A) else Color(0xFF25262B),
                    contentColor = Color.White
                ) { Box(contentAlignment = Alignment.Center) { Text(t("dpad_mode"), style = MaterialTheme.typography.labelMedium) } }
                Surface(
                    onClick = { vm.setRemoteControlMode(1) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(11.dp),
                    color = if (controlMode == 1) Color(0xFF4B455A) else Color(0xFF25262B),
                    contentColor = Color.White
                ) { Box(contentAlignment = Alignment.Center) { Text(t("touchpad_mode"), style = MaterialTheme.typography.labelMedium) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("hold_repeat"), style = MaterialTheme.typography.labelSmall, color = Color(0xFFB8BBC4))
                    Switch(checked = repeatEnabled, onCheckedChange = vm::setHoldRepeatEnabled, modifier = Modifier.height(32.dp))
                }
            }

            Box(
                Modifier.fillMaxWidth().weight(1f, fill = true),
                contentAlignment = Alignment.Center
            ) {
                if (controlMode == 0) {
                    CircularDPad(
                        enabled = ready,
                        repeatEnabled = repeatEnabled,
                        onKey = vm::sendKey,
                        onLongKey = vm::sendLongKey,
                        size = padSize
                    )
                } else {
                    TouchpadControl(vm, modifier = Modifier.fillMaxWidth(), compact = true)
                }
            }

            Row(
                Modifier.fillMaxWidth().height(76.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(52.dp).fillMaxHeight().background(Color(0xFF353535), RoundedCornerShape(22.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("+", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.VOLUME_UP) })
                    Text("VOL", color = Color(0xFFB8BBC4), style = MaterialTheme.typography.labelSmall)
                    Text("−", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.VOLUME_DOWN) })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ReferenceCircleAction("⌨", ready, Modifier.weight(1f)) { focusRequester.requestFocus(); keyboardController?.show() }
                        ReferenceCircleAction("⌂", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.HOME) }
                        ReferenceCircleAction("✥", ready, Modifier.weight(1f)) { vm.setRemoteControlMode(if (controlMode == 0) 1 else 0) }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ReferenceCircleAction("☰", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.MENU) }
                        ReferenceCircleAction("◀×", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.VOLUME_MUTE) }
                        ReferenceCircleAction("↶", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.BACK) }
                    }
                }
                Column(
                    Modifier.width(52.dp).fillMaxHeight().background(Color(0xFF353535), RoundedCornerShape(22.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("⌃", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.CHANNEL_UP) })
                    Text("CH", color = Color(0xFFB8BBC4), style = MaterialTheme.typography.labelSmall)
                    Text("⌄", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.CHANNEL_DOWN) })
                }
            }

            Row(
                Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { updated ->
                        val previous = lastRealtimeText
                        textValue = updated
                        textStatus = null
                        if (textMode == 0 && updated.composition == null && updated.text != previous) {
                            lastRealtimeText = updated.text
                            vm.sendRealtimeTextDelta(previous, updated.text) { sent -> if (!sent) textStatus = textSyncFailedLabel }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focusRequester),
                    placeholder = { Text(t("text_placeholder"), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    enabled = ready,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textMode == 1) {
                            vm.sendText(textValue.text) { sent ->
                                textStatus = if (sent) textSentLabel else textFailedLabel
                                if (sent) textValue = TextFieldValue("")
                            }
                        } else keyboardController?.hide()
                    })
                )
                IconButton(onClick = { focusRequester.requestFocus(); keyboardController?.show() }, enabled = ready) {
                    Icon(Icons.Default.Keyboard, contentDescription = t("keyboard_open"), tint = Color(0xFFB8BBC4))
                }
                IconButton(
                    onClick = {
                        if (voiceRecording) stopVoice.set(true)
                        else if (!voiceSupported) voiceStatus = voiceNotSupportedLabel
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceCapture()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = ready && (voiceSupported || voiceRecording)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = if (voiceRecording) t("voice_stop") else t("voice_input"), tint = if (voiceRecording) Color(0xFFFF7777) else Color(0xFFB8BBC4))
                }
                IconButton(
                    onClick = {
                        if (textMode == 1) {
                            vm.sendText(textValue.text) { sent ->
                                textStatus = if (sent) textSentLabel else textFailedLabel
                                if (sent) textValue = TextFieldValue("")
                            }
                        } else {
                            if (lastRealtimeText.isNotEmpty()) vm.sendRealtimeTextDelta(lastRealtimeText, "")
                            textValue = TextFieldValue("")
                            lastRealtimeText = ""
                        }
                    },
                    enabled = ready && if (textMode == 1) textValue.text.isNotEmpty() && textInputReady else textValue.text.isNotEmpty()
                ) {
                    Icon(Icons.Default.Send, contentDescription = if (textMode == 1) t("send_text") else t("text_clear"), tint = Color(0xFF8B78FF))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = textMode == 0,
                        onClick = {
                            textMode = 0
                            val current = textValue.text
                            if (current.isNotEmpty() && textValue.composition == null) {
                                lastRealtimeText = current
                                vm.sendRealtimeTextDelta("", current) { sent -> if (!sent) textStatus = textSyncFailedLabel }
                            }
                        },
                        label = { Text(t("text_mode_realtime"), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(26.dp)
                    )
                    FilterChip(
                        selected = textMode == 1,
                        onClick = {
                            if (lastRealtimeText.isNotEmpty()) vm.sendRealtimeTextDelta(lastRealtimeText, "")
                            lastRealtimeText = ""
                            textValue = TextFieldValue("")
                            textMode = 1
                        },
                        label = { Text(t("text_mode_batch"), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(26.dp)
                    )
                }
                Text(
                    textStatus ?: voiceStatus ?: if (textInputReady) t("connected") else t("text_waiting"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9298A3),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(46.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp)) { Text(label) }
}


@Composable
private fun ReferenceCircleAction(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(1f),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color(0xFF353535),
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun MediaControlAction(
    symbol: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    buttonSize: androidx.compose.ui.unit.Dp,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(buttonSize),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (emphasized) Color(0xFF6554B8) else Color(0xFF353535),
            contentColor = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    symbol,
                    style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color(0xFFB8BBC4),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
