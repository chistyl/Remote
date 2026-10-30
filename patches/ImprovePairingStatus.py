from pathlib import Path
import re

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# Language navigation rewrites the fallback branch to an explicit tab 2 branch.
pattern = r"((?:else|2)\s*->\s*DeviceScreen\(\s*)"
s, n = re.subn(pattern, r"\1status = notice,\n                    ", s, count=1)
if n != 1:
    raise SystemExit(f"Could not locate DeviceScreen call site (matches={n})")

pattern = r"private fun DeviceScreen\("
s, n = re.subn(pattern, "private fun DeviceScreen(status: String, ", s, count=1)
if n != 1:
    raise SystemExit(f"Could not locate DeviceScreen signature (matches={n})")

header = '    Text("DEVICE CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)'
if header not in s:
    raise SystemExit("Could not locate DeviceScreen header")

card = '''    Text("DEVICE CONNECTION", color = Muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(12.dp),
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
    }'''
s = s.replace(header, card, 1)

s, n = re.subn(r'Button\(onClick = onConnect, enabled = [^,]+,', 'Button(onClick = onConnect,', s, count=1)
if n != 1:
    raise SystemExit("Could not locate pairing button")

p.write_text(s, encoding="utf-8")
print("Added inline pairing status, progress indicator, and responsive pairing button.")
