#!/usr/bin/env python3
"""
Reater Pro 離線啟用碼產生器 (與 app/.../LicenseVerifier.kt 對應)

設計說明請看 tools/README.md,不寫在程式裡。

secret 來源順序:環境變數 REATER_LICENSE_SECRET > tools/.license_secret
> 下方 LICENSE_SECRET_HEX。
換密碼請用 tools/rotate_secret.py 一鍵輪換,不要手動只改一邊,否則新碼驗不過。

用法:
    python tools/generate_license.py jimmy910824@gmail.com
    python tools/generate_license.py jimmy910824@gmail.com --count 5
    python tools/generate_license.py jimmy910824@gmail.com --no-log   # 不寫入帳本(測試用)
"""
import csv
import sys
import os
import re
import hmac
import hashlib
import secrets
from datetime import datetime, timezone, timedelta
from pathlib import Path

ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
DOMAIN = "REATER-PROv1"
MASK = 0x5A
EMAIL_RE = re.compile(r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")

# 管理端預設 secret (64 hex = 32 bytes)。換密碼請用 rotate_secret.py,勿手動改一半。
LICENSE_SECRET_HEX = "c8073d50601e879c9222a35de66212ae6e191504280c71c89152fbfb3ec662ad"

TOOLS_DIR = Path(__file__).resolve().parent
LEDGER_FILE = TOOLS_DIR / "licenses_issued.csv"


def load_secret() -> bytes:
    env = os.environ.get("REATER_LICENSE_SECRET", "").strip()
    if env:
        return bytes.fromhex(env)
    secret_file = TOOLS_DIR / ".license_secret"
    if secret_file.exists():
        return bytes.fromhex(secret_file.read_text(encoding="utf-8").strip())
    return bytes.fromhex(LICENSE_SECRET_HEX)


def canonicalize_email(email: str) -> str:
    """與 Kotlin LicenseVerifier.canonicalizeEmail 完全對應。"""
    mail = email.strip().lower()
    if not mail or len(mail) > 254:
        raise ValueError("Email 不可為空且長度需 <= 254")
    try:
        mail.encode("ascii")
    except UnicodeEncodeError:
        raise ValueError("Email 第一版僅支援 ASCII (避免 Python/Kotlin Unicode 大小寫差異)")
    if not EMAIL_RE.match(mail):
        raise ValueError(f"Email 格式不接受: {email.strip()}")
    return mail


def encode_40bit(five: bytes) -> str:
    assert len(five) == 5
    acc = int.from_bytes(five, "big")
    return "".join(ALPHABET[(acc >> (35 - i * 5)) & 31] for i in range(8))


def encode_serial20(value: int) -> str:
    assert 0 <= value < (1 << 20)
    return "".join(ALPHABET[(value >> (15 - i * 5)) & 31] for i in range(4))


def load_ledger_serials() -> set:
    """讀回 (email, serial) 已發行組合,用於避開同 Email 重複。"""
    used = set()
    if not LEDGER_FILE.exists():
        return used
    try:
        with LEDGER_FILE.open("r", encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                if row.get("email") and row.get("serial"):
                    used.add((row["email"].strip().lower(), row["serial"].strip().upper()))
    except Exception:
        pass
    return used


def append_ledger(email: str, key: str, serial: str) -> None:
    is_new = not LEDGER_FILE.exists()
    tz = timezone(timedelta(hours=8))  # 台灣時間
    now = datetime.now(tz).isoformat(timespec="seconds")
    with LEDGER_FILE.open("a", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        if is_new:
            w.writerow(["timestamp_taipei", "email", "key", "serial"])
        w.writerow([now, email, key, serial])


def generate_key(email: str, secret: bytes, used: set | None = None) -> str:
    mail = canonicalize_email(email)
    for _ in range(50):  # 抽到同 Email 用過的 serial 就重抽,實務上幾乎一次就中
        serial = encode_serial20(secrets.randbits(20))
        if used is None or (mail, serial) not in used:
            break
    else:
        raise RuntimeError("serial 重抽 50 次仍碰撞,帳本可能異常,請檢查 licenses_issued.csv")
    msg = f"{DOMAIN}:{mail}:{serial}".encode("utf-8")
    auth = encode_40bit(hmac.new(secret, msg, hashlib.sha256).digest()[:5])
    body = serial + auth  # 12 chars
    if used is not None:
        used.add((mail, serial))
    return f"PRO-{body[0:4]}-{body[4:8]}-{body[8:12]}"


def verify_key(email: str, code: str, secret: bytes) -> bool:
    try:
        mail = canonicalize_email(email)
    except ValueError:
        return False
    body = code.strip().upper().replace("-", "").replace(" ", "")
    if body.startswith("PRO"):
        body = body[3:]
    if len(body) != 12 or any(c not in ALPHABET for c in body):
        return False
    serial, auth_given = body[:4], body[4:]
    msg = f"{DOMAIN}:{mail}:{serial}".encode("utf-8")
    auth_expected = encode_40bit(hmac.new(secret, msg, hashlib.sha256).digest()[:5])
    return hmac.compare_digest(auth_given, auth_expected)


def obfuscated_patch(secret: bytes) -> str:
    masked = bytes(b ^ MASK for b in secret)
    parts = [", ".join(str(b) for b in masked[i * 8:(i + 1) * 8]) for i in range(4)]
    lines = [f"    private val P{i} = intArrayOf({parts[i]})" for i in range(4)]
    return "\n".join(lines)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--rotate" in sys.argv:
        print("請改用 tools/rotate_secret.py 一鍵輪換 (它會自動寫檔+貼片,避免只改一半)。")
        print("真的只想預覽新 secret 才用舊模式:先看 tools/README.md 再決定。")
        sys.exit(2)

    count = 1
    no_log = "--no-log" in sys.argv
    for i, a in enumerate(sys.argv[1:]):
        if a == "--count" and i + 1 < len(sys.argv[1:]):
            try:
                count = max(1, int(sys.argv[1:][i + 1]))
            except Exception:
                count = 1
        elif a.startswith("--count="):
            try:
                count = max(1, int(a.split("=", 1)[1]))
            except Exception:
                count = 1

    if not args:
        print(__doc__)
        print("範例: python tools/generate_license.py jimmy910824@gmail.com --count 3")
        sys.exit(1)

    email = args[0]
    try:
        canonicalize_email(email)
    except ValueError as e:
        print(f"錯誤: {e}")
        sys.exit(1)

    secret = load_secret()
    used = None if no_log else load_ledger_serials()
    print("=" * 45)
    print("   Reater Pro 啟用碼產生器 (HMAC 40-bit,離線可驗)")
    print(f"   輸入對象: {canonicalize_email(email)}")
    print(f"   帳本: {'不記錄(--no-log)' if no_log else str(LEDGER_FILE.name)}")
    try:
        for _ in range(count):
            key = generate_key(email, secret, used)
            ok = verify_key(email, key, secret)  # 自我檢查,確保 App 端也能過
            serial = key.replace("-", "")[3:7]
            if not no_log:
                append_ledger(canonicalize_email(email), key, serial)
            print(f"   啟用碼:   {key}  (自驗: {'OK' if ok else 'FAIL'})")
    except ValueError as e:
        print(f"   錯誤: {e}")
        sys.exit(1)
    print("   注意:舊萬用碼與舊 PRO-XXXXXXXX 均已失效,需重發。")
    print("=" * 45)
