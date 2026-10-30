from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
old_candidates = [
    "var pairingPending by remember { mutableStateOf(false) }",
    "var pairingPending: Boolean by remember { mutableStateOf<Boolean>(false) }",
]
matched = [candidate for candidate in old_candidates if s.count(candidate) == 1]
if len(matched) != 1:
    raise SystemExit(f"Expected exactly one known pairingPending declaration, found {matched}")
s = s.replace(matched[0], "val pairingPending = remember { androidx.compose.runtime.mutableStateOf(false) }", 1)

# MainActivity's state reads/writes are explicit; the DeviceScreen parameter remains a Boolean.
s = s.replace("pairingPending = pairingPending, pin = pin", "pairingPending = pairingPending.value, pin = pin")
s = s.replace("pairingPending = false", "pairingPending.value = false")
s = s.replace("pairingPending = true", "pairingPending.value = true")

p.write_text(s, encoding="utf-8")
print("Converted pairingPending to explicit MutableState<Boolean> with .value access.")
