from pathlib import Path
import shutil

base = Path("GoogleTVRemote/app/src/main/res")
for folder in ["values", "values-zh-rCN", "values-en", "values-ja", "values-ko", "values-th", "values-vi"]:
    target = base / folder
    target.mkdir(parents=True, exist_ok=True)
    shutil.copy(Path("patches/localization") / folder / "strings.xml", target / "strings.xml")

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
if "import androidx.compose.ui.res.stringResource" not in s:
    s = s.replace("import androidx.compose.ui.platform.LocalContext", "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.stringResource")

replacements = {
    'Text("遙控器")':'Text(stringResource(R.string.nav_remote))',
    'Text("鍵盤")':'Text(stringResource(R.string.nav_keyboard))',
    'Text("設備")':'Text(stringResource(R.string.nav_devices))',
    'Text("智慧遙控器",':'Text(stringResource(R.string.smart_remote),',
    'else "未連接電視"':'else stringResource(R.string.tv_disconnected)',
    'else if (paired) "已配對；請重新連線"':'else if (paired) stringResource(R.string.paired_reconnect)',
    'Text("管理",':'Text(stringResource(R.string.manage),',
    'ActionTile("返回",':'ActionTile(stringResource(R.string.back),',
    'ActionTile("首頁",':'ActionTile(stringResource(R.string.home),',
    'ActionTile("最近使用",':'ActionTile(stringResource(R.string.recent),',
    'ActionTile("靜音",':'ActionTile(stringResource(R.string.mute),',
    'ActionTile("播放 / 暫停",':'ActionTile(stringResource(R.string.play_pause),',
    'ActionTile("選單",':'ActionTile(stringResource(R.string.menu),',
    'ActionTile("電源",':'ActionTile(stringResource(R.string.power),',
    'Text("觸控板  /  滑動移動焦點，點擊確認",':'Text(stringResource(R.string.touchpad_hint),',
    'notice = "正在搜尋同一個 Wi-Fi 網路中的 Google TV…"':'notice = context.getString(R.string.searching_tv)',
    'mutableStateOf("請先搜尋或輸入電視 IP 位址")':'mutableStateOf(context.getString(R.string.search_tv_hint))'
}
for old, new in replacements.items():
    if old not in s:
        print("WARN not found:", old)
    else:
        s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
print("Copied seven locale resource sets and localized the primary navigation, header and remote controls.")
