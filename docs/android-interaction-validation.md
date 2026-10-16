# Android UI／互動與持久化修改紀錄

修改日期：2026-10-15。整合日期：2026-10-16。
實作分支：`codex/android-interaction-persistence`；主分支：`master`。

## 修改範圍

| 計畫章節 | 實作內容 | 主要位置 |
| --- | --- | --- |
| 1、52、57、59 | 圖片 Pager、真正 transform 縮放、點擊位置雙擊、邊界阻力與回彈、淡入控制列、頁碼保存 | MediaViewerDialog、DetailMediaPager |
| 2、58 | 真正 Activity 轉向、影片尺寸判斷、播放位置／靜音／倍速保存、控制列與安全 Insets | InlineVideoPlayer、SafeBarModifiers |
| 3、4 | 主文／母文／留言媒體 Pager、分類可點擊套用篩選、作者與儲存時間分行 | DetailDialog |
| 5 | 可編輯人工筆記；每次編輯、失焦、關閉與背景保存；AI 輸出獨立欄位，摘要輸入可含筆記 | DetailDialog、MainViewModel、ThreadPostRepository |
| 6、52 | 邊界拖曳跟隨、阻力、距離與速度門檻、spring、背景透明度；Pager 拖曳時阻擋關閉 | DetailDragState |
| 7–12 | 全部／未讀／收藏分類篩選、搜尋與動畫；PRO 頁只做管理、排序與規則編輯／啟停 | MainActivity、CategoryFilterBar、SmartCollectionEngine |
| 13–15 | 設定文案、儲存設定按鈕、點空白清焦點、鍵盤優先返回 | SettingsDialog |
| 16–24 | 圖示／自訂頭像雙頁籤、篩選與分類區塊、PRO 介紹、持久原圖、非破壞編輯與歷史 | IconGalleryDialog、AvatarStorage、AvatarCropDialog |
| 21、53 | 頭像原圖／成品／metadata／歷史／目前參照隨備份保存；還原重新配置檔名 | BackupManager |
| 25–47、55 | 穩定分類輸入框、即時搜尋／直接建立／選取、SavedStateHandle 草稿、IME 避讓、BringIntoView、可捲動面板 | ShareSaveActivity、ShareSaveViewModel、CategoryDropdown |
| 48–62 | 安全 Insets、無障礙描述、GIF decoder、媒體載入與錯誤重試、統一動畫 | 共用 UI 元件 |

## 必要的一併調整

- 資料庫升至第 9 版。保留歷史第 8 版 schema 的智慧分類 `isEnabled`，補 `7 → 8 → 9` 遷移；舊摘要複製至 `aiSummary`，人工 `manualNote` 保留。
- 保留 `manualSummary` 歷史欄位與備份相容；新 AI 寫入只更新 `aiSummary`。
- 分享 Activity 使用非浮動透明視窗，讓 `adjustResize` 與 IME Insets 有穩定的可用範圍；保留旋轉時的 Activity 狀態。
- 新增 `coil-gif` 解碼 GIF，以及 JUnit／JVM JSON 測試依賴。
- 兩個原有 Threads 測試的診斷輸出引用已不存在的欄位，改為輸出留言數，讓原有測試可編譯；未變更解析器。
- 明確選取「未分類」及主動清空備註時，儲存層尊重本次編輯，避免被自動分類或既有內容覆蓋。

## 工作開始前的變更

開始時已有 `app/build.gradle.kts`、Manifest、BackupManager、SettingsRepository、MediaViewerDialog、PasscodeUnlockDialog、InlineVideoPlayer、SafeBarModifiers、SettingsDialog 的未提交修改，以及建置腳本刪除／新增與 `ic_mode_switch.xml`。這些內容均保留；不能將完整 Git diff 視為本次新增內容。

工作前的 staged／unstaged patch 與 status 保存在系統暫存目錄 `reater-before-20261015`。`Questions/` 是使用者提供的問題圖片。建置產生的 `.kotlin/` 不屬於產品程式碼修改。

## 自動驗證

- `:app:assembleDebug` 通過，產物：`app/build/outputs/apk/debug/app-debug.apk`。
- `apksigner verify` 通過。
- `:app:testDebugUnitTest`：14 個測試全部通過，包含頭像 6 個、分類正規化 2 個、智慧規則 3 個，以及原有 Threads 測試 3 個。
- `tools/verify_note_migration.py` 通過：執行實際 Kotlin 遷移中的 SQL，驗證第 7／8 版升至第 9 版後人工筆記、舊摘要及舊版停用規則保留，且 user_edits 與新建 schema 相符。
- `git diff --check` 通過。
- `:app:lintDebug` 通過。經使用者授權修正先前的 3 個既有 error：
  - 兩處通知呼叫保留原權限檢查，另捕捉權限在檢查後被撤回時的 `SecurityException`，避免背景工作因此中斷。
  - 本機 `local.properties` 的 SDK 路徑改用跳脫的磁碟機分隔符號；此檔維持不納入 Git。
- 最終 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=2` 整批執行成功；APK 簽章再次驗證通過。

## 提交與整合範圍

既有 10 個 App 檔案先獨立記錄為保留快照，再提交本次修改，讓兩批變更可分別檢視。`master` 採 fast-forward 納入這兩個 commit；本次只整合本機分支。

原本 staged 的 `build-release.ps1.example` 刪除、未追蹤的 `build-release.bat` 與 `Questions/` 維持原狀，不納入本次提交。

Lint 報告：`app/build/reports/lint-results-debug.html`。
測試報告：`app/build/reports/tests/testDebugUnitTest/index.html`。

## 尚待實機驗收

目前 `adb devices` 無連接裝置，SDK 也未提供可執行的模擬器；下列項目僅完成實作與程式碼交叉檢查，不能標示實機通過：

- Samsung／Pixel，小螢幕、大字體、手勢導覽與三鍵導覽的實際排版。
- Pinch／Pan／Pager 邊界交接、雙擊、詳細頁雙向拖曳與彈回手感。
- 直／橫影片旋轉後的位置、播放、音量與倍速，以及退出後還原方向。
- Gboard、Samsung Keyboard、注音組字、清空分類、無結果新增、備註輸入、兩次返回。
- Android 強制終止／程序重建、刪除相簿原圖後重開，以及完整 `.reater` 備份匯出／還原的裝置流程。

測試用 APK 使用 debug 簽章；本次未覆寫既有 release APK。
