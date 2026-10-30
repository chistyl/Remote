from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# Surface the current connection notice directly inside the device screen.
old_call = """                else -> DeviceScreen(
                    ip = ip, onIp = { ip = it }, devices = discovered, savedDevices = savedDevices, searching = searching,"""
new_call = """                else -> DeviceScreen(
                    status = notice,
                    ip = ip, onIp = { ip = it }, devices = discovered, savedDevices = savedDevices, searching = searching,"""
if old_call not in s:
    raise SystemExit("Could not locate DeviceScreen call site")
s = s.replace(old_call, new_call, 1)

old_sig = """private fun DeviceScreen(ip: String, onIp: (String) -> Unit,"""
new_sig = """private fun DeviceScreen(status: String, ip: String, onIp: (String) -> Unit,"""
if old_sig not in s:
    raise SystemExit("Could not locate DeviceScreen signature")
s = s.replace(old_sig, new_sig, 1)

old_header = """    Text("DEVICE CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
    Spacer(Modifier.height(14.dp))"""
new_header = """    Text("DEVICE CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel2).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status.contains("正在")) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else {
            Icon(Icons.Default.Info, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(status, color = Color.White, fontSize = 12.sp)
    }
    Spacer(Modifier.height(14.dp))"""
if old_header not in s:
    raise SystemExit("Could not locate DeviceScreen header")
s = s.replace(old_header, new_header, 1)

# Keep the pairing button visibly enabled; all validation is surfaced by the click handler.
s = s.replace(
    'Button(onClick = onConnect, enabled = ip.isNotBlank() && !pairingPending, modifier = Modifier.fillMaxWidth(),',
    'Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(),'
)
s = s.replace(
    'Button(onClick = onConnect, enabled = !pairingPending, modifier = Modifier.fillMaxWidth(),',
    'Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(),'
)

p.write_text(s, encoding="utf-8")
print("Added inline pairing status, progress indicator, and always-responsive pairing button.")
