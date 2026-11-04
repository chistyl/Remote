package com.chi.tvremote.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
    var textToSend by remember { mutableStateOf("") }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RemoteActionButton(t("back"), Icons.Default.ArrowBack, ready, { vm.sendKey(KeyCodes.BACK) }, Modifier.weight(1f))
            RemoteActionButton(
                t("home"), Icons.Default.Home, ready, { vm.sendKey(KeyCodes.HOME) },
                Modifier.weight(1f), onLongClick = { vm.sendLongKey(KeyCodes.HOME) }
            )
            RemoteActionButton(t("menu"), Icons.Default.Menu, ready, { vm.sendKey(KeyCodes.MENU) }, Modifier.weight(1f))
            RemoteActionButton(t("input_source"), Icons.Default.SettingsInputHdmi, ready, { vm.sendKey(KeyCodes.TV_INPUT) }, Modifier.weight(1f))
            RemoteActionButton(t("power"), Icons.Default.PowerSettingsNew, ready, { vm.sendKey(KeyCodes.POWER) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (transportConnected) vm.disconnect() else vm.connect() },
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text(if (transportConnected) t("disconnect") else t("connect_tv")) }
            Text(
                if (ready) t("connected") else if (transportConnected) t("connecting") else t("disconnected"),
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelMedium
            )
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = controlMode == 0, onClick = { vm.setRemoteControlMode(0) }, label = { Text(t("dpad_mode")) }, modifier = Modifier.weight(1f))
            FilterChip(selected = controlMode == 1, onClick = { vm.setRemoteControlMode(1) }, label = { Text(t("touchpad_mode")) }, modifier = Modifier.weight(1f))
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatActionButton(t("volume_down"), Modifier.weight(1f), ready, repeatEnabled) { vm.sendKey(KeyCodes.VOLUME_DOWN) }
            KeyButton(t("mute"), Modifier.weight(1f), ready) { vm.sendKey(KeyCodes.VOLUME_MUTE) }
            RepeatActionButton(t("volume_up"), Modifier.weight(1f), ready, repeatEnabled) { vm.sendKey(KeyCodes.VOLUME_UP) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(t("previous"), Modifier.weight(1f), ready) { vm.sendKey(KeyCodes.MEDIA_PREV) }
            KeyButton(t("play_pause"), Modifier.weight(1f), ready) { vm.sendKey(KeyCodes.MEDIA_PLAY_PAUSE) }
            KeyButton(t("next"), Modifier.weight(1f), ready) { vm.sendKey(KeyCodes.MEDIA_NEXT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatActionButton(t("channel_down"), Modifier.weight(1f), ready, repeatEnabled) { vm.sendKey(KeyCodes.CHANNEL_DOWN) }
            RepeatActionButton(t("channel_up"), Modifier.weight(1f), ready, repeatEnabled) { vm.sendKey(KeyCodes.CHANNEL_UP) }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(t("text_input"), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = textToSend,
            onValueChange = { textToSend = it; textStatus = null },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(t("text_placeholder")) },
            singleLine = true,
            enabled = ready,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                vm.sendText(textToSend) { sent ->
                    textStatus = if (sent) textSentLabel else textFailedLabel
                    if (sent) textToSend = ""
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
            Button(
                onClick = {
                    vm.sendText(textToSend) { sent ->
                        textStatus = if (sent) textSentLabel else textFailedLabel
                        if (sent) textToSend = ""
                    }
                },
                enabled = ready && textToSend.isNotEmpty() && textInputReady,
                modifier = Modifier.weight(1f)
            ) { Text(t("send_text")) }
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
