from pathlib import Path

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")

old = '''                    onConnect = {
                        pairingPending = false
                        notice = "正在建立安全配對連線…"
                        scope.launch {
                            try { notice = pairingClient.begin(ip); pairingPending = true }
                            catch (e: Exception) { pairingPending = false; notice = "配對連線失敗：${e.localizedMessage ?: "請確認 IP 與 Wi-Fi"}" }
                        }
                    },'''
new = '''                    onConnect = {
                        when {
                            ip.isBlank() -> notice = "請先輸入電視 IP，或從搜尋結果選擇設備"
                            pairingPending -> notice = "目前已有配對程序進行中，請先完成或重新開始"
                            else -> {
                                pairingPending = false
                                notice = "正在建立安全配對連線…"
                                scope.launch {
                                    try { notice = pairingClient.begin(ip); pairingPending = true }
                                    catch (e: Exception) { pairingPending = false; notice = "配對連線失敗：${e.localizedMessage ?: "請確認 IP 與 Wi-Fi"}" }
                                }
                            }
                        }
                    },'''
if s.count(old) != 1:
    raise SystemExit(f"Connect handler match count: {s.count(old)}")
s = s.replace(old, new, 1)

old = '''                    onFinishPairing = {
                        notice = "正在驗證配對碼…"
                        scope.launch {
                            try {'''
new = '''                    onFinishPairing = {
                        if (pin.length != 6) {
                            notice = "請輸入電視顯示的 6 位數驗證碼"
                        } else {
                            notice = "正在驗證配對碼…"
                            scope.launch {
                            try {'''
if s.count(old) != 1:
    raise SystemExit(f"Finish handler start match count: {s.count(old)}")
s = s.replace(old, new, 1)

old = '''                            } catch (e: Exception) { notice = "配對失敗：${e.localizedMessage ?: "請重新確認驗證碼"}" }
                        }
                    }
                )'''
new = '''                            } catch (e: Exception) { notice = "配對失敗：${e.localizedMessage ?: "請重新確認驗證碼"}" }
                            }
                        }
                    }
                )'''
if s.count(old) != 1:
    raise SystemExit(f"Finish handler end match count: {s.count(old)}")
s = s.replace(old, new, 1)

s = s.replace('Button(onClick = onConnect, enabled = ip.isNotBlank() && !pairingPending,', 'Button(onClick = onConnect,')
s = s.replace('Button(onClick = onFinishPairing, enabled = pin.length == 6,', 'Button(onClick = onFinishPairing,')

p.write_text(s, encoding="utf-8")
print("Pairing controls now respond to taps and report validation/pending states instead of appearing inert.")
