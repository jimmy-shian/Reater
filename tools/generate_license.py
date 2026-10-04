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
    python tools/generate_license.py jimmy910824@gmail.com --txt      # 另存寄信範本 txt
    python tools/generate_license.py jimmy910824@gmail.com --out=mail.txt  # 指定 txt 路徑(單組時)
"""
import csv
import sys
import os
import re
import hmac
import hashlib
import secrets
import urllib.parse
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


def make_pro_links(email: str, key: str) -> tuple[str, str]:
    """一鍵開通連結：複製後到 App「解鎖→貼上開通連結」填入（需 App 已安裝新版）。

    只發 reater:// 主連結（Android 直接開 App；Gmail 常顯示成純文字，長按複製即可）。
    不再發 https://reater.app 備用——該網域不存在，點了只會報「沒有該網域」，
    根本到不了 App。query 與 MainActivity.parseProUnlock 對應。
    回傳 (app_link, "")：第二欄保留空字串，相容舊呼叫方。
    """
    mail = canonicalize_email(email)
    q = urllib.parse.urlencode({"email": mail, "code": key.strip()})
    return (f"reater://pro-unlock?{q}", "")


def email_template(email: str, key: str) -> str:
    """寄信直接貼：主旨 + 內文（含一鍵開通連結與手動備案）。

    直接複製整段貼到 Gmail 寄出即可，含歡迎語、開通步驟、
    遺失補發與不可轉售說明。
    """
    mail = canonicalize_email(email)
    app_link, _ = make_pro_links(mail, key)
    return (
        f"主旨：Reater Pro 啟用碼｜歡迎使用 Reater Pro\n"
        f"\n"
        f"您好，\n"
        f"\n"
        f"歡迎使用 Reater Pro，感謝您的購買與支持！\n"
        f"您的 Pro 啟用資訊如下（本碼與申請 Email 綁定）：\n"
        f"\n"
        f"申請 Email：{mail}\n"
        f"啟用碼：{key}\n"
        f"\n"
        f"★ 一鍵開通連結（若點不開請長按複製，再到 App 貼上）：\n"
        f"{app_link}\n"
        f"\n"
        f"開通方式（三選一）：\n"
        f"1. 連結能點：手機直接點上方連結，即開啟 App 自動解鎖。\n"
        f"2. 連結點不開（Gmail 常見）：長按複製整段連結 → 開啟 Reater →\n"
        f"   解鎖 →「貼上開通連結自動填入」→ 按「驗證並啟用」。\n"
        f"3. 手動輸入：開啟 Reater → 設定 → 輸入啟用碼 →\n"
        f"   輸入 Email（{mail}）+ 啟用碼（{key}）→ 按「驗證並啟用」。\n"
        f"\n"
        f"【遺失補發】\n"
        f"啟用碼遺失或換手機找不到信，可直接回覆本信或來信申請再次寄送，\n"
        f"請務必使用原申請 Email（{mail}）來信，主旨註明「Reater Pro 啟用碼補發」，\n"
        f"我們會免費再次寄送同一組啟用碼，無需重複購買。\n"
        f"\n"
        f"【使用與保密說明】\n"
        f"1. 一組啟用碼僅綁定一個 Email，僅供您本人使用。\n"
        f"2. 請勿公開、轉貼、轉售或分享啟用碼與開通連結。\n"
        f"3. 啟用碼離線即可驗證，請妥善保存本信以便日後重裝使用。\n"
        f"\n"
        f"若開通失敗，請回信附上 Email 與啟用碼截圖，我們會協助處理。\n"
        f"聯絡信箱：jimmy910824@gmail.com\n"
        f"\n"
        f"祝使用愉快！\n"
        f"Reater 團隊 敬上\n"
    )


def save_email_txt(email: str, key: str, out_path: Path | None = None) -> Path:
    """把寄信範本存成 txt，方便直接當附件或貼信使用（UTF-8）。"""
    mail = canonicalize_email(email)
    serial = key.replace("-", "")[3:7]
    safe_mail = re.sub(r"[^A-Za-z0-9._-]+", "_", mail)
    if out_path is None:
        out_path = TOOLS_DIR / f"email_{safe_mail}_{serial}.txt"
    out_path.write_text(email_template(email, key), encoding="utf-8")
    return out_path


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--rotate" in sys.argv:
        print("請改用 tools/rotate_secret.py 一鍵輪換 (它會自動寫檔+貼片,避免只改一半)。")
        print("真的只想預覽新 secret 才用舊模式:先看 tools/README.md 再決定。")
        sys.exit(2)

    count = 1
    no_log = "--no-log" in sys.argv
    save_txt = "--txt" in sys.argv or any(a.startswith("--out") for a in sys.argv)
    out_arg: str | None = None
    for a in sys.argv[1:]:
        if a.startswith("--out="):
            out_arg = a.split("=", 1)[1]
        elif a == "--out":
            save_txt = True
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
        for idx in range(count):
            key = generate_key(email, secret, used)
            ok = verify_key(email, key, secret)  # 自我檢查,確保 App 端也能過
            serial = key.replace("-", "")[3:7]
            if not no_log:
                append_ledger(canonicalize_email(email), key, serial)
            app_link, _ = make_pro_links(email, key)
            print(f"   啟用碼:   {key}  (自驗: {'OK' if ok else 'FAIL'})")
            print(f"   一鍵開通(複製到 App 貼上): {app_link}")
            print("   ---- 寄信範本（直接複製貼上） ----")
            print(email_template(email, key))
            if save_txt:
                if out_arg and count == 1:
                    txt_path = save_email_txt(email, key, Path(out_arg))
                else:
                    txt_path = save_email_txt(email, key)
                print(f"   已存 txt: {txt_path}")
                if out_arg and count > 1 and idx == 0:
                    print(f"   提示: --out 僅在 --count=1 時指定單檔，多組時自動按 serial 分檔。")
    except ValueError as e:
        print(f"   錯誤: {e}")
        sys.exit(1)
    print("   注意:舊萬用碼與舊 PRO-XXXXXXXX 均已失效,需重發。")
    print("=" * 45)
