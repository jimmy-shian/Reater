# Reater — Threads 稍後閱讀 App

> 把 Threads 好文一鍵存下來，離線也能慢慢讀、分類、搜尋、回顧。
> Android 原生 App，Jetpack Compose + Room + Hilt 打造，支援分享收藏、自動抓文、AI 分類摘要、統計分析、桌面小工具、備份還原與 Pro 解鎖。

---

## 目錄

- [1. 專案簡介](#1-專案簡介)
- [2. 核心功能一覽](#2-核心功能一覽)
- [3. 技術架構](#3-技術架構)
- [4. 專案結構](#4-專案結構)
- [5. 環境需求](#5-環境需求)
- [6. 快速開始](#6-快速開始)
- [7. 日常使用操作說明](#7-日常使用操作說明)
- [8. 設定頁完整說明](#8-設定頁完整說明)
- [9. Pro 解鎖三種方式](#9-pro-解鎖三種方式)
- [10. 備份與還原](#10-備份與還原)
- [11. 桌面小工具 Widget](#11-桌面小工具-widget)
- [12. Release 打包與簽名](#12-release-打包與簽名)
- [13. Pro 啟用碼管理工具](#13-pro-啟用碼管理工具)
- [14. 權限說明](#14-權限說明)
- [15. 常見問題 FAQ](#15-常見問題-faq)
- [16. 安全與隱私說明](#16-安全與隱私說明)

---

## 1. 專案簡介

| 項目 | 說明 |
| --- | --- |
| App 名稱 | Reater |
| Package | `com.reater.app` |
| 版本 | `1.0.0` (versionCode `1`) |
| 語系 | 繁中 / 簡中 / 英文 (`values`, `values-zh-rTW`, `values-zh-rCN`) |
| 入口 | `ui.MainActivity` (singleTask, 豎屏) |
| 分享入口 | `ui.share.ShareSaveActivity` (接收 `ACTION_SEND` / `text/plain`) |
| 資料庫 | Room 3.x, schema 輸出至 `app/schemas/` (目前 v3–v6) |

**一句話定位：** 類似 Pocket / Readwise 的 Threads 專用版本，強調本機優先、離線可讀、可匯出、不綁雲端。

---

## 2. 核心功能一覽

### 收藏與抓取

- **系統分享存文：** 在 Threads / 瀏覽器按「分享 → Reater」，`ShareSaveActivity` 彈窗即存，無需複製貼上。
- **手動貼連結存文：** 主頁頂部輸入框貼上 Threads 貼文網址或 shortcode 亦可儲存。
- **多路解析器：** `threads/ThreadsWebResolver` 自動依序嘗試 GraphQL → oEmbed → HTML 解析 → SJS 解析，提高抓取成功率。
- **內文 + 留言 + 媒體一次存：** 作者、內文、讚數、留言串、圖片 / 影片網址全部落地 Room，並下載媒體到本機快取 (`MediaDownloader`)，離線可看。
- **媒體去重：** `MediaDedup` 以正規化 URL 避免重複下載同一圖片 / 影片。

### 閱讀與整理

- **Feed 卡片 (`PostCard`)：** 顯示頭像、作者、時間、內文預覽、統計列、分類徽章、媒體縮圖，支援行內影片播放 (`InlineVideoPlayer`) 與全螢幕檢視 (`MediaViewerDialog`)。
- **詳情對話框 (`DetailDialog`)：** 全文、完整留言樹、外部連結點擊 (`LinkifiedText`)、手動筆記 / 摘要編輯、已讀 / 收藏切換、分享 / 刪除。
- **分類 + 標籤 + 關鍵字：** 自建分類 (`CategoryEntity`)、多標籤 (`TagEntity`)、FTS 全文搜尋 (`ItemFtsEntity`)。
- **智慧收藏 (`SmartCollectionEngine`)：** 未讀稍後讀、我的最愛、近期新增等虛擬集合自動聚合。
- **本機分類器 (`OnDeviceClassifier`)：** 無網路時用規則先給分類，AI 回來後再覆寫 (`persistAiAnalysis`)。
- **搜尋 / 篩選 / 排序：** 關鍵字、分類下拉 (`CategoryDropdown`)、Pro 分類列 (`ProCategoryFilterRow`)、已讀 / 未讀 / 收藏 FilterChip。

### AI 加值 (需自備 Key, 預設關閉)

- `data/remote/OpenAiClient` 呼叫 OpenAI-compatible `/chat/completions`。
- 可自訂 `baseUrl` + `model` (預設 `https://api.openai.com/v1` + `gpt-5-nano`)。
- 回傳 `AiAnalysisResult(category, tags, summary)` 自動寫回貼文。
- **設定頁需先勾選「允許傳送內容到 AI」才會送出**，否則只跑本機分類。

### 統計與提醒

- **Analytics (`AnalyticsScreen`)：** 閱讀量、收藏趨勢、分類分佈、互動圖表 (`InteractiveAnalyticsChart`)。
- **未讀提醒 (`UnreadNudgeWorker`)：** 久未讀自動推播。
- **複習摘要 (`ReviewDigestWorker`)：** 每日 21:00 (可調) 推播回顧。
- 推播通道統一由 `notify/NotifyCenter` 建立。

### Pro / 變現

- **Google Play 一次性購買** (`PlayBillingManager`, 商品 ID `pro_unlock`，定義在 `app/build.gradle.kts` 的 `PRO_PRODUCT_ID`)。
- **離線啟用碼** (`LicenseVerifier` + `tools/generate_license.py`)，支援 Email 綁定 + Deep Link 一鍵開通。
- **存取碼鎖** (`PasscodeUnlockDialog`)：可設數字鎖，防窺探。

---

## 3. 技術架構

### 語言與 SDK

| 層 | 選型 |
| --- | --- |
| 語言 | Kotlin 2.3.21, JVM 21, Java 21 |
| UI | Jetpack Compose (BOM 2024.11.00) + Material3 1.3.1 + Navigation Compose 2.8.4 |
| DI | Hilt 2.58 + hilt-navigation-compose |
| DB | Room 3.0.3 + sqlite-bundled 2.7.1 + KSP 2.3.9, FTS 搜尋 |
| 網路 | OkHttp 4.12.0 + kotlinx-serialization-json 1.7.3 + coroutines 1.9.0 |
| 圖片 | Coil 2.7.0 |
| Widget | Glance 1.1.1 |
| 背景任務 | WorkManager 2.10.0 |
| 偏好設定 | DataStore Preferences 1.1.1 |
| 加密 | Tink Android 1.15.0 |
| 金流 | Billing KTX 9.1.0 |
| Gradle | AGP 8.10.1, parallel + build-cache + configuration-cache 全開 |

完整版本鎖定請看 `gradle/libs.versions.toml`。

### 分層簡圖

```
ui/ (MainActivity, feed/, detail/, settings/, share/, analytics/, player/, components/, theme/)
 ├─ domain/ (UrlParser, OnDeviceClassifier, SmartCollectionEngine)
 ├─ data/repository/ (ThreadPostRepository, SettingsRepository, LicenseVerifier, PlayBillingManager)
 ├─ data/local/ (AppDatabase, dao/, entity/)
 ├─ data/remote/ (ThreadsGraphQLClient, threads/*Parser, MediaDownloader, MediaDedup, OpenAiClient)
 ├─ data/backup/ (BackupManager: JSON/CSV 匯出入)
 ├─ notify/ (NotifyCenter, UnreadNudgeWorker, ReviewDigestWorker)
 └─ widget/ (ReaterGlanceWidget, WidgetUpdateWorker)
di/DatabaseModule.kt + ReaterApplication.kt (Hilt 入口)
```

---

## 4. 專案結構

```
Reater/
├─ README.md                      ← 本文件
├─ settings.gradle.kts            ← rootProject.name = "Reater", include(":app")
├─ build.gradle.kts               ← 全域 plugin 宣告
├─ gradle.properties              ← 2GB heap, daemon, parallel, caching, config-cache
├─ gradle/libs.versions.toml      ← 所有依賴版本單一來源
├─ gradlew / gradlew.bat
├─ local.properties               ← sdk.dir (本機專用,不進 git)
├─ app/
│  ├─ build.gradle.kts            ← applicationId, min/target SDK, PRO_PRODUCT_ID, Room schemaDir
│  ├─ proguard-rules.pro
│  ├─ schemas/com.reater.app.data.local.AppDatabase/{3,4,5,6}.json
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/reater/app/      ← 全部 Kotlin 原始碼 (約 65 檔)
│     └─ res/ (values, values-zh-rTW, values-zh-rCN, xml/, mipmap/, drawable/, theme/)
├─ tools/
│  ├─ README.md                   ← 發碼工具白話說明
│  ├─ generate_license.py         ← Pro 啟用碼產生器
│  └─ rotate_secret.py            ← 一鍵輪換 secret
├─ build-release.ps1              ← 一鍵 release: 建置→zipalign→簽名→驗證→拷貝 (本機私鑰,不進 git)
└─ Reater-release-signed.apk      ← 上次簽名產物 (gitignore, 僅本機留存)
```

關鍵類對照：

| 想改什麼 | 去哪裡 |
| --- | --- |
| 主畫面列表 / 搜尋 / 篩選邏輯 | `ui/MainActivity.kt` (`MainViewModel`, `MainScreen`) |
| 分享存文彈窗 | `ui/share/ShareSaveActivity.kt`, `ShareSaveViewModel.kt` |
| 貼文抓取流程 | `data/repository/ThreadPostRepository.kt` |
| Threads 解析 | `data/remote/threads/ThreadsWebResolver.kt` + `Threads*Parser.kt` |
| AI 分類摘要 | `data/remote/OpenAiClient.kt`, `domain/OnDeviceClassifier.kt` |
| 設定頁 | `ui/settings/SettingsDialog.kt`, `data/repository/SettingsRepository.kt` |
| 備份還原 | `data/backup/BackupManager.kt` |
| 推播 | `notify/NotifyCenter.kt` |
| Widget | `widget/ReaterGlanceWidget.kt` |
| Pro 驗證 | `data/repository/LicenseVerifier.kt`, `data/billing/PlayBillingManager.kt` |

---

## 5. 環境需求

| 項目 | 需求 |
| --- | --- |
| OS | Windows / macOS / Linux 皆可 (本 repo 附 `build-release.ps1` 以 Windows 為主) |
| JDK | 21 (Gradle toolchain 會抓, `compileOptions` 已鎖 `VERSION_21`) |
| Android SDK | `compileSdk 36`, `targetSdk 36`, `minSdk 26` (Android 8.0+) |
| Build-Tools | 任一新版即可, 腳本會自動取最新版 |
| Python | 3.10+ (僅 `tools/*.py` 發碼用, App 本體不需要) |
| 網路 | 首次 Gradle sync 需連 `google()` + `mavenCentral()` |

`local.properties` 範例 (Windows)：

```properties
sdk.dir=C\:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk
```

---

## 6. 快速開始

### 6.1 Debug 直接跑

```powershell
# 1. 複製專案後,確認 local.properties 的 sdk.dir 指向本機 SDK
# 2. Android Studio 開啟此資料夾,等待 Gradle sync 完成
# 3. 或指令列:
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:assembleDebug
```

產物：`app\build\outputs\apk\debug\app-debug.apk`

### 6.2 Release 簽名包 (一鍵)

```powershell
powershell -ExecutionPolicy Bypass -File .\build-release.ps1
```

腳本依序做 6 步：

1. `:app:assembleRelease --parallel --build-cache --offline`
2. `zipalign -p -f 4` 對齊
3. `apksigner sign` 簽名 (預設 keystore 路徑 / alias / 密碼見腳本 `param` 區，請改成自己的)
4. `apksigner verify --verbose` 驗證
5. 拷貝為根目錄 `Reater-release-signed.apk`
6. `gradlew --stop` 釋放 Daemon (省 RAM)

常用變體：

```powershell
# 只重簽名,不重編 (程式沒改時最快)
powershell -ExecutionPolicy Bypass -File .\build-release.ps1 -SkipBuild

# 保留 Daemon (連續多次打包更快)
powershell -ExecutionPolicy Bypass -File .\build-release.ps1 -NoStopDaemon
```

> 注意：`build-release.ps1` 內含私鑰路徑與密碼，已 `.gitignore`，請勿上傳。正式發版前務必換成自己的 keystore。

---

## 7. 日常使用操作說明

### 7.1 第一次開啟

1. 允許通知權限 (Android 13+ 會跳系統詢問，用於未讀提醒 / 複習摘要)。
2. 進入主畫面，點右上角 **⚙ 設定**，依第 8 節調整主題、字體、AI、提醒。
3. 建議先做一次 **匯出備份** 熟悉流程 (設定 → 匯出 JSON)，確認檔案能正常產生。

### 7.2 存一篇 Threads (三種方式)

**A. 分享存 (最常用)：**

1. 在 Threads App 開啟任一貼文 → 點「分享」。
2. 分享清單選 **Reater**。
3. 彈出 `ShareSave` 視窗顯示解析中 → 成功後 Toast 提示，可選分類 / 加筆記後按儲存。
4. 回到 Reater 主頁即看到新卡片 (未讀狀態)。

**B. 貼連結存：**

1. 複製 Threads 連結 (如 `https://www.threads.com/@user/post/xxxx`)。
2. 回到 Reater 主頁頂部輸入框貼上 → 按儲存 / Enter。
3. App 自動呼叫 `ThreadPostRepository.savePost` → 解析 → 落庫 → 卡片出現。

**C. 純文字 / 筆記存：**

1. 在輸入框直接貼一段文字 (非網址)，App 會走 `processIncomingText` 建一筆手動筆記。
2. 可後續在詳情頁補連結或分類。

失敗排查：連結需為公開貼文；私人 / 需登入貼文抓不到內文時會保留連結與標題，請手動補筆記。

### 7.3 主頁閱讀流

| 元件 | 操作 |
| --- | --- |
| 頂部搜尋框 | 輸入關鍵字即時 FTS 搜尋內文 / 作者 / 筆記 |
| 分類下拉 | `CategoryDropdown` 選特定分類, 空白 = 全部 |
| Pro 分類列 | 橫向快速切換 (Pro 解鎖後可用更多智慧分類) |
| FilterChip | 已讀 / 未讀 / 收藏三態篩選 |
| 卡片點擊 | 開 `DetailDialog` 看全文 + 留言串 |
| 卡片書籤鈕 | 切換收藏 (`Bookmark` / `BookmarkBorder`) |
| 卡片圓點 | 切換已讀 / 未讀 |
| 長按 / `MoreVert` | 分享 (`formatPostShareText`)、複製連結、刪除、匯出單篇 |
| 底部統計列 | 該篇讚數 / 留言數 / 媒體數 (`ThreadsStatsRow`) |
| 圖片 / 影片 | 點縮圖開 `MediaViewerDialog`，影片行內播 (`InlineVideoPlayer`)，本機影片可調外部播放器 (FileProvider) |

### 7.4 詳情頁操作

1. 點卡片進入 `DetailDialog`。
2. 頂部作者列 + 時間 (`TimeFormat` 相對時間)。
3. 中段全文 (`LinkifiedText` 點連結可跳瀏覽器)。
4. 留言區依 `sortKey` / `depth` 縮排顯示。
5. 底部可編輯 **手動筆記 / 手動摘要** (`UserEditEntity`)，存檔即寫回 DB。
6. 右上動作列：標已讀、收藏、分享、刪除 (`DeleteForever` 二次確認)。

### 7.5 分析頁

主頁切到 **Analytics** Tab (`AnalyticsScreen`)：

- 總收藏數、已讀率、近 7/30 天趨勢 (`InteractiveAnalyticsChart`)。
- 分類佔比長條圖，點長條可跳回該分類列表。
- `loadAnalytics` / `getAnalytics` 皆走本機聚合，不上傳。

---

## 8. 設定頁完整說明

入口：主頁右上 ⚙ → `SettingsDialog`。所有設定即時寫入 DataStore + 匯出時一併備份 (`ExportedSettings`)。

| 分區 | 欄位 | 說明 |
| --- | --- | --- |
| 外觀 | 主題模式 | 跟隨系統 / 淺色 / 深色 (`themeMode`) |
| 外觀 | 字體縮放 | Slider `fontScale`，全 App 生效 |
| 外觀 | 頭像 | 內建頭像 (`AvatarIcons`) / 圖庫自選 (`IconGalleryDialog`, `customAvatarUri`) |
| AI | 允許傳送內容 | 總開關 (`aiTransmissionConsent`)，關閉則完全不上傳 |
| AI | Base URL | 相容 OpenAI 格式，預設 `https://api.openai.com/v1`，可填自架中轉 |
| AI | Model | 預設 `gpt-5-nano`，下拉或手打 |
| AI | API Key | 存 Tink 加密區，不進備份明文 |
| 提醒 | 未讀提醒 | 開關 + 延遲分鐘 (`unreadNudgeDelayMin` 預設 10) |
| 提醒 | 每日複習 | 開關 + 小時 (`reviewDigestHour` 預設 21) |
| 安全 | 存取碼鎖 | 設 4–6 位數字，下次開啟需解鎖 (`PasscodeUnlockDialog`) |
| Pro | 狀態 | 顯示 Pro / Free，附 `ProUnlockCard` 升級入口 |
| 資料 | 匯出 JSON | `BackupManager.exportToJson` 全庫匯出 (見第 10 節) |
| 資料 | 匯入 JSON | 選擇 `.json` 還原，會合併不覆蓋刪除 |
| 資料 | 匯出 CSV | 扁平表格，方便試算表分析 |
| 關於 | 版本 | `1.0.0` + schema 版本 + 商品 ID |

改 AI Key 後務必按「測試連線」，成功才會存檔。

---

## 9. Pro 解鎖三種方式

| 方式 | 入口 | 流程 |
| --- | --- | --- |
| Google Play 購買 | 設定 → `ProUnlockCard` → 立即升級 | 調 `PlayBillingManager` 結帳 `pro_unlock`，成功寫 `ProDao` |
| 啟用碼手輸 | 設定 → 用 Reater 開啟 / 輸入啟用碼 | 輸入 `PRO-XXXX-XXXX-XXXX` + Email，`LicenseVerifier` 本機驗 HMAC |
| Email 一鍵開通 | 點信中連結 | `reater://pro-unlock?email=..&code=..` 被 `MainActivity.parseProUnlock` 攔截自動解鎖；`https://reater.app/pro-unlock` 為備用 (部分信箱只認 https) |

驗證規則 (與 `tools/generate_license.py` 對應)：Email 先 `trim + 小寫 + ASCII 檢查`，啟用碼為 4 碼編號 (20-bit) + 8 碼驗證碼 (40-bit)，驗證碼 = `HMAC_SHA256(secret, "REATER-PROv1|email|serial")` 取前 40-bit 編碼。

---

## 10. 備份與還原

格式：`ReaterExportV1(version="v1", fileFormat="reater")`，內含 `settings` + `items[]` + `categories[]`，每篇含 `canonicalUrl, shortcode, bodyText, comments[], tags[], media[]`。

| 操作 | 路徑 | 說明 |
| --- | --- | --- |
| 匯出 JSON | 設定 → 匯出 JSON | 經 `BackupManager.exportToJson`，存到系統分享 / 檔案，檔名含日期 |
| 匯入 JSON | 設定 → 匯入 JSON | `BackupManager.import`，以 `canonicalUrl` 去重合併，媒體缺失會標記需重抓 |
| 匯出 CSV | 設定 → 匯出 CSV | `exportToCsv`，一列一篇，留言合併為純文字欄 |

建議節奏：重大整理前匯出一次；換機時先匯出 → 新機安裝 → 匯入 → 開啟自動抓漏 (`fetchPostByPostIdOrShortcode` 補媒體)。

---

## 11. 桌面小工具 Widget

- Provider：`widget/ReaterGlanceWidget.kt` + `ReaterGlanceWidgetReceiver`，資訊檔 `res/xml/reater_widget_info.xml`。
- 新增方式：桌面長按 → 小工具 → 找到 Reater → 拖到桌面。
- 內容：未讀數 + 最新 3 篇標題，點任一標題 Deep Link 回主 App 該篇。
- 更新：`WidgetUpdateWorker` 在存文 / 標已讀後觸發 `ensureChannel` 推播刷新。

---

## 12. Release 打包與簽名

前置：`local.properties` 有 `sdk.dir`，`app/build.gradle.kts` 的 `versionCode/versionName` 已遞增，`PRO_PRODUCT_ID` 與 Play Console 一致。

```powershell
# 標準發版
powershell -ExecutionPolicy Bypass -File .\build-release.ps1

# 確認產物
Get-Item .\Reater-release-signed.apk
```

`gradle.properties` 已開 `parallel / caching / configuration-cache / kotlin.incremental`，首編慢、次編快屬正常。

---

## 13. Pro 啟用碼管理工具

詳見 `tools/README.md`，此處為操作速查。

```bash
# 發一組碼
python tools/generate_license.py someone@example.com

# 一次發 5 組
python tools/generate_license.py someone@example.com --count 5

# 測試不寫帳本
python tools/generate_license.py someone@example.com --no-log

# 預覽輪換 (不寫檔)
python tools/rotate_secret.py --dry-run

# 正式輪換 secret (做 4 件事: 生新 secret→寫 .license_secret→同步 generate_license.py→同步 LicenseVerifier.kt→歸檔舊 csv)
python tools/rotate_secret.py
```

規則：Email 只收 ASCII；同 Email 撞編號自動重抽 (`licenses_issued.csv` 為帳本，已 gitignore)；輪換後舊碼全失效，需重編 APK + 重發碼。

---

## 14. 權限說明

| 權限 | 用途 |
| --- | --- |
| `INTERNET` | 抓 Threads 貼文 / AI 請求 / Play Billing 驗證 |
| `POST_NOTIFICATIONS` | 未讀提醒 / 每日複習 (Android 13+ 動態申請) |
| FileProvider (`${applicationId}.fileprovider`) | 本機影片交外部播放器，需 `grantUriPermissions`，配置見 `res/xml/file_paths.xml` |

備份還原走系統檔案選擇器，不需 `READ/WRITE_EXTERNAL_STORAGE`。

---

## 15. 常見問題 FAQ

**Q: 分享到 Reater 沒反應？**
A: 確認是用系統分享選 Reater (不是複製連結)。若仍無，改用主頁貼連結方式，並檢查該貼文是否公開。

**Q: 影片播不出來？**
A: 先看是否已下載完成 (卡片轉圈即下載中)；本機影片預設用外部播放器開，需有可播 MP4 的播放器。

**Q: AI 一直沒摘要？**
A: 三處檢查：設定 AI 總開關有開、Key 正確且有額度、該篇已有內文。失敗會保留本機分類，不會丟文。

**Q: 新啟用碼驗不過？**
A: 九成是兩邊 secret 不一致 (`tools/.license_secret` vs `LicenseVerifier.kt` 的 `P0..P3`)，一律用 `rotate_secret.py` 輪換，不要手改一半。

**Q: assembleRelease 失敗？**
A: 先看 `build-debug.log` / `--stacktrace`；常見為 JDK 非 21、SDK 缺 36、或離線模式缺快取 (拿掉 `--offline` 重跑一次)。

**Q: 換手機怎麼搬？**
A: 舊機匯出 JSON → 新機匯入 JSON → 等媒體補抓完成 → 對一次未讀數即完成。

---

## 16. 安全與隱私說明

- 本機優先：貼文、筆記、設定預設只存本機 Room + DataStore，不上雲。
- AI 需明確同意：`aiTransmissionConsent=false` 預設，不勾選就不送任何內文出去。
- API Key 經 Tink 加密存放，不寫入備份 JSON 明文。
- Pro secret (`P0..P3`) 僅為 XOR 混淆，防順手破解不防逆向；外流請立刻 `rotate_secret.py` + 重發版 (見 `tools/README.md` 誠實版說明)。
- 備份檔含全部收藏明文，請勿直接傳到公開空間。

---

> 維護提示：改 `AppDatabase` Entity 記得遞增版本並讓 Room 產生新 `app/schemas/...json`；改商品 ID 只改 `app/build.gradle.kts` 的 `PRO_PRODUCT_ID`；改 Pro 密碼只用 `tools/rotate_secret.py`。
