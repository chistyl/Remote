from pathlib import Path
import re

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")

pattern = r"var pairingPending(?:\s*:\s*Boolean)?\s+by\s+remember\s*\{\s*mutableStateOf(?:<Boolean>)?\(false\)\s*\}"
s, count = re.subn(pattern, "val pairingPending = remember { androidx.compose.runtime.mutableStateOf(false) }", s, count=1)
if count == 0 and "val pairingPending = remember { androidx.compose.runtime.mutableStateOf(false) }" not in s:
    raise SystemExit("Could not locate pairingPending Compose state declaration")

s = s.replace("pairingPending = pairingPending, pin = pin", "pairingPending = pairingPending.value, pin = pin")
s = re.sub(r"(?<![.\w])pairingPending\s*=\s*(false|true)", r"pairingPending.value = \1", s)

p.write_text(s, encoding="utf-8")
print("Pairing state uses explicit MutableState<Boolean> access.")
