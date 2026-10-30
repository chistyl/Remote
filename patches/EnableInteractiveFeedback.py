from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")

def replace_once(old, new, label):
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, got {count}")
    s = s.replace(old, new, 1)

# Header power control must be actionable, with a useful disconnected state.
replace_once(
'''Box(Modifier.size(42.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {''',
'''Box(Modifier.size(42.dp).clip(CircleShape).background(Panel).clickable {
                    if (!connected) {
                        notice = "請先連接電視，再使用電源控制"
                        tab = 2
                    } else {
                        scope.launch {
                            try { remoteClient.key(26); notice = "已傳送電源指令" }
                            catch (e: Exception) { connected = false; notice = "電源控制失敗：${e.localizedMessage ?: "連線中斷"}" }
                        }
                    }
                }, contentAlignment = Alignment.Center) {''',
"header power icon")

# Remote command buttons remain tappable; disconnected taps navigate to Devices and explain why.
replace_once(
'''0 -> RemoteScreen(enabled = connected, onCommand = { command ->
                    val key = when (command) {''',
'''0 -> RemoteScreen(enabled = connected, onCommand = { command ->
                    if (!connected) {
                        notice = "請先連接電視後再使用遙控功能"
                        tab = 2
                    } else {
                    val key = when (command) {''',
"remote command guard start")
replace_once(
'''                        catch (e: Exception) { connected = false; notice = "遙控失敗：${e.localizedMessage ?: "連線中斷"}" }
                    }
                })''',
'''                        catch (e: Exception) { connected = false; notice = "遙控失敗：${e.localizedMessage ?: "連線中斷"}" }
                    }
                    }
                })''',
"remote command guard end")

# Keyboard send action gives feedback even before pairing.
replace_once(
'''                1 -> KeyboardScreen(text = typedText, onText = { typedText = it }, enabled = connected, onSend = {
                    scope.launch {''',
'''                1 -> KeyboardScreen(text = typedText, onText = { typedText = it }, enabled = connected, onSend = {
                    if (!connected) {
                        notice = "文字已保留；請先連接電視再傳送"
                        tab = 2
                    } else scope.launch {''',
"keyboard send guard")

# Touch controls and action tiles provide tap feedback instead of being inert while disconnected.
replace_once('''Modifier.size(82.dp).clip(CircleShape).background(Accent).clickable(enabled) { onCommand("確認 OK") }''',
             '''Modifier.size(82.dp).clip(CircleShape).background(Accent).clickable { onCommand("確認 OK") }''',
             "OK button")
replace_once('''Modifier.size(70.dp).clip(CircleShape).background(Panel2).clickable(enabled, onClick = onClick)''',
             '''Modifier.size(70.dp).clip(CircleShape).background(Panel2).clickable(onClick = onClick)''',
             "D-pad button")
replace_once('''Column(modifier.height(76.dp).clip(RoundedCornerShape(15.dp)).background(Panel).clickable(enabled, onClick = onClick).padding(8.dp)''',
             '''Column(modifier.height(76.dp).clip(RoundedCornerShape(15.dp)).background(Panel).clickable(onClick = onClick).padding(8.dp)''',
             "action tile")
replace_once('''IconButton(onClick = onSend, enabled = enabled)''',
             '''IconButton(onClick = onSend)''',
             "keyboard send button")

# Touchpad gesture attempts also explain the disconnected state through the same command handler.
replace_once(
'''                    if (enabled) {
                        if (!moved) onCommand("確認 OK")
                        else if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY)) onCommand(if (dragX > 0) "右" else "左")
                        else onCommand(if (dragY > 0) "下" else "上")
                    }''',
'''                    if (!enabled) onCommand("請先連接電視")
                    else if (!moved) onCommand("確認 OK")
                    else if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY)) onCommand(if (dragX > 0) "右" else "左")
                    else onCommand(if (dragY > 0) "下" else "上")''',
"touchpad feedback")

p.write_text(s, encoding="utf-8")
print("Interactive controls updated: disconnected taps now provide feedback; connected controls still send real commands.")
