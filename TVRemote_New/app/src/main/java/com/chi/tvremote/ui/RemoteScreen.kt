package com.chi.tvremote.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ReferenceCircleAction("⏮", ready) { vm.sendKey(KeyCodes.MEDIA_PREV) }
            ReferenceCircleAction("◀◀", ready) { vm.sendKey(KeyCodes.MEDIA_REWIND) }
            ReferenceCircleAction("▶Ⅱ", ready) { vm.sendKey(KeyCodes.MEDIA_PLAY_PAUSE) }
            ReferenceCircleAction("▶▶", ready) { vm.sendKey(KeyCodes.MEDIA_FAST_FORWARD) }
            ReferenceCircleAction("⏭", ready) { vm.sendKey(KeyCodes.MEDIA_NEXT) }
        }
        if (!ready) {
            Text(if (transportConnected) t("connecting") else t("disconnected"), color = Color(0xFFB7B7B7), style = MaterialTheme.typography.labelSmall)
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        powerOn?.let { Text(if (it) t("tv_power_on") else t("tv_power_off"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        volumeInfo?.let { info ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (info.muted) t("muted") else t("volume_info"), style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    progress = { if (info.muted) 0f else info.percent / 100f },
                    modifier = Modifier.weight(1f)
                )
                Text(if (info.muted) "—" else "${info.percent}%", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = { vm.setRemoteControlMode(0) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (controlMode == 0) Color(0xFF4B455A) else Color(0xFF101116),
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) { Text(t("dpad_mode"), style = MaterialTheme.typography.titleSmall) }
            }
            Surface(
                onClick = { vm.setRemoteControlMode(1) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (controlMode == 1) Color(0xFF4B455A) else Color(0xFF101116),
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) { Text(t("touchpad_mode"), style = MaterialTheme.typography.titleSmall) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text(t("hold_repeat"), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            Switch(checked = repeatEnabled, onCheckedChange = vm::setHoldRepeatEnabled)
        }
        if (controlMode == 0) {
            CircularDPad(
                enabled = ready, repeatEnabled = repeatEnabled,
                onKey = vm::sendKey, onLongKey = vm::sendLongKey,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            TouchpadControl(vm)
        }

        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(62.dp).fillMaxHeight().background(Color(0xFF353535), RoundedCornerShape(34.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("+", color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.VOLUME_UP) })
                Text("音量", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("−", color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.VOLUME_DOWN) })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReferenceCircleAction("⌨", ready, Modifier.weight(1f)) { focusRequester.requestFocus(); keyboardController?.show() }
                    ReferenceCircleAction("⌂", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.HOME) }
                    ReferenceCircleAction("✥", ready, Modifier.weight(1f)) { vm.setRemoteControlMode(if (controlMode == 0) 1 else 0) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReferenceCircleAction("☰", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.MENU) }
                    ReferenceCircleAction("◀×", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.VOLUME_MUTE) }
                    ReferenceCircleAction("↶", ready, Modifier.weight(1f)) { vm.sendKey(KeyCodes.BACK) }
                }
            }
            Column(
                Modifier.width(62.dp).fillMaxHeight().background(Color(0xFF353535), RoundedCornerShape(34.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("⌃", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.CHANNEL_UP) })
                Text("CH", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("⌄", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { if (ready) vm.sendKey(KeyCodes.CHANNEL_DOWN) })
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(t("text_input"), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = textMode == 0,
                onClick = {
                    textMode = 0
                    val initialText = textValue.text
                    if (initialText.isNotEmpty() && textValue.composition == null) {
                        lastRealtimeText = initialText
                        vm.sendRealtimeTextDelta("", initialText) { sent ->
                            if (!sent) textStatus = textSyncFailedLabel
                        }
                    } else {
                        lastRealtimeText = ""
                    }
                },
                label = { Text(t("text_mode_realtime")) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = textMode == 1,
                onClick = {
                    if (lastRealtimeText.isNotEmpty()) {
                        vm.sendRealtimeTextDelta(lastRealtimeText, "")
                        lastRealtimeText = ""
                    }
                    textValue = TextFieldValue("")
                    textMode = 1
                },
                label = { Text(t("text_mode_batch")) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            if (textMode == 0) t("text_realtime_hint") else t("text_input_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { updated ->
                val previous = lastRealtimeText
                textValue = updated
                textStatus = null
                if (textMode == 0 && updated.composition == null && updated.text != previous) {
                    lastRealtimeText = updated.text
                    vm.sendRealtimeTextDelta(previous, updated.text) { sent ->
                        if (!sent) textStatus = textSyncFailedLabel
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(t("text_placeholder")) },
            singleLine = true,
            enabled = ready,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (textMode == 1) {
                    vm.sendText(textValue.text) { sent ->
                        textStatus = if (sent) textSentLabel else textFailedLabel
                        if (sent) textValue = TextFieldValue("")
                    }
                } else {
                    keyboardController?.hide()
                }
            })
        )
        if (ready && !voiceSupported) {
            Text(t("voice_not_supported"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { focusRequester.requestFocus(); keyboardController?.show() },
                enabled = ready,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(t("keyboard_open"))
            }
            OutlinedButton(
                onClick = {
                    if (voiceRecording) {
                        stopVoice.set(true)
                    } else if (!voiceSupported) {
                        voiceStatus = voiceNotSupportedLabel
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startVoiceCapture()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = ready && (voiceSupported || voiceRecording),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (voiceRecording) t("voice_stop") else t("voice_input"))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (textMode == 1) {
                Button(
                    onClick = {
                        vm.sendText(textValue.text) { sent ->
                            textStatus = if (sent) textSentLabel else textFailedLabel
                            if (sent) textValue = TextFieldValue("")
                        }
                    },
                    enabled = ready && textValue.text.isNotEmpty() && textInputReady,
                    modifier = Modifier.weight(1f)
                ) { Text(t("send_text")) }
            } else {
                OutlinedButton(
                    onClick = {
                        val previouslySynced = lastRealtimeText
                        if (previouslySynced.isNotEmpty()) {
                            vm.sendRealtimeTextDelta(previouslySynced, "")
                        }
                        textValue = TextFieldValue("")
                        lastRealtimeText = ""
                    },
                    enabled = textValue.text.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text(t("text_clear")) }
            }
            Text(
                if (textInputReady) t("connected") else t("text_waiting"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        textStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        voiceStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
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
