# Reater

Threads 稍後閱讀 APP，將貼文、留言、圖片、影片與文字附件收藏在手機中，搭配分類、筆記、搜尋與閱讀提醒，整理自己的閱讀資料庫。

<p align="center">
  <img src="app/src/main/ic_launcher-web.png" alt="Reater APP 圖示：書頁與鋼筆" width="192" />
</p>

目前版本：**1.0.4** · Android **8.0 以上**

## APP 功能

### 收藏與閱讀

- 從 Threads 或瀏覽器分享連結到 Reater，或在主頁輸入貼文連結。
- 擷取貼文內文、作者資訊、留言、圖片、影片與獨立文字附件。
- 保存已下載的媒體供離線閱讀，文字附件提供複製功能。
- 重複收藏時可選擇覆蓋更新、另存新紀錄；垃圾桶中的文章可還原更新。
- 貼文卡片與詳情頁支援閱讀、收藏、筆記、摘要、分享及原文連結。
- 圖片支援縮放與切換；影片支援行內播放、全螢幕、靜音與播放控制，全螢幕媒體支援垂直滑動關閉。

### 分類與整理

- 自訂分類名稱與圖示，支援拖曳排序、分類搜尋與篇數顯示。
- 使用標籤、關鍵字搜尋、已讀、未讀與收藏狀態篩選文章。
- Pro 智慧收藏支援 AND／OR 條件、文字比對、互動數、媒體類型、時間與分類關鍵字，提供符合文章預覽與命中原因。
- 內建高讚熱門、長期必讀、靈感庫智慧規則，可編輯條件與恢復預設。

### AI、個人化與提醒

- 設定 OpenAI 相容端點、API Key 與模型，從端點取得可用模型清單。
- 授權傳送貼文與留言後，使用 AI 分類與摘要。
- 支援亮色、暗色、系統主題與字體縮放。
- 圖示展覽室提供內建圖示；Pro 可選擇相簿圖片、裁切並管理歷史頭像。
- 未讀提醒、每日回顧、閱讀統計與桌面小工具。
- Pro 支援 Google Play 一次性購買、Email 與啟用碼驗證、啟用連結。

### 備份與還原

在設定中選擇「匯出備份」，將 `.reater` 檔案保存至系統檔案選擇器指定的位置。重新安裝或換機後，選擇「匯入備份」還原。

目前匯出格式為 v2 ZIP 容器，包含 JSON 資料清單、成功取得的媒體、頭像與裁切設定；涵蓋文章、留言、分類、標籤、筆記、智慧收藏、儲存查詢、AI 執行紀錄、抓取紀錄與 APP 設定。匯入時驗證資源大小與 SHA-256，並支援既有 v1 JSON 備份。匯出時取得失敗的媒體保留原始網址，供後續擷取。

API Key 使用 Tink 加密儲存在裝置。備份包含收藏內容與授權資訊，請保存於受控位置。「保留可還原的 Reater 資料」選項會開啟備份匯出流程，完成後可於重新安裝時匯入。

## 專案說明

| 項目 | 設定 |
| --- | --- |
| 套件名稱 | `com.reater.app` |
| APP 版本 | `versionName = "1.0.4"`、`versionCode = 4` |
| Android SDK | min 26、compile / target 36 |
| Java / Kotlin JVM | 21 |
| Gradle / Android Gradle Plugin | 8.11.1 / 8.10.1 |
| Kotlin | 2.3.21 |
| 介面 | Jetpack Compose、Material 3 |
| 本機資料 | Room 3、Bundled SQLite、DataStore |
| 資料庫版本 | 11，schema 位於 `app/schemas/` |
| 相依注入 / 網路 | Hilt、OkHttp、kotlinx.serialization |
| 背景工作 / 小工具 | WorkManager、Glance |

### 原始碼結構

```text
app/
  schemas/                  Room schema 與版本紀錄
  src/main/java/com/reater/app/
    data/backup/            .reater 備份、驗證與還原
    data/local/             Entity、DAO、資料庫遷移
    data/remote/            Threads 擷取與 OpenAI 相容 API
    data/repository/        收藏與設定資料操作
    domain/                 分類、智慧收藏與文字附件
    ui/                     主頁、詳情、分享儲存、設定與播放器
    notify/                 閱讀提醒
    widget/                 桌面小工具
  src/test/                 單元測試與測試資料
build-release.bat           Windows release 編譯與簽署
.github/workflows/          tag 觸發的 APK 建置與發布
tools/                     Pro 啟用碼管理工具
```

### 開發與驗證

安裝 JDK 21、Android SDK Platform 36 與 Build Tools 35.0.0，使用 Android Studio 開啟專案。在 `local.properties` 設定 `sdk.dir`，或設定 `ANDROID_HOME`。

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
Release 編譯產物：`app/build/outputs/apk/release/app-release-unsigned.apk`，供簽署流程使用。

互動驗證項目見 [Android 互動驗證紀錄](docs/android-interaction-validation.md)。Pro 啟用碼管理見 [tools 說明](tools/README.md)。

### Windows BAT 編譯與簽署

`build-release.bat` 執行 clean、release 編譯、zipalign、簽署與簽章驗證，將成品輸出至根目錄 `Reater-v1.0.4.apk`。簽署設定來自目前命令列環境變數：

| 變數 | 用途 |
| --- | --- |
| `REATER_KEYSTORE_PATH` | release keystore 完整路徑 |
| `REATER_KEY_ALIAS` | 簽署別名，預設 `mykey` |
| `REATER_STORE_PASS` | keystore 密碼 |
| `REATER_KEY_PASS` | key 密碼 |

```powershell
.\build-release.bat
.\build-release.bat --dry-run
.\build-release.bat --keep-daemon
.\build-release.bat --skip-build
```

`--dry-run` 顯示版本、SDK 與輸出路徑，會先驗證簽署設定與 SDK 路徑。`--skip-build` 使用現有 unsigned APK 重新簽署，適用於該產物仍存在時。BAT 完成後保留根目錄成品並清理中繼 APK 與根目錄 `.log`。

### GitHub Actions 雲端編譯與發布

在 GitHub 儲存庫的 **Settings → Secrets and variables → Actions** 設定以下 repository secrets：

| Secret | 內容 |
| --- | --- |
| `REATER_KEYSTORE_BASE64` | release keystore 的完整 Base64 內容 |
| `REATER_KEY_ALIAS` | release key 別名 |
| `REATER_STORE_PASS` | keystore 密碼 |
| `REATER_KEY_PASS` | key 密碼 |

雲端沿用本機 release keystore、key 別名及密碼；workflow 會核對 APK 簽署憑證的 SHA-256，確保與本機版本一致，維持覆蓋更新的簽章相容性。Android 也會檢查套件名稱與版本號，1.0.4 使用相同套件名稱並遞增 versionCode。以下指令將 Base64 複製到剪貼簿，貼入 GitHub secret：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes($env:REATER_KEYSTORE_PATH)) | Set-Clipboard
```

`.github/workflows/release-apk.yml` 由推送 `v主版號.次版號.修訂號` tag 觸發，例如 `v1.0.4`。GitHub Actions 使用 Ubuntu 與 JDK 21，依序執行：

1. 核對 tag 與 `versionName`，驗證必要 secrets。
2. 安裝 SDK、執行單元測試並編譯 release APK。
3. 對齊 APK、使用 release keystore 簽署並驗證簽章。
4. 產生 `Reater-v1.0.4.apk` 與 `.apk.sha256`，上傳至 Actions artifact，保留 30 天。
5. 建立對應 GitHub Release 並附加 APK 與校驗檔；重新執行會更新同名附件。

成品可在 **Actions → Build and release APK → 對應執行紀錄 → Artifacts** 或 **Releases → 對應版本** 下載。發布工作使用該次執行的 `GITHUB_TOKEN` 與 `contents: write` 權限。

簽署資料以加密的 repository secrets 保存，keystore 在 runner 暫存目錄解碼，簽署結束後清除。Secrets 僅傳入驗證及簽署步驟，第三方 Actions 固定使用 commit SHA。上傳成品為 APK 與校驗檔，私鑰及密碼由 secrets 管理。請保留原始 keystore 備份，並限制儲存庫與 workflow 的寫入權限；具有修改 workflow 權限的人仍可變更 secrets 的使用方式。

參考 [Android APK 簽署與更新](https://developer.android.com/studio/publish/app-signing)及 [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)。

### 版本與 tag

發版時先更新 `app/build.gradle.kts` 的 `versionName` 與遞增 `versionCode`，將本次發版內容完成 commit，再於該 commit 建立 annotated tag。以下為提交完成後的發版指令：

```powershell
git tag -a v1.0.4 -m "Reater 1.0.4"
git push origin master
git push origin v1.0.4
```

推送 tag 後，GitHub Actions 會從 tag 指向的 commit 編譯 APK。

流程設定參考 [GitHub Actions workflow 語法](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)、[Gradle Actions](https://github.com/gradle/actions) 與 [Artifact 上傳](https://github.com/actions/upload-artifact)。
