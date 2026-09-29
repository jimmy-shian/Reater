package com.reater.app.data.repository

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Reater Pro 離線啟用碼驗證器 (HMAC-SHA256 短碼方案)。
 *
 * Key 格式:PRO-XXXX-XXXX-XXXX,body 12 碼 = serial(4 碼) + auth(8 碼)。
 * 驗證方式:重算 HMAC(secret, "REATER-PROv1:<email>:<serial>") 前段是否等於後 8 碼。
 * Email 規則:trim + lowercase,僅 ASCII 常見格式 (與 tools/generate_license.py 對應)。
 * 安全與設計說明請看 tools/README.md,不寫在程式裡。
 *
 * 換 secret:請用 tools/rotate_secret.py 一鍵輪換,兩邊必須一致:
 * - App 端:本檔案下方 P0..P3。
 * - 管理端:tools/.license_secret (或 REATER_LICENSE_SECRET / LICENSE_SECRET_HEX)。
 */
object LicenseVerifier {
    private const val MASK = 0x5A
    private const val DOMAIN = "REATER-PROv1"

    // 32-byte secret 經 XOR 0x5A 混淆後切成 4 段存放。換 secret 請跑 tools/rotate_secret.py,勿手改。
    private val P0 = intArrayOf(146, 93, 103, 10, 58, 68, 221, 198)
    private val P1 = intArrayOf(200, 120, 249, 7, 188, 56, 72, 244)
    private val P2 = intArrayOf(52, 67, 79, 94, 114, 86, 43, 146)
    private val P3 = intArrayOf(203, 8, 161, 161, 100, 156, 56, 247)

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val CHAR_TO_VAL: Map<Char, Int> by lazy {
        ALPHABET.mapIndexed { i, c -> c to i }.toMap()
    }
    private val EMAIL_RE = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private fun licenseSecret(): ByteArray {
        val masked = P0 + P1 + P2 + P3
        return ByteArray(masked.size) { i -> (masked[i] xor MASK).toByte() }
    }

    /** 把使用者輸入正規化:去空白/連字號、轉大寫、去掉 PRO 前綴。 */
    fun normalize(code: String): String {
        var s = code.trim().uppercase().replace("-", "").replace(" ", "")
        if (s.startsWith("PRO")) s = s.removePrefix("PRO")
        return s
    }

    /** Email 正規化:trim + lowercase,僅接受 ASCII 常見格式,與 Python 端完全對應。 */
    fun canonicalizeEmail(email: String): String? {
        val mail = email.trim().lowercase()
        if (mail.isEmpty() || mail.length > 254) return null
        if (mail.any { it.code > 127 }) return null
        if (!EMAIL_RE.matches(mail)) return null
        return mail
    }

    fun verify(email: String, code: String): Boolean {
        val mail = canonicalizeEmail(email) ?: return false
        val body = normalize(code)
        if (body.length != 12) return false
        if (body.any { it !in CHAR_TO_VAL }) return false
        val serial = body.substring(0, 4)
        val authGiven = body.substring(4, 12)
        val authExpected = authFor(mail, serial) ?: return false
        return MessageDigest.isEqual(
            authGiven.toByteArray(Charsets.US_ASCII),
            authExpected.toByteArray(Charsets.US_ASCII)
        )
    }

    /** 重算某 email+serial 應該對應的 8 碼 auth,供驗證與單元測試使用。 */
    fun authFor(emailLower: String, serial4: String): String? {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(licenseSecret(), "HmacSHA256"))
            val msg = "$DOMAIN:$emailLower:${serial4.uppercase()}".toByteArray(Charsets.UTF_8)
            val digest = mac.doFinal(msg)
            encode40Bit(digest.copyOfRange(0, 5))
        } catch (_: Exception) {
            null
        }
    }

    /** 5 bytes (40-bit, big-endian) -> 8 個 Base32 字元,與 tools/generate_license.py 完全對應。 */
    internal fun encode40Bit(five: ByteArray): String {
        require(five.size == 5)
        var acc = 0L
        for (b in five) acc = (acc shl 8) or (b.toInt() and 0xFF).toLong()
        val sb = StringBuilder(8)
        for (i in 0 until 8) {
            val shift = 35 - i * 5
            sb.append(ALPHABET[((acc shr shift) and 31L).toInt()])
        }
        return sb.toString()
    }
}
