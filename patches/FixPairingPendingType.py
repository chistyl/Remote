from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
old = "var pairingPending by remember { mutableStateOf(false) }"
new = "var pairingPending: Boolean by remember { mutableStateOf<Boolean>(false) }"
count = s.count(old)
if count != 1:
    raise SystemExit(f"Expected one pairingPending state declaration, found {count}")
p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("Applied explicit Boolean type to pairingPending Compose state.")
