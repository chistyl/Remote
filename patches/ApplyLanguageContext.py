from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
needle = '''class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {'''
replacement = '''class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguagePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {'''
if s.count(needle) != 1:
    raise SystemExit(f"Could not locate MainActivity declaration exactly once: {s.count(needle)}")
p.write_text(s.replace(needle, replacement, 1), encoding="utf-8")
print("Connected saved language preference to Activity configuration.")
