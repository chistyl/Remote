from pathlib import Path
import shutil

root = Path("GoogleTVRemote/app/src/main")
res = root / "res"
(res / "drawable").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
shutil.copy("patches/icon/ic_launcher_foreground.xml", res / "drawable/ic_launcher_foreground.xml")
shutil.copy("patches/icon/ic_launcher_background.xml", res / "drawable/ic_launcher_background.xml")
shutil.copy("patches/icon/ic_launcher.xml", res / "mipmap-anydpi-v26/ic_launcher.xml")
shutil.copy("patches/icon/ic_launcher.xml", res / "mipmap-anydpi-v26/ic_launcher_round.xml")

manifest = root / "AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
needle = '<application\n'
if needle not in text:
    raise SystemExit("Could not find application tag in AndroidManifest.xml")
if 'android:icon="@mipmap/ic_launcher"' not in text:
    text = text.replace(needle, needle + '        android:icon="@mipmap/ic_launcher"\n        android:roundIcon="@mipmap/ic_launcher_round"\n', 1)
manifest.write_text(text, encoding="utf-8")
print("Installed monochrome black-background / white remote adaptive launcher icon.")
