from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")

marker = "private fun RemoteApp() {"
if s.count(marker) != 1:
    raise SystemExit("Could not locate RemoteApp declaration exactly once")

# Move LocalContext.current to the top of RemoteApp, before remember state initializers.
s = s.replace(marker + "\n", marker + "\n    val context = LocalContext.current\n", 1)
later = "    val context = LocalContext.current\n"
# Remove the second occurrence (the one originally below coroutine scope).
first = s.find(later, s.find(marker) + len(marker))
second = s.find(later, first + len(later))
if second < 0:
    raise SystemExit("Could not locate the later duplicate context declaration")
s = s[:second] + s[second + len(later):]

s = s.replace("                            pairingPending -> notice =", "                            pairingPending.value -> notice =")
p.write_text(s, encoding="utf-8")
print("Moved Compose context before localized state initialization and corrected pairingPending Boolean check.")
