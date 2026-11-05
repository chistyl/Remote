package com.chi.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
    var code by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var savedMenu by remember { mutableStateOf(false) }
    var nearbyMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.startDiscovery() }
    DisposableEffect(Unit) { onDispose { vm.stopDiscovery() } }
    LaunchedEffect(state) {
        if (state == PairingClient.State.WaitCode) showCode = true
        if (state == PairingClient.State.Paired) { showCode = false; onPaired() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 680.dp
        val outerPadding = if (compact) 16.dp else 22.dp
        val sectionGap = if (compact) 7.dp else 10.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = outerPadding, vertical = if (compact) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("connect_tv"), style = MaterialTheme.typography.headlineSmall)
                Text(t("wifi_help"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (savedDevices.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("saved_tvs"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    if (savedDevices.size > 2) {
                        Box {
                            TextButton(onClick = { savedMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text("+${savedDevices.size - 2} 台")
                            }
                            DropdownMenu(expanded = savedMenu, onDismissRequest = { savedMenu = false }) {
                                savedDevices.drop(2).forEach { device ->
                                    DropdownMenuItem(
                                        text = { Column { Text(device.name); Text(device.host, style = MaterialTheme.typography.labelSmall) } },
                                        onClick = { savedMenu = false; onConnectSaved(device.host) }
                                    )
                                }
                            }
                        }
                    }
                }
                savedDevices.take(2).forEach { device ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121927)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                Text(device.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(device.host, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            TextButton(onClick = { onConnectSaved(device.host) }) { Text(t("quick_connect")) }
                            TextButton(onClick = { vm.forgetTv(device.host) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(t("remove")) }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("nearby_tvs"), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (devices.size > 2) {
                    Box {
                        TextButton(onClick = { nearbyMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("+${devices.size - 2} 台")
                        }
                        DropdownMenu(expanded = nearbyMenu, onDismissRequest = { nearbyMenu = false }) {
                            devices.drop(2).forEach { device ->
                                DropdownMenuItem(
                                    text = { Column { Text(device.name); Text(device.host, style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { nearbyMenu = false; ip = device.host; vm.setHost(device.host) }
                                )
                            }
                        }
                    }
                }
            }
            if (devices.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().height(if (compact) 38.dp else 44.dp)
                        .background(Color(0xFF121927), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(t("searching"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                devices.take(2).forEach { device ->
                    OutlinedButton(
                        onClick = { ip = device.host; vm.setHost(device.host) },
                        modifier = Modifier.fillMaxWidth().height(if (compact) 38.dp else 42.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("${device.name}  ·  ${device.host}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it; vm.setHost(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("tv_ip")) },
                placeholder = { Text(t("ip_example")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { vm.startPairing() },
                modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = ip.isNotBlank() && state !in setOf(
                    PairingClient.State.Connecting, PairingClient.State.WaitRequestAck,
                    PairingClient.State.WaitOptionAck, PairingClient.State.WaitConfigAck,
                    PairingClient.State.WaitCode, PairingClient.State.WaitSecretAck
                )
            ) {
                Text(when (state) {
                    PairingClient.State.Connecting -> t("connecting_text")
                    PairingClient.State.WaitRequestAck, PairingClient.State.WaitOptionAck,
                    PairingClient.State.WaitConfigAck -> t("pairing")
                    PairingClient.State.Paired -> t("paired")
                    PairingClient.State.Failed -> t("retry_pair")
                    else -> t("start_pair")
                })
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121927)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(t("pair_method"), style = MaterialTheme.typography.titleSmall)
                    Text("${t("step1")}  ·  ${t("step2")}  ·  ${t("step3")}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t("pair_once"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                    label = { Text(t("six_code")) },
                    singleLine = true,
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
