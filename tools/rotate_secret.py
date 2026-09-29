#!/usr/bin/env python3
"""
一鍵輪換 Reater Pro 啟用碼 secret。

做了什麼 (一次搞定,不會只改一半):
  1. 產生 32-byte 新 secret。
  2. 寫入 tools/.license_secret (git 忽略,優先讀取)。
  3. 自動更新 tools/generate_license.py 的 LICENSE_SECRET_HEX (保持一致)。
  4. 自動更新 app/.../LicenseVerifier.kt 的 P0..P3 (XOR 0x5A 混淆貼片)。
  5. 歸檔舊帳本 tools/licenses_issued.csv -> licenses_issued_<日期>.bak
     (舊 secret 發的碼會全失效,帳本留著備查但不再用於避重)。

用法:
    python tools/rotate_secret.py              # 正式輪換 (會改檔)
    python tools/rotate_secret.py --dry-run    # 只預覽,不寫檔

輪換後記得:重新編譯 APK,並通知舊用戶重領啟用碼。
"""
import re
import secrets
import shutil
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path

MASK = 0x5A
TOOLS_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = TOOLS_DIR.parent
SECRET_FILE = TOOLS_DIR / ".license_secret"
GENERATOR = TOOLS_DIR / "generate_license.py"
VERIFIER = PROJECT_ROOT / "app/src/main/java/com/reater/app/data/repository/LicenseVerifier.kt"
LEDGER = TOOLS_DIR / "licenses_issued.csv"


def obfuscated_lines(secret: bytes) -> list[str]:
    masked = bytes(b ^ MASK for b in secret)
    parts = [", ".join(str(b) for b in masked[i * 8:(i + 1) * 8]) for i in range(4)]
    return [f"    private val P{i} = intArrayOf({parts[i]})" for i in range(4)]


def patch_verifier(lines: list[str]) -> int:
    text = VERIFIER.read_text(encoding="utf-8")
    count = 0
    for i, newline in enumerate(lines):
        pat = re.compile(rf"^    private val P{i} = intArrayOf\([^)]*\)$", re.MULTILINE)
        text, n = pat.subn(newline, text, count=1)
        count += n
    if count != 4:
        raise RuntimeError(f"LicenseVerifier.kt 只 patch 到 {count}/4 行,請檢查 P0..P3 格式是否被手改過")
    VERIFIER.write_text(text, encoding="utf-8")
    return count


def patch_generator(secret_hex: str) -> None:
    text = GENERATOR.read_text(encoding="utf-8")
    new_text, n = re.subn(
        r'^LICENSE_SECRET_HEX = "[0-9a-fA-F]+"',
        f'LICENSE_SECRET_HEX = "{secret_hex}"',
        text,
        count=1,
        flags=re.MULTILINE,
    )
    if n != 1:
        raise RuntimeError("generate_license.py 的 LICENSE_SECRET_HEX 找不到,請檢查是否被手改過")
    GENERATOR.write_text(new_text, encoding="utf-8")


def main() -> None:
    dry_run = "--dry-run" in sys.argv
    new_secret = secrets.token_bytes(32)
    secret_hex = new_secret.hex()
    lines = obfuscated_lines(new_secret)

    print("=" * 55)
    print("  Reater Pro secret 一鍵輪換" + (" (預覽,不寫檔)" if dry_run else ""))
    print(f"  新 secret: {secret_hex}")
    print("  P0..P3 貼片:")
    for ln in lines:
        print("  " + ln)

    if dry_run:
        print("  --dry-run:未寫入任何檔案。拿掉參數即正式執行。")
        print("=" * 55)
        return

    SECRET_FILE.write_text(secret_hex + "\n", encoding="utf-8")
    patch_generator(secret_hex)
    patch_verifier(lines)

    if LEDGER.exists():
        tz = timezone(timedelta(hours=8))
        stamp = datetime.now(tz).strftime("%Y%m%d_%H%M%S")
        backup = TOOLS_DIR / f"licenses_issued_{stamp}.bak"
        shutil.move(str(LEDGER), str(backup))
        print(f"  舊帳本已歸檔: {backup.name} (舊碼已全失效,備查用)")

    print(f"  已寫入: {SECRET_FILE.name} + generate_license.py + LicenseVerifier.kt")
    print("  下一步:重新編譯 APK,舊啟用碼需全部重發。")
    print("=" * 55)


if __name__ == "__main__":
    main()
