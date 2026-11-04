package com.chi.tvremote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalAppLanguage = compositionLocalOf { "zh-TW" }

private data class Words(val zh: String, val en: String, val vi: String)

private val words = mapOf(
    "connected" to Words("● 已連線", "● Connected", "● Đã kết nối"),
    "connecting" to Words("● 啟用中", "● Activating", "● Đang kích hoạt"),
    "disconnected" to Words("● 未連線", "● Disconnected", "● Chưa kết nối"),
    "pair" to Words("配對", "Pair", "Ghép nối"),
    "remote" to Words("遙控器", "Remote", "Điều khiển"),
    "mouse" to Words("滑鼠", "Touchpad", "Bàn di chuột"),
    "connect_tv" to Words("連接你的電視", "Connect your TV", "Kết nối TV của bạn"),
    "wifi_help" to Words("手機與電視連接同一個 Wi-Fi。輸入電視的 IP 位址，按下開始配對。", "Connect your phone and TV to the same Wi-Fi. Enter the TV IP address and start pairing.", "Kết nối điện thoại và TV cùng Wi-Fi. Nhập địa chỉ IP của TV rồi bắt đầu ghép nối."),
    "saved_tvs" to Words("已配對的電視", "Paired TVs", "TV đã ghép nối"),
    "quick_connect" to Words("快速連線", "Quick connect", "Kết nối nhanh"),
    "remove" to Words("移除", "Remove", "Xóa"),
    "nearby_tvs" to Words("附近的電視", "Nearby TVs", "TV gần đây"),
    "searching" to Words("正在搜尋同一 Wi-Fi 網路上的電視…", "Searching for TVs on this Wi-Fi…", "Đang tìm TV trong mạng Wi-Fi này…"),
    "tv_ip" to Words("電視 IP 位址", "TV IP address", "Địa chỉ IP TV"),
    "pairing" to Words("正在配對…", "Pairing…", "Đang ghép nối…"),
    "connecting_text" to Words("正在連線…", "Connecting…", "Đang kết nối…"),
    "paired" to Words("已完成配對", "Paired", "Đã ghép nối"),
    "retry_pair" to Words("重新配對", "Pair again", "Ghép nối lại"),
    "start_pair" to Words("開始配對", "Start pairing", "Bắt đầu ghép nối"),
    "pair_method" to Words("配對方式", "How to pair", "Cách ghép nối"),
    "step1" to Words("1. 電視畫面會顯示配對碼。", "1. A pairing code appears on the TV.", "1. Mã ghép nối sẽ hiển thị trên TV."),
    "step2" to Words("2. 在手機輸入配對碼。", "2. Enter the code on your phone.", "2. Nhập mã trên điện thoại."),
    "step3" to Words("3. 配對成功後即可使用遙控器。", "3. Use the remote after pairing.", "3. Có thể dùng điều khiển sau khi ghép nối."),
    "pair_once" to Words("只需配對一次；不需額外電視端設定。", "Pair once; no extra TV-side setup required.", "Chỉ cần ghép nối một lần; không cần thiết lập thêm trên TV."),
    "enter_code" to Words("輸入電視配對碼", "Enter TV pairing code", "Nhập mã ghép nối TV"),
    "six_code" to Words("6 位十六進位配對碼", "6-digit hexadecimal pairing code", "Mã ghép nối hệ thập lục phân gồm 6 ký tự"),
    "confirm_pair" to Words("確認配對", "Confirm", "Xác nhận"),
    "cancel" to Words("取消", "Cancel", "Hủy"),
    "disconnect" to Words("中斷連線", "Disconnect", "Ngắt kết nối"),
    "power" to Words("電源", "Power", "Nguồn"),
    "direction" to Words("方向控制", "Navigation", "Điều hướng"),
    "dpad_mode" to Words("按鍵模式", "D-pad", "Phím điều hướng"),
    "touchpad_mode" to Words("觸控板模式", "Touchpad", "Bàn di chuột"),
    "hold_repeat" to Words("長按連續操作", "Press and hold to repeat", "Nhấn giữ để lặp lại"),
    "direction_disabled" to Words("方向控制（連線完成後啟用）", "Navigation (available when connected)", "Điều hướng (khả dụng khi đã kết nối)"),
    "back" to Words("返回", "Back", "Quay lại"),
    "home" to Words("首頁", "Home", "Trang chủ"),
    "menu" to Words("選單", "Menu", "Menu"),
    "settings" to Words("系統設定", "Settings", "Cài đặt"),
    "input_source" to Words("電視訊號源", "TV input", "Nguồn vào TV"),
    "channel" to Words("頻道控制", "Channel", "Kênh"),
    "channel_down" to Words("頻道 −", "Channel −", "Kênh −"),
    "channel_up" to Words("頻道 +", "Channel +", "Kênh +"),
    "volume_media" to Words("音量與媒體", "Volume & media", "Âm lượng & media"),
    "volume_info" to Words("電視音量", "TV volume", "Âm lượng TV"),
    "muted" to Words("已靜音", "Muted", "Đã tắt tiếng"),
    "tv_power_on" to Words("電視已開啟", "TV is on", "TV đang bật"),
    "tv_power_off" to Words("電視待機／關閉", "TV is off / standby", "TV đang tắt / chờ"),
    "volume_down" to Words("音量 −", "Volume −", "Âm lượng −"),
    "mute" to Words("靜音", "Mute", "Tắt tiếng"),
    "volume_up" to Words("音量 +", "Volume +", "Âm lượng +"),
    "previous" to Words("上一段", "Previous", "Trước"),
    "play_pause" to Words("播放 / 暫停", "Play / Pause", "Phát / Tạm dừng"),
    "next" to Words("下一段", "Next", "Tiếp"),
    "hdmi_shortcuts" to Words("HDMI 快捷鍵", "HDMI shortcuts", "Phím tắt HDMI"),
    "hdmi_note" to Words("HDMI 快捷鍵需電視韌體支援；部分機型可能只支援開啟訊號源選單。", "HDMI shortcuts depend on TV firmware; some models only open the input menu.", "Phím tắt HDMI tùy thuộc firmware TV; một số mẫu chỉ mở menu nguồn vào."),
    "voice_input" to Words("語音控制", "Voice control", "Điều khiển bằng giọng nói"),
    "voice_connecting" to Words("正在建立電視語音連線…", "Starting TV voice session…", "Đang khởi tạo phiên giọng nói TV…"),
    "voice_stop" to Words("停止語音", "Stop voice", "Dừng giọng nói"),
    "voice_listening" to Words("正在聆聽，說完請按停止", "Listening. Tap stop when finished.", "Đang nghe. Nhấn dừng khi hoàn tất."),
    "voice_done" to Words("語音已送出", "Voice sent", "Đã gửi giọng nói"),
    "voice_failed" to Words("語音控制失敗，請確認電視支援語音功能", "Voice control failed. Check TV voice support.", "Điều khiển giọng nói thất bại. Kiểm tra hỗ trợ từ TV."),
    "mic_permission" to Words("需要允許麥克風權限才能使用語音控制", "Microphone permission is required for voice control", "Cần quyền micro để điều khiển bằng giọng nói"),
    "voice_not_supported" to Words("這台電視未提供 Remote v2 語音功能", "This TV does not support Remote v2 voice", "TV này không hỗ trợ giọng nói Remote v2"),
    "text_input" to Words("手機文字輸入", "Phone keyboard", "Nhập văn bản bằng điện thoại"),
    "keyboard_open" to Words("開啟手機鍵盤", "Open phone keyboard", "Mở bàn phím điện thoại"),
    "text_input_hint" to Words("先在電視上開啟搜尋或文字輸入欄，再於此輸入文字。", "Focus a search or text field on the TV first, then type here.", "Trước tiên hãy mở ô tìm kiếm hoặc nhập văn bản trên TV, sau đó nhập tại đây."),
    "text_placeholder" to Words("輸入要傳送到電視的文字", "Enter text to send to TV", "Nhập văn bản để gửi đến TV"),
    "send_text" to Words("傳送文字", "Send text", "Gửi văn bản"),
    "text_waiting" to Words("等待電視文字欄位就緒…", "Waiting for a TV text field…", "Đang chờ trường nhập văn bản trên TV…"),
    "text_sent" to Words("已傳送", "Sent", "Đã gửi"),
    "text_failed" to Words("尚未取得電視輸入欄位資訊。請先在電視上選取文字欄位。", "TV input field is not ready. Focus a text field on the TV first.", "Trường nhập TV chưa sẵn sàng. Hãy chọn trường văn bản trên TV trước."),
    "touchpad" to Words("觸控滑鼠", "Touchpad", "Bàn di chuột"),
    "touch_help" to Words("滑動控制焦點，點一下確認，長按可長按選取。", "Swipe to move focus, tap to select, and long-press to hold.", "Vuốt để di chuyển tiêu điểm, chạm để chọn, nhấn giữ để giữ."),
    "touch_surface" to Words("觸 控 板", "TOUCHPAD", "BÀN DI CHUỘT"),
    "swipe_directions" to Words("上下左右滑動", "Swipe up, down, left or right", "Vuốt lên, xuống, trái hoặc phải"),
    "touch_repeat_hint" to Words("滑動距離越長，焦點移動越多格", "Longer swipes move through more items", "Vuốt càng xa, tiêu điểm di chuyển càng nhiều bước"),
    "tap_confirm" to Words("單擊：確認　雙擊：連續確認", "Tap: select   Double tap: select twice", "Chạm: chọn   Chạm hai lần: chọn hai lần"),
    "right_back" to Words("右鍵／返回", "Right button / Back", "Nút phải / Quay lại"),
    "left_select" to Words("左鍵／確認", "Left button / Select", "Nút trái / Chọn"),
    "sensitivity" to Words("靈敏度", "Sensitivity", "Độ nhạy"),
    "touch_note" to Words("注意：Android TV Remote v2 提供方向鍵事件，不提供通用滑鼠游標注入；本頁以觸控手勢轉換成方向鍵、確認與長按操作。", "Note: Android TV Remote v2 sends navigation key events, not generic mouse cursor movement. Touch gestures here are mapped to navigation, select and long-press actions.", "Lưu ý: Android TV Remote v2 hỗ trợ phím điều hướng, không hỗ trợ di chuyển con trỏ chuột chung. Cử chỉ chạm được chuyển thành điều hướng, chọn và nhấn giữ."),
    "language" to Words("語言", "Language", "Ngôn ngữ"),
    "ip_example" to Words("例如 192.168.1.100", "e.g. 192.168.1.100", "ví dụ 192.168.1.100")
)

@Composable
fun t(key: String): String {
    val language = LocalAppLanguage.current
    val entry = words[key] ?: return key
    return when (language) {
        "en" -> entry.en
        "vi" -> entry.vi
        else -> entry.zh
    }
}
