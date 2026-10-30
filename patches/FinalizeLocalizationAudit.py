from pathlib import Path
import xml.etree.ElementTree as ET

main = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
source = main.read_text(encoding="utf-8")
old = 'label = { Text("設定") }'
new = 'label = { Text(stringResource(R.string.nav_settings)) }'
if old in source:
    source = source.replace(old, new, 1)
elif new not in source:
    raise SystemExit("Settings navigation item not found")

if "import androidx.compose.ui.res.stringResource" not in source:
    source = source.replace("import androidx.compose.ui.platform.LocalContext", "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.stringResource")

folders = ["values", "values-zh-rCN", "values-en", "values-ja", "values-ko", "values-th", "values-vi"]
key_sets = {}
for folder in folders:
    path = Path("GoogleTVRemote/app/src/main/res") / folder / "strings.xml"
    root = ET.parse(path).getroot()
    key_sets[folder] = {node.attrib["name"] for node in root.findall("string")}
base = key_sets["values"]
for folder, keys in key_sets.items():
    if keys != base:
        raise SystemExit("Locale resource key mismatch in " + folder)
if "nav_settings" not in base:
    raise SystemExit("nav_settings resource missing")
main.write_text(source, encoding="utf-8")
print("Seven locale resources have matching keys; Settings navigation localized.")
