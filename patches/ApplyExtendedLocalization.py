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
