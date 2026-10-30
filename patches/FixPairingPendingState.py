from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
old = "var pairingPending by remember { mutableStateOf(false) }"
new = "val pairingPending = remember { androidx.compose.runtime.mutableStateOf(false) }"
if s.count(old) != 1:
    raise SystemExit(f"Expected original pairingPending declaration once, got {s.count(old)}")
s = s.replace(old, new, 1)

# MainActivity's state reads/writes are explicit; the DeviceScreen parameter remains a Boolean.
s = s.replace("pairingPending = pairingPending, pin = pin", "pairingPending = pairingPending.value, pin = pin")
s = s.replace("pairingPending = false", "pairingPending.value = false")
s = s.replace("pairingPending = true", "pairingPending.value = true")

p.write_text(s, encoding="utf-8")
print("Converted pairingPending to explicit MutableState<Boolean> with .value access.")
