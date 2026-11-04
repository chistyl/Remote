package com.chi.tvremote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chi.tvremote.RemoteViewModel
import com.chi.tvremote.protocol.PairingClient

@Composable
fun PairingScreen(vm: RemoteViewModel, onPaired: () -> Unit, onConnectSaved: (String) -> Unit) {
    val state by vm.pairing.state.collectAsState()
    val error by vm.pairing.error.collectAsState()
    val host by vm.host.collectAsState()
    val devices by vm.devices.collectAsState()
    val savedDevices by vm.savedDevices.collectAsState()
    var ip by remember(host) { mutableStateOf(host) }
    LaunchedEffect(Unit) { vm.startDiscovery() }
    DisposableEffect(Unit) { onDispose { vm.stopDiscovery() } }
    var code by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state == PairingClient.State.WaitCode) showCode = true
        if (state == PairingClient.State.Paired) { showCode = false; onPaired() }
    }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t("connect_tv"), style = MaterialTheme.typography.headlineSmall)
        Text(t("wifi_help"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (savedDevices.isNotEmpty()) {
            Text(t("saved_tvs"), style = MaterialTheme.typography.titleMedium)
            savedDevices.forEach { device ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121927)),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                            Text(device.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onConnectSaved(device.host) }) { Text(t("quick_connect")) }
                        TextButton(onClick = { vm.forgetTv(device.host) }) { Text(t("remove")) }
                    }
                }
            }
        }
        Text(t("nearby_tvs"), style = MaterialTheme.typography.titleMedium)
        if (devices.isEmpty()) {
            Text(t("searching"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            devices.forEach { device ->
                OutlinedButton(
                    onClick = { ip = device.host; vm.setHost(device.host) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("${device.name}  ·  ${device.host}") }
            }
        }
        OutlinedTextField(
            value = ip, onValueChange = { ip = it; vm.setHost(it) },
            modifier = Modifier.fillMaxWidth(), label = { Text(t("tv_ip")) },
            placeholder = { Text(t("ip_example")) }, singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Button(
            onClick = { vm.startPairing() }, modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = ip.isNotBlank() && state !in setOf(PairingClient.State.Connecting, PairingClient.State.WaitRequestAck,
                PairingClient.State.WaitOptionAck, PairingClient.State.WaitConfigAck, PairingClient.State.WaitCode, PairingClient.State.WaitSecretAck)
        ) { Text(when(state) {
            PairingClient.State.Connecting -> t("connecting_text")
            PairingClient.State.WaitRequestAck, PairingClient.State.WaitOptionAck, PairingClient.State.WaitConfigAck -> t("pairing")
            PairingClient.State.Paired -> t("paired")
            PairingClient.State.Failed -> t("retry_pair")
            else -> t("start_pair")
        }) }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121927))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("pair_method"), style = MaterialTheme.typography.titleMedium)
                Text(t("step1"))
                Text(t("step2"))
                Text(t("step3"))
                Text(t("pair_once"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showCode) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(t("enter_code")) },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(6) },
                    label = { Text(t("six_code")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.submitCode(code); showCode = false; code = "" }, enabled = code.length == 6) {
                    Text(t("confirm_pair"))
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelPairing(); showCode = false }) { Text(t("cancel")) } }
        )
    }
}
