from pathlib import Path
import xml.etree.ElementTree as ET
root = Path("patches/localization")
items = {
"direction_control":["方向控制","方向控制","DIRECTION CONTROL","方向操作","방향 조작","ควบคุมทิศทาง","ĐIỀU KHIỂN HƯỚNG"],
"touch_area":["觸控操作區","触控操作区","Touch Area","タッチ操作エリア","터치 조작 영역","พื้นที่สัมผัส","Vùng cảm ứng"],
"swipe_tap_ok":["滑動切換方向・點擊 OK","滑动切换方向・点击 OK","Swipe to move · Tap OK","スワイプで移動・タップで決定","스와이프 이동 · 탭하여 확인","ปัดเพื่อเลื่อน · แตะเพื่อเลือก","Vuốt để di chuyển · Chạm OK"],
"hdmi_shortcuts":["HDMI 輸入快捷鍵（依電視支援）","HDMI 输入快捷键（视电视支持情况）","HDMI input shortcuts (TV support varies)","HDMI入力ショートカット（テレビの対応による）","HDMI 입력 바로가기 (TV 지원에 따름)","ทางลัด HDMI (ขึ้นอยู่กับทีวี)","Phím tắt HDMI (tùy TV hỗ trợ)"],
"hdmi_note":["提示：HDMI 快捷鍵及電源鍵是否有效，取決於電視韌體；觸控板以方向鍵事件模擬焦點移動。","提示：HDMI 快捷键和电源键是否有效取决于电视固件；触控板通过方向键事件模拟焦点移动。","Note: HDMI and power shortcuts depend on TV firmware. The touchpad simulates focus movement with directional keys.","注意：HDMIと電源のショートカットはテレビのファームウェアに依存します。タッチパッドは方向キーでフォーカス移動を模擬します。","참고: HDMI 및 전원 바로가기는 TV 펌웨어에 따라 다릅니다. 터치패드는 방향키로 포커스 이동을 모방합니다.","หมายเหตุ: ปุ่มลัด HDMI และปุ่มเปิด/ปิดขึ้นอยู่กับเฟิร์มแวร์ทีวี ทัชแพดจำลองการเลื่อนโฟกัสด้วยปุ่มทิศทาง","Lưu ý: phím tắt HDMI và nguồn tùy firmware TV. Bàn di mô phỏng di chuyển tiêu điểm bằng phím điều hướng."],
"phone_keyboard":["手機鍵盤","手机键盘","PHONE KEYBOARD","スマホキーボード","휴대폰 키보드","แป้นพิมพ์โทรศัพท์","BÀN PHÍM ĐIỆN THOẠI"],
"keyboard_placeholder":["輸入要在電視搜尋的文字…","输入要在电视搜索的文字…","Enter text to search on TV…","テレビで検索する文字を入力…","TV에서 검색할 텍스트 입력…","ป้อนข้อความเพื่อค้นหาบนทีวี…","Nhập nội dung cần tìm trên TV…"],
"keyboard_help":["使用手機原生鍵盤輸入，將文字傳送至電視目前焦點欄位。","使用手机原生键盘输入，将文字发送到电视当前焦点字段。","Type with your phone keyboard and send text to the TV's focused field.","スマホのキーボードで入力し、テレビのフォーカス中の欄に送信します。","휴대폰 키보드로 입력한 텍스트를 TV의 현재 포커스 입력란으로 전송합니다.","พิมพ์ด้วยแป้นพิมพ์โทรศัพท์แล้วส่งไปยังช่องที่เลือกบนทีวี","Nhập bằng bàn phím điện thoại và gửi đến ô đang được chọn trên TV."],
"device_connection":["設備連線","设备连接","DEVICE CONNECTION","デバイス接続","기기 연결","การเชื่อมต่ออุปกรณ์","KẾT NỐI THIẾT BỊ"],
"search_nearby":["搜尋附近的 Google TV","搜索附近的 Google TV","Find nearby Google TV","近くの Google TV を検索","주변 Google TV 검색","ค้นหา Google TV ใกล้เคียง","Tìm Google TV gần đây"],
"same_wifi":["透過同一個 Wi-Fi 網路探索設備","通过同一 Wi-Fi 网络发现设备","Discover devices on the same Wi-Fi network","同じ Wi-Fi ネットワーク上のデバイスを検出","동일한 Wi-Fi 네트워크에서 기기 검색","ค้นหาอุปกรณ์ในเครือข่าย Wi-Fi เดียวกัน","Tìm thiết bị trên cùng mạng Wi-Fi"],
"searching":["搜尋中…","搜索中…","Searching…","検索中…","검색 중…","กำลังค้นหา…","Đang tìm…"],
"search":["搜尋","搜索","Search","検索","검색","ค้นหา","Tìm kiếm"],
"reconnect":["重新連線","重新连接","Reconnect","再接続","다시 연결","เชื่อมต่ออีกครั้ง","Kết nối lại"],
"exploring_lan":["正在探索區域網路…","正在探索局域网…","Scanning local network…","ローカルネットワークを検索中…","로컬 네트워크 검색 중…","กำลังสแกนเครือข่ายท้องถิ่น…","Đang quét mạng nội bộ…"],
"manual_ip":["手動輸入 IP 位址","手动输入 IP 地址","Enter IP address manually","IPアドレスを手動入力","IP 주소 직접 입력","ป้อนที่อยู่ IP ด้วยตนเอง","Nhập địa chỉ IP thủ công"],
"example_ip":["例如 192.168.1.100","例如 192.168.1.100","e.g. 192.168.1.100","例: 192.168.1.100","예: 192.168.1.100","เช่น 192.168.1.100","ví dụ 192.168.1.100"],
"start_pairing":["開始配對","开始配对","Start pairing","ペア設定を開始","페어링 시작","เริ่มจับคู่","Bắt đầu ghép đôi"],
"pairing_code":["輸入電視顯示的 6 位數十六進位驗證碼","输入电视显示的 6 位十六进制验证码","Enter the 6-character hexadecimal code shown on TV","テレビに表示された6桁の16進数コードを入力","TV에 표시된 6자리 16진수 코드 입력","ป้อนรหัสฐานสิบหก 6 หลักที่แสดงบนทีวี","Nhập mã thập lục phân 6 ký tự hiển thị trên TV"],
"example_pin":["例如 A1B2C3","例如 A1B2C3","e.g. A1B2C3","例: A1B2C3","예: A1B2C3","เช่น A1B2C3","ví dụ A1B2C3"],
"confirm_pairing":["確認配對","确认配对","Confirm pairing","ペア設定を確認","페어링 확인","ยืนยันการจับคู่","Xác nhận ghép đôi"],
"pairing_help":["首次配對時，請依電視畫面顯示的驗證碼完成授權。憑證會保存在手機 App 私有空間。","首次配对时，请根据电视画面显示的验证码完成授权。凭据会保存在手机 App 私有空间。","For first-time pairing, authorize using the code shown on your TV. Credentials are stored in the app's private storage.","初回ペア設定では、テレビに表示されたコードで認証してください。資格情報はアプリの専用領域に保存されます。","처음 페어링할 때 TV에 표시된 코드로 승인하세요. 인증 정보는 앱의 비공개 저장소에 보관됩니다.","จับคู่ครั้งแรกโดยใช้รหัสที่แสดงบนทีวี ข้อมูลรับรองจะถูกเก็บไว้ในพื้นที่ส่วนตัวของแอป","Khi ghép đôi lần đầu, hãy xác thực bằng mã trên TV. Thông tin xác thực được lưu trong vùng riêng của ứng dụng."]
}
folders=["values","values-zh-rCN","values-en","values-ja","values-ko","values-th","values-vi"]
for idx,folder in enumerate(folders):
    path=root/folder/"strings.xml"
    tree=ET.parse(path); res=tree.getroot()
    existing={e.attrib.get("name") for e in res.findall("string")}
    for key,translations in items.items():
        if key not in existing:
            node=ET.SubElement(res,"string",{"name":key}); node.text=translations[idx]
    ET.indent(tree,space="    "); tree.write(path,encoding="utf-8",xml_declaration=True)
p=Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s=p.read_text(encoding="utf-8")
replacements={
'Text("DIRECTION CONTROL"':'Text(stringResource(R.string.direction_control)',
'Text("觸控操作區"':'Text(stringResource(R.string.touch_area)',
'Text("滑動切換方向・點擊 OK"':'Text(stringResource(R.string.swipe_tap_ok)',
'Text("HDMI 輸入快捷鍵（依電視支援）"':'Text(stringResource(R.string.hdmi_shortcuts)',
'Text("提示：HDMI 快捷鍵及電源鍵是否有效，取決於電視韌體；觸控板以方向鍵事件模擬焦點移動。"':'Text(stringResource(R.string.hdmi_note)',
'Text("PHONE KEYBOARD"':'Text(stringResource(R.string.phone_keyboard)',
'Text("輸入要在電視搜尋的文字…", color = Muted)':'Text(stringResource(R.string.keyboard_placeholder), color = Muted)',
'Text("使用手機原生鍵盤輸入，將文字傳送至電視目前焦點欄位。"':'Text(stringResource(R.string.keyboard_help)',
'Text("DEVICE CONNECTION"':'Text(stringResource(R.string.device_connection)',
'Text("搜尋附近的 Google TV"':'Text(stringResource(R.string.search_nearby)',
'Text("透過同一個 Wi-Fi 網路探索設備"':'Text(stringResource(R.string.same_wifi)',
'if (searching) "搜尋中…" else "搜尋"':'if (searching) stringResource(R.string.searching) else stringResource(R.string.search)',
'Text("重新連線"':'Text(stringResource(R.string.reconnect)',
'Text("正在探索區域網路…"':'Text(stringResource(R.string.exploring_lan)',
'Text("手動輸入 IP 位址"':'Text(stringResource(R.string.manual_ip)',
'Text("例如 192.168.1.100")':'Text(stringResource(R.string.example_ip))',
'Text("開始配對"':'Text(stringResource(R.string.start_pairing)',
'Text("輸入電視顯示的 6 位數十六進位驗證碼"':'Text(stringResource(R.string.pairing_code)',
'Text("例如 A1B2C3")':'Text(stringResource(R.string.example_pin))',
'Text("確認配對"':'Text(stringResource(R.string.confirm_pairing)',
'Text("首次配對時，請依電視畫面顯示的驗證碼完成授權。憑證會保存在手機 App 私有空間。"':'Text(stringResource(R.string.pairing_help)'
}
for old,new in replacements.items():
    if old not in s: print("WARN not found:",old)
    else: s=s.replace(old,new)
p.write_text(s,encoding="utf-8")
print("Localized remote, keyboard, discovery and pairing screens in seven languages.")

# Add language settings copy and translations; resources must be copied AFTER adding new keys.
settings_items = {
    "settings_language_title": ["語言 / LANGUAGE", "语言 / LANGUAGE", "LANGUAGE", "言語 / LANGUAGE", "언어 / LANGUAGE", "ภาษา / LANGUAGE", "NGÔN NGỮ / LANGUAGE"],
    "settings_language_desc": ["選擇 App 顯示語言；預設跟隨手機系統語言。", "选择 App 显示语言；默认跟随手机系统语言。", "Choose the app language. By default, it follows your device language.", "アプリの表示言語を選択します。既定では端末の言語に従います。", "앱 표시 언어를 선택하세요. 기본값은 기기 언어를 따릅니다.", "เลือกภาษาของแอป โดยค่าเริ่มต้นจะใช้ภาษาของอุปกรณ์", "Chọn ngôn ngữ ứng dụng. Mặc định theo ngôn ngữ thiết bị."],
    "settings_language_label": ["介面語言", "界面语言", "App language", "アプリの言語", "앱 언어", "ภาษาแอป", "Ngôn ngữ ứng dụng"],
    "follow_system": ["跟隨系統語言", "跟随系统语言", "Follow system", "システム設定に従う", "시스템 설정 따르기", "ตามภาษาระบบ", "Theo hệ thống"]
}
for idx, folder in enumerate(folders):
    path = root / folder / "strings.xml"
    tree = ET.parse(path); res = tree.getroot()
    existing = {e.attrib.get("name") for e in res.findall("string")}
    for key, translations in settings_items.items():
        if key not in existing:
            node = ET.SubElement(res, "string", {"name": key}); node.text = translations[idx]
    ET.indent(tree, space="    "); tree.write(path, encoding="utf-8", xml_declaration=True)
    target = Path("GoogleTVRemote/app/src/main/res") / folder
    target.mkdir(parents=True, exist_ok=True)
    import shutil
    shutil.copy(path, target / "strings.xml")

# Localize the language settings screen itself.
settings = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/AppLanguageSettings.kt")
if settings.exists():
    t = settings.read_text(encoding="utf-8")
    if "import androidx.compose.ui.res.stringResource" not in t:
        t = t.replace("import androidx.compose.ui.platform.LocalContext", "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.stringResource")
    t = t.replace('Choice(null, "跟隨系統語言")', 'Choice(null, "Follow system")')
    t = t.replace('?: "跟隨系統語言"', '?: context.getString(R.string.follow_system)')
    t = t.replace('Text("語言 / LANGUAGE", fontSize = 12.sp)', 'Text(stringResource(R.string.settings_language_title), fontSize = 12.sp)')
    t = t.replace('Text("選擇 App 顯示語言；預設跟隨手機系統語言。", fontSize = 14.sp)', 'Text(stringResource(R.string.settings_language_desc), fontSize = 14.sp)')
    t = t.replace('label = { Text("介面語言") }', 'label = { Text(stringResource(R.string.settings_language_label)) }')
    settings.write_text(t, encoding="utf-8")

# Complete the remaining always-visible labels in the remote and device screens.
more = {
"paired_devices_count":["已配對設備（%1$d）","已配对设备（%1$d）","Paired devices (%1$d)","ペア設定済みデバイス（%1$d）","페어링된 기기 (%1$d)","อุปกรณ์ที่จับคู่แล้ว (%1$d)","Thiết bị đã ghép đôi (%1$d)"],
"found_devices_count":["搜尋到的設備（%1$d）","搜索到的设备（%1$d）","Discovered devices (%1$d)","検出されたデバイス（%1$d）","검색된 기기 (%1$d)","อุปกรณ์ที่พบ (%1$d)","Thiết bị tìm thấy (%1$d)"],
"volume_down":["音量 −","音量 −","Volume −","音量 −","볼륨 −","ลดเสียง","Giảm âm lượng"],
"volume_up":["音量 +","音量 +","Volume +","音量 +","볼륨 +","เพิ่มเสียง","Tăng âm lượng"],
"local_network_privacy":["區域網路連線・不需雲端帳號","局域网连接・无需云端账号","LOCAL NETWORK · NO CLOUD ACCOUNT","ローカルネットワーク接続・クラウドアカウント不要","로컬 네트워크 연결 · 클라우드 계정 불필요","เชื่อมต่อเครือข่ายท้องถิ่น · ไม่ต้องใช้บัญชีคลาวด์","MẠNG NỘI BỘ · KHÔNG CẦN TÀI KHOẢN ĐÁM MÂY"],
"touchpad_title":["觸控板操作","触控板操作","TOUCHPAD CONTROL","タッチパッド操作","터치패드 조작","ควบคุมทัชแพด","ĐIỀU KHIỂN BÀN DI"],
"tv_remote_title":["智慧遙控器","智能遥控器","Smart Remote","スマートリモコン","스마트 리모컨","รีโมทอัจฉริยะ","Điều khiển thông minh"],
"nav_settings":["設定","设置","Settings","設定","설정","การตั้งค่า","Cài đặt"]
}
for idx, folder in enumerate(folders):
    path = root / folder / "strings.xml"
    tree = ET.parse(path); res = tree.getroot()
    existing = {e.attrib.get("name") for e in res.findall("string")}
    for key, translations in more.items():
        if key not in existing:
            node = ET.SubElement(res, "string", {"name": key}); node.text = translations[idx]
    ET.indent(tree, space="    "); tree.write(path, encoding="utf-8", xml_declaration=True)
    import shutil
    target = Path("GoogleTVRemote/app/src/main/res") / folder
    target.mkdir(parents=True, exist_ok=True)
    shutil.copy(path, target / "strings.xml")

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
more_replacements = {
'Text("遙控器")':'Text(stringResource(R.string.nav_remote))',
'Text("鍵盤")':'Text(stringResource(R.string.nav_keyboard))',
'Text("設備")':'Text(stringResource(R.string.nav_devices))',
'Text("智慧遙控器",':'Text(stringResource(R.string.tv_remote_title),',
'Text("管理",':'Text(stringResource(R.string.manage),',
'Text("LOCAL NETWORK  •  NO CLOUD ACCOUNT"':'Text(stringResource(R.string.local_network_privacy)',
'Text("觸控板  /  滑動移動焦點，點擊確認"':'Text(stringResource(R.string.touchpad_title)',
'ActionTile("返回",':'ActionTile(stringResource(R.string.back),',
'ActionTile("首頁",':'ActionTile(stringResource(R.string.home),',
'ActionTile("最近使用",':'ActionTile(stringResource(R.string.recent),',
'ActionTile("音量 −",':'ActionTile(stringResource(R.string.volume_down),',
'ActionTile("靜音",':'ActionTile(stringResource(R.string.mute),',
'ActionTile("音量 +",':'ActionTile(stringResource(R.string.volume_up),',
'Text("已配對設備（${savedDevices.size}）"':'Text(stringResource(R.string.paired_devices_count, savedDevices.size)',
'Text("搜尋到的設備（${devices.size}）"':'Text(stringResource(R.string.found_devices_count, devices.size)'
}
for old, new in more_replacements.items():
    if old not in s: print("WARN missing static label:", old)
    else: s = s.replace(old, new)
p.write_text(s, encoding="utf-8")

# Runtime connection and pairing notices: these change while the app is in use.
runtime = {
"status_not_connected":["尚未連線","尚未连接","Not connected","未接続","연결되지 않음","ยังไม่ได้เชื่อมต่อ","Chưa kết nối"],
"status_paired_device":"已配對：%1$s|已配对：%1$s|Paired: %1$s|ペア設定済み：%1$s|페어링됨: %1$s|จับคู่แล้ว: %1$s|Đã ghép đôi: %1$s".split("|"),
"status_tv_disconnected":["未連接電視","未连接电视","TV not connected","テレビ未接続","TV 연결 안 됨","ไม่ได้เชื่อมต่อทีวี","TV chưa kết nối"],
"status_remote_secure":["Remote v2 安全連線中","Remote v2 安全连接中","Remote v2 secure connection active","Remote v2 セキュア接続中","Remote v2 보안 연결 중","กำลังเชื่อมต่ออย่างปลอดภัยด้วย Remote v2","Đang kết nối bảo mật Remote v2"],
"status_sent":"已傳送：%1$s|已发送：%1$s|Sent: %1$s|送信済み：%1$s|전송됨: %1$s|ส่งแล้ว: %1$s|Đã gửi: %1$s".split("|"),
"status_remote_failed":"遙控失敗：%1$s|遥控失败：%1$s|Remote command failed: %1$s|リモート操作に失敗：%1$s|리모컨 명령 실패: %1$s|คำสั่งรีโมทล้มเหลว: %1$s|Lệnh điều khiển thất bại: %1$s".split("|"),
"status_connection_lost":["連線中斷","连接中断","Connection lost","接続が切断されました","연결 끊김","การเชื่อมต่อขาดหาย","Mất kết nối"],
"status_text_sent":["已透過電視 IME 傳送文字","已通过电视 IME 发送文字","Text sent through the TV IME","テレビのIME経由でテキストを送信しました","TV IME를 통해 텍스트 전송됨","ส่งข้อความผ่าน IME ของทีวีแล้ว","Đã gửi văn bản qua IME của TV"],
"status_text_failed":"文字傳送失敗：%1$s|文字发送失败：%1$s|Text send failed: %1$s|テキスト送信失敗：%1$s|텍스트 전송 실패: %1$s|ส่งข้อความไม่สำเร็จ: %1$s|Gửi văn bản thất bại: %1$s".split("|"),
"status_searching_tv":["正在搜尋同一個 Wi-Fi 網路中的 Google TV…","正在搜索同一 Wi-Fi 网络中的 Google TV…","Searching for Google TV on this Wi-Fi network…","同じ Wi-Fi ネットワーク上の Google TV を検索中…","같은 Wi-Fi 네트워크에서 Google TV 검색 중…","กำลังค้นหา Google TV ในเครือข่าย Wi-Fi นี้…","Đang tìm Google TV trên mạng Wi-Fi này…"],
"status_selected_device":"已選取 %1$s（%2$s），可以開始配對|已选择 %1$s（%2$s），可以开始配对|Selected %1$s (%2$s). Ready to pair.|%1$s（%2$s）を選択しました。ペア設定できます。|%1$s(%2$s) 선택됨. 페어링할 수 있습니다.|เลือก %1$s (%2$s) แล้ว พร้อมจับคู่|Đã chọn %1$s (%2$s). Có thể ghép đôi.".split("|"),
"status_saved_pairing":["正在使用已保存的配對憑證連線…","正在使用已保存的配对凭据连接…","Connecting with saved pairing credentials…","保存済みのペア設定情報で接続中…","저장된 페어링 정보로 연결 중…","กำลังเชื่อมต่อด้วยข้อมูลจับคู่ที่บันทึกไว้…","Đang kết nối bằng thông tin ghép đôi đã lưu…"],
"status_reconnected":"已重新連線：%1$s|已重新连接：%1$s|Reconnected: %1$s|再接続しました：%1$s|다시 연결됨: %1$s|เชื่อมต่ออีกครั้งแล้ว: %1$s|Đã kết nối lại: %1$s".split("|"),
"status_tv_interrupted":"電視連線中斷：%1$s|电视连接中断：%1$s|TV connection interrupted: %1$s|テレビ接続が中断されました：%1$s|TV 연결 중단: %1$s|การเชื่อมต่อทีวีขาดหาย: %1$s|Kết nối TV bị gián đoạn: %1$s".split("|"),
"status_reconnect_failed":"重新連線失敗：%1$s|重新连接失败：%1$s|Reconnect failed: %1$s|再接続に失敗：%1$s|다시 연결 실패: %1$s|เชื่อมต่ออีกครั้งไม่สำเร็จ: %1$s|Kết nối lại thất bại: %1$s".split("|"),
"status_pairing_connect":["正在建立安全配對連線…","正在建立安全配对连接…","Establishing secure pairing connection…","セキュアなペア設定接続を確立中…","보안 페어링 연결 설정 중…","กำลังสร้างการเชื่อมต่อจับคู่ที่ปลอดภัย…","Đang thiết lập kết nối ghép đôi bảo mật…"],
"status_pairing_failed":"配對連線失敗：%1$s|配对连接失败：%1$s|Pairing connection failed: %1$s|ペア設定接続に失敗：%1$s|페어링 연결 실패: %1$s|การเชื่อมต่อจับคู่ล้มเหลว: %1$s|Kết nối ghép đôi thất bại: %1$s".split("|"),
"status_check_ip_wifi":["請確認 IP 與 Wi-Fi","请确认 IP 和 Wi-Fi","Check the IP address and Wi-Fi","IPアドレスとWi-Fiを確認してください","IP 주소와 Wi-Fi를 확인하세요","โปรดตรวจสอบ IP และ Wi-Fi","Vui lòng kiểm tra IP và Wi-Fi"],
"status_verifying_pin":["正在驗證配對碼…","正在验证配对码…","Verifying pairing code…","ペアコードを確認中…","페어링 코드 확인 중…","กำลังตรวจสอบรหัสจับคู่…","Đang xác minh mã ghép đôi…"],
"status_pairing_success":["配對成功，正在啟動遙控通道…","配对成功，正在启动遥控通道…","Pairing successful. Starting remote channel…","ペア設定成功。リモートチャネルを起動中…","페어링 성공. 리모컨 채널 시작 중…","จับคู่สำเร็จ กำลังเริ่มช่องทางรีโมท…","Ghép đôi thành công. Đang khởi động kênh điều khiển…"],
"status_remote_connected":["Remote v2 已連線","Remote v2 已连接","Remote v2 connected","Remote v2 接続済み","Remote v2 연결됨","เชื่อมต่อ Remote v2 แล้ว","Remote v2 đã kết nối"],
"status_pairing_error":"配對失敗：%1$s|配对失败：%1$s|Pairing failed: %1$s|ペア設定に失敗：%1$s|페어링 실패: %1$s|จับคู่ล้มเหลว: %1$s|Ghép đôi thất bại: %1$s".split("|"),
"status_repair_hint":["請重新配對","请重新配对","Please pair again","再度ペア設定してください","다시 페어링하세요","โปรดจับคู่ใหม่อีกครั้ง","Vui lòng ghép đôi lại"],
"status_pair_code_hint":["請重新確認驗證碼","请重新确认验证码","Check the pairing code again","ペアコードをもう一度確認してください","페어링 코드를 다시 확인하세요","โปรดตรวจสอบรหัสจับคู่อีกครั้ง","Vui lòng kiểm tra lại mã ghép đôi"]
}
for idx, folder in enumerate(folders):
    path = root / folder / "strings.xml"
    tree = ET.parse(path); res = tree.getroot()
    existing = {e.attrib.get("name") for e in res.findall("string")}
    for key, translations in runtime.items():
        if key not in existing:
            node = ET.SubElement(res, "string", {"name": key}); node.text = translations[idx]
    ET.indent(tree, space="    "); tree.write(path, encoding="utf-8", xml_declaration=True)
    import shutil
    target = Path("GoogleTVRemote/app/src/main/res") / folder
    target.mkdir(parents=True, exist_ok=True)
    shutil.copy(path, target / "strings.xml")

p = Path("GoogleTVRemote/app/src/main/java/com/chi/tvremote/MainActivity.kt")
s = p.read_text(encoding="utf-8")
runtime_replacements = {
'var deviceName by remember { mutableStateOf("尚未連線") }':'var deviceName by remember { mutableStateOf(context.getString(R.string.status_not_connected)) }',
'else if (paired) "已配對：$deviceName" else "未連接電視"':'else if (paired) context.getString(R.string.status_paired_device, deviceName) else context.getString(R.string.status_tv_disconnected)',
'if (connected) "Remote v2 安全連線中"':'if (connected) context.getString(R.string.status_remote_secure)',
'notice = "已傳送：$command"':'notice = context.getString(R.string.status_sent, command)',
'notice = "遙控失敗：${e.localizedMessage ?: "連線中斷"}"':'notice = context.getString(R.string.status_remote_failed, e.localizedMessage ?: context.getString(R.string.status_connection_lost))',
'notice = "已透過電視 IME 傳送文字"':'notice = context.getString(R.string.status_text_sent)',
'notice = "文字傳送失敗：${e.localizedMessage ?: "連線中斷"}"':'notice = context.getString(R.string.status_text_failed, e.localizedMessage ?: context.getString(R.string.status_connection_lost))',
'notice = "正在搜尋同一個 Wi-Fi 網路中的 Google TV…"':'notice = context.getString(R.string.status_searching_tv)',
'notice = "已選取 ${device.name}（${device.host}），可以開始配對"':'notice = context.getString(R.string.status_selected_device, device.name, device.host)',
'notice = "正在使用已保存的配對憑證連線…"':'notice = context.getString(R.string.status_saved_pairing)',
'notice = "已重新連線：${saved.name}"':'notice = context.getString(R.string.status_reconnected, saved.name)',
'notice = "電視連線中斷：$reason"':'notice = context.getString(R.string.status_tv_interrupted, reason)',
'notice = "重新連線失敗：${e.localizedMessage ?: "請重新配對"}"':'notice = context.getString(R.string.status_reconnect_failed, e.localizedMessage ?: context.getString(R.string.status_repair_hint))',
'notice = "正在建立安全配對連線…"':'notice = context.getString(R.string.status_pairing_connect)',
'notice = "配對連線失敗：${e.localizedMessage ?: "請確認 IP 與 Wi-Fi"}"':'notice = context.getString(R.string.status_pairing_failed, e.localizedMessage ?: context.getString(R.string.status_check_ip_wifi))',
'notice = "正在驗證配對碼…"':'notice = context.getString(R.string.status_verifying_pin)',
'notice = "配對成功，正在啟動遙控通道…"':'notice = context.getString(R.string.status_pairing_success)',
'notice = "Remote v2 已連線"':'notice = context.getString(R.string.status_remote_connected)',
'notice = "配對失敗：${e.localizedMessage ?: "請重新確認驗證碼"}"':'notice = context.getString(R.string.status_pairing_error, e.localizedMessage ?: context.getString(R.string.status_pair_code_hint))'
}
for old, new in runtime_replacements.items():
    if old not in s: print("WARN runtime notice not found:", old)
    else: s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
