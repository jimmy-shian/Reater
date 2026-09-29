# Reater Pro 啟用碼工具說明（白話版）

## 先搞懂三件事（審查後的誠實版本）

1. **偽造難度是 40-bit，不是 60-bit。**
   票面 12 碼 = 編號 4 碼（20-bit）+ 驗證碼 8 碼（40-bit）。
   攻擊者可以固定編號、只猜後 8 碼，所以是 1.1 兆分之一。
   擋順手破解夠用，不要拿來吹高安全性。

2. **serial 不保證全球唯一。**
   20-bit 只有約 100 萬種，發到上千張一定會撞。
   撞了不會壞，只是同 Email 會拿到「完全相同的一組碼」
   （因為驗證碼是 `HMAC(Email + serial)` 算出來的）。
   發碼腳本靠 `licenses_issued.csv` 帳本自動重抽，實務上避開同 Email 重複。

3. **App 裡的 Secret 藏不住高手。**
   `P0..P3` 只是 XOR 切段混淆，反編譯拼得回去。
   一旦 Secret 外流，對方就能自印任意 Email 的合法碼。
   所以這套目標是「提高分享與破解成本」（WinRAR 式），不防改 APK 的人。

## 日常發碼

```bash
python tools/generate_license.py someone@example.com
python tools/generate_license.py someone@example.com --count 5
```

- Email 規定：trim + 小寫，只收 ASCII 常見格式（跟 App 端同一套規則）。
- 發過的碼會記到 `tools/licenses_issued.csv`（台灣時間），同 Email 抽到用過的編號會自動重抽。
- 測試不想污染帳本：加 `--no-log`。

## 改密碼（一鍵輪換，最常用）

```bash
# 先預覽，不寫檔
python tools/rotate_secret.py --dry-run

# 確定要換，正式執行
python tools/rotate_secret.py
```

它一次做完 4 件事，不會只改一半：

1. 生 32-byte 新 secret，寫入 `tools/.license_secret`（已 gitignore）。
2. 同步更新 `tools/generate_license.py` 的 `LICENSE_SECRET_HEX`。
3. 同步更新 `app/.../LicenseVerifier.kt` 的 `P0..P3`。
4. 把舊 `licenses_issued.csv` 歸檔成 `licenses_issued_<日期>.bak`。

換完記得：**重編 APK + 舊碼全部重發**（舊 secret 的碼即刻失效）。

## 檔案一覽

| 檔案 | 用途 | 能不能進 git |
| --- | --- | --- |
| `tools/generate_license.py` | 發碼 + 自驗 | 可以 |
| `tools/rotate_secret.py` | 一鍵換密碼 | 可以 |
| `tools/README.md` | 就是這份 | 可以 |
| `tools/.license_secret` | 真正的 secret（優先讀取） | 不行（已忽略） |
| `tools/licenses_issued.csv` | 發行帳本，避重 + 對帳 | 建議不要，備查就好 |
| `tools/licenses_issued_*.bak` | 輪換時自動歸檔的舊帳本 | 不要 |

## 救急

- **新碼驗不過**：先查兩邊 secret 是否一致（`.license_secret` vs App 內 `P0..P3`），
  九成是手動只改了一邊。以後一律用 `rotate_secret.py`。
- **懷疑 secret 外流**：立刻跑一次 `rotate_secret.py`，重編 APK，舊碼作廢。
- **Email 含中文或特殊字**：第一版不支援，請用戶給 ASCII 信箱。
