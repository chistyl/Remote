# TV Remote v1.1.12（Remote ATV 5.0.5 操作對齊版）

> 本專案為 Kotlin + Jetpack Compose 重建版本，並非原 Flutter APK 的原始碼。

本專案以使用者提供的 `Remote_ATV_5.0.5_繁體中文修正版2.apk` 作為功能與操作參考。原 APK 為 Flutter AOT，未取得其完整原始碼；本專案以 Kotlin + Jetpack Compose 重建，不宣稱是原版原始碼。

## v1.1.5 本輪修正
- 修正切換不同已儲存電視時，舊連線工作仍 active 導致新連線被忽略的問題。
- 加入連線世代識別，避免舊連線結束時清除新連線狀態。
- 電視 mDNS 服務消失時，同步移除尚未解析的待處理服務。

## v1.1.4 本輪修正
- 修正 Android 9 NSD 服務探索：改為序列化解析佇列，避免同時發現多台電視時，因 Android 一次只允許一筆 resolve 而漏掉其他裝置。
- 修正遙控頁離開時語音錄音工作可能因 DisposableEffect 捕獲舊 Job 而未取消的問題；改為取消最新錄音工作並設定停止旗標。
- 版本代碼更新為 16。

## v1.1.3 本輪更新
- 依原版 APK 可辨識的 `Remote layout`、`Tap based control`、`Swipe based touchpad` 操作字串，加入「按鍵模式／觸控板模式」切換。
- 預設使用圓形方向鍵，中央 OK；可切換至滑動觸控板。
- 首頁及中央 OK 支援長按事件，對齊原版 5.0.4 起的長按確認／首頁操作。
- 加入頂部五個主要快捷鍵：返回、首頁、選單、訊號源、電源。
- 方向鍵、音量及頻道按鍵支援按住後連續送出；初始延遲 420 ms，後續間隔 115 ms。
- 加入「長按連續操作」開關，並保存使用者選擇。
- 保存按鍵／觸控板模式，重新開啟 APP 後維持上次設定。
- 移除重複出現的首頁、選單、電源、系統設定及訊號源按鍵列，避免同一功能重複佔位。

## 既有功能
- Android TV Remote v2 配對（TLS 6467）及控制（TLS 6466）
- 同 Wi-Fi mDNS 搜尋與手動輸入 IP
- 配對憑證保存及電視憑證指紋驗證
- 斷線自動重連、Ping 回應、TCP 分段封包解析
- 返回、首頁、選單、電源、音量、靜音、播放／暫停、上一段／下一段、頻道及訊號源按鍵
- 電視回報音量時顯示音量百分比及靜音狀態；不虛構電視未回報的數值
- 電視回報電源狀態時顯示狀態
- 手機鍵盤文字輸入及 Remote v2 語音控制
- 已配對電視記憶與快速重新連線
- 繁體中文、英文、越南文

## 明確不包含
- 電視 APP 自訂快捷清單（依使用者要求移除）
- ADB 診斷頁及 ADB 相依套件
- 假稱可透過 Remote v2 注入真正滑鼠座標；觸控板手勢目前轉換成方向鍵／確認事件

## 建置需求
- Android Studio、JDK 17、Android SDK Platform 34、Gradle 8.7 或相容版本
- `minSdk 21`、`targetSdk 34`
- 執行 `:app:testDebugUnitTest` 及 `:app:assembleDebug`
- 專案含 GitHub Actions 工作流程 `.github/workflows/android-build.yml`

APK 預期輸出：`app/build/outputs/apk/debug/app-debug.apk`

## 驗證狀態
- 已執行觸控手勢及長按時間設定的獨立 Kotlin 編譯／邏輯檢查。
- 已檢查語言鍵值引用、Android XML 格式、重複控制列清除及 ZIP 完整性。
- 目前執行環境沒有 Android SDK／Gradle，無法在此完成 Android 全專案編譯、APK 產生、安裝或真實電視配對測試；不可將獨立 Kotlin 測試視為完整 APK 建置成功。

### v1.1.6
- 修正取消配對後舊工作覆寫新配對狀態，以及舊 socket 清理競態。

### v1.1.7 本輪修正
- 修正配對取消與重新開始同時發生時，socket 可能在取消後才被舊工作掛回共享欄位的競態。
- 配對世代、socket 指派、狀態更新、取消與舊工作清理改由同一把鎖保護。
- 舊世代無法更新新配對狀態，也無法關閉新一輪配對的 socket。
- versionCode 更新為 19，versionName 更新為 1.1.7。

### v1.1.8 本輪修正
- 修正配對碼使用全域 rendezvous Channel 的跨輪次問題：取消配對時，舊的送碼 coroutine 可能持續等待，或延遲送出的舊配對碼可能被下一輪配對接收。
- 改為每次配對各自建立 CompletableDeferred，配對碼只交給目前等待中的那一輪。
- 取消時同步取消並清除當輪配對碼等待器；送碼改為非阻塞完成，不再因等待接收而掛住 UI coroutine。
- versionCode 更新為 20，versionName 更新為 1.1.8。

### v1.1.9 本輪修正
- 修正快速切換電視／取消連線時，舊 TLS 連線可能在握手完成後才寫入共用 socket，覆蓋新連線的競態。
- 連線世代、socket 發布、斷線清理及狀態重設統一由 connectionLock 保護。
- 舊世代建立的 socket 若在取消或切換後才完成握手，會立即關閉，不會被設為目前連線。
- 舊 read loop 的回覆固定寫回來源 socket，不會誤送到新電視。
- versionCode 更新為 21，versionName 更新為 1.1.9。

### v1.1.10 本輪修正
- 重置每次連線取得的電視功能旗標，避免切換不同電視時沿用上一台電視的能力狀態。
- versionCode 更新為 22，versionName 更新為 1.1.10。

### v1.1.11 本輪修正
- 修正 Android 9 NSD 搜尋中的過期解析回呼，避免已離線的電視因舊 resolve callback 再次出現在裝置清單。
- 搜尋開始時清除舊離線標記；重新發現服務時解除對應標記。
- 停止搜尋時清空離線標記與待解析佇列，並以 discovery generation 阻擋舊搜尋回呼回寫。
- versionCode 更新為 23，versionName 更新為 1.1.11。

## v1.1.11 驗證狀態
- RemoteClient 與 PairingClient 協定／配對模組獨立 Kotlin 編譯通過。
- 20 項純 Kotlin 測試通過。
- Android XML、版本號、NSD 過期回呼防護與 ZIP 完整性檢查通過。
- **尚未完成** Android Gradle 全專案建置、APK 安裝及 Android 9 電視實機測試；不可將上述獨立模組測試視為 APK 完整驗收。

### v1.1.11 追加除錯修正（同名服務快速離線／重新出現）
- 發現原本僅使用 `lostServiceNames` 的保護仍有競態：同名 mDNS 服務若在舊 resolve callback 返回前重新出現，`onServiceFound` 會先清除離線標記，舊 callback 可能被誤認為有效。
- 新增 `ServiceResolveEpoch`，每次 `onServiceLost` 都增加該服務的世代；resolve 開始時記錄世代，回呼只有在世代仍一致時才能加入裝置清單。重新發現服務不會讓舊回呼復活。
- 新增 3 項回歸測試，涵蓋同名服務快速重新出現、不同服務互不影響，以及新一輪搜尋重置世代。
- 追加測試後共 23 項純 Kotlin 測試通過。完整 Android Gradle 建置及 Android 9 實機測試仍待 GitHub CI／實機驗證。

### CI 啟動修正
- 將 v1.1.11 專用 GitHub Actions 工作流程同步放到預設分支，確保 GitHub 能辨識並允許此工作流程執行。
- 本次文件提交用來重新觸發原生 Android 測試與 APK 建置；不變更 APP 功能。


## v1.1.12 工作分支更新
- 本版本以 v1.1.11 原生重建版本為基礎，保留既有配對、連線、方向鍵、音量、文字輸入及觸控板操作。
- 版本號更新為 versionCode 24、versionName 1.1.12。
- 觸控板手勢維持以 DPAD 方向／確認事件操作，不宣稱支援真正滑鼠游標座標注入。
- 建置工作流程支援本工作分支，並分別上傳 Debug APK 與完整原始碼 ZIP。
- Android 9 電視實機配對與長時間連續操作仍需實機驗收。
