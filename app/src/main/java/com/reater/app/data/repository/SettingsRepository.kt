package com.reater.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "reater_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_ENCRYPTED_API_KEY = stringPreferencesKey("openai_api_key_enc")
    private val KEY_BASE_URL = stringPreferencesKey("custom_base_url")
    private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_ai_model")
    private val KEY_AUTO_FETCH_ENABLED = booleanPreferencesKey("auto_fetch_enabled")
    private val KEY_AI_CONSENT = booleanPreferencesKey("ai_transmission_consent")
    private val KEY_PRO_UNLOCKED = booleanPreferencesKey("pro_unlocked")
    // Pro 雙軌授權：Play 購買與離線啟用碼各自獨立，isPro = 任一為 true。
    // 舊版只有單一 pro_unlocked，每次 Play 對帳無購買就會寫 false 蓋掉啟用碼，
    // 造成「更新/重開 App 就掉 Pro」。新版 Play 只寫自己的旗標，不再清啟用碼。
    // KEY_PRO_UNLOCKED 保留為 legacy 遷移用（舊已解鎖用戶直接沿用）。
    private val KEY_PRO_PLAY = booleanPreferencesKey("pro_play_owned")
    private val KEY_PRO_LICENSED = booleanPreferencesKey("pro_licensed")
    private val KEY_PRO_EMAIL = stringPreferencesKey("pro_license_email")
    private val KEY_PRO_CODE = stringPreferencesKey("pro_license_code")
    // 外觀
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // system / light / dark
    private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
    // 通知：儲存後未讀提醒 / 每日回顧
    private val KEY_UNREAD_NUDGE_ENABLED = booleanPreferencesKey("unread_nudge_enabled")
    private val KEY_UNREAD_NUDGE_DELAY_MIN = intPreferencesKey("unread_nudge_delay_min")
    private val KEY_REVIEW_DIGEST_ENABLED = booleanPreferencesKey("review_digest_enabled")
    private val KEY_REVIEW_DIGEST_HOUR = intPreferencesKey("review_digest_hour")
    private val KEY_LAST_CATEGORY_ID = androidx.datastore.preferences.core.longPreferencesKey("last_selected_category_id")
    private val KEY_CUSTOM_AVATAR_ID = stringPreferencesKey("custom_avatar_id")
    private val KEY_CUSTOM_AVATAR_URI = stringPreferencesKey("custom_avatar_uri")
    // 自訂頭像歷史（由新到舊的內部絕對路徑，以 \n 連接，上限 AvatarStorage.MAX_HISTORY）
    private val KEY_CUSTOM_AVATAR_HISTORY = stringPreferencesKey("custom_avatar_history")

    private val aead: Aead by lazy {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "tink_keyset", "reater_key_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://reater_master_key")
            .build()
            .keysetHandle
        keysetHandle.getPrimitive(Aead::class.java)
    }

    val selectedModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_MODEL] ?: "gpt-5-nano"
    }

    val customBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: "https://api.openai.com/v1"
    }

    val autoFetchEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_FETCH_ENABLED] ?: true
    }

    val aiTransmissionConsent: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_CONSENT] ?: false
    }

    val isProUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[KEY_PRO_PLAY] ?: false) ||
            (prefs[KEY_PRO_LICENSED] ?: false) ||
            (prefs[KEY_PRO_UNLOCKED] ?: false)
    }

    /** 啟用碼綁定的 Email（供 Deep Link / 除錯顯示，不影響驗證）。 */
    val proLicenseEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRO_EMAIL]
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    val fontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SCALE] ?: 1f
    }

    val unreadNudgeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_UNREAD_NUDGE_ENABLED] ?: true
    }

    val unreadNudgeDelayMin: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_UNREAD_NUDGE_DELAY_MIN] ?: 10
    }

    val reviewDigestEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REVIEW_DIGEST_ENABLED] ?: true
    }

    val reviewDigestHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_REVIEW_DIGEST_HOUR] ?: 21
    }

    val lastSelectedCategoryId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_CATEGORY_ID]
    }

    suspend fun setLastSelectedCategoryId(categoryId: Long?) {
        context.dataStore.edit { prefs ->
            if (categoryId != null) {
                prefs[KEY_LAST_CATEGORY_ID] = categoryId
            } else {
                prefs.remove(KEY_LAST_CATEGORY_ID)
            }
        }
    }

    val customAvatarId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AVATAR_ID] ?: "life"
    }

    val customAvatarUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AVATAR_URI]
    }

    val customAvatarHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AVATAR_HISTORY]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setCustomAvatarId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_AVATAR_ID] = id
            // 切回內建圖示只清除 current，歷史照片保留以便隨時切回
            prefs.remove(KEY_CUSTOM_AVATAR_URI)
        }
    }

    suspend fun setCustomAvatarUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) {
                prefs[KEY_CUSTOM_AVATAR_URI] = uri
                // 手動指定路徑也納入歷史（回選過往圖片走這裡）
                val hist = (listOf(uri) + (prefs[KEY_CUSTOM_AVATAR_HISTORY]
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()))
                    .distinct()
                    .take(com.reater.app.ui.AvatarStorage.MAX_HISTORY)
                prefs[KEY_CUSTOM_AVATAR_HISTORY] = hist.joinToString("\n")
            } else {
                // 清除 current（退回圖示），歷史保留；真正刪除走 deleteCustomAvatar()
                prefs.remove(KEY_CUSTOM_AVATAR_URI)
            }
        }
    }

    /** 歷史回選：切換 current，不動歷史順序以外的任何檔案。 */
    suspend fun selectCustomAvatar(path: String) {
        val f = java.io.File(path)
        if (!f.isAbsolute || !f.exists() || f.length() <= 0) return
        setCustomAvatarUri(path)
    }

    /** 刪除單張歷史：刪檔 + 移出歷史；若刪的是使用中則一併清除 current 退回圖示。 */
    suspend fun deleteCustomAvatar(path: String) {
        runCatching { java.io.File(path).takeIf { it.isAbsolute }?.delete() }
        context.dataStore.edit { prefs ->
            val hist = (prefs[KEY_CUSTOM_AVATAR_HISTORY]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() } ?: emptyList())
                .filter { it != path }
            if (hist.isEmpty()) prefs.remove(KEY_CUSTOM_AVATAR_HISTORY)
            else prefs[KEY_CUSTOM_AVATAR_HISTORY] = hist.joinToString("\n")
            if (prefs[KEY_CUSTOM_AVATAR_URI] == path) prefs.remove(KEY_CUSTOM_AVATAR_URI)
        }
    }

    /**
     * 相簿 picker 回傳的 content:// URI 只是暫時授權，重開 App 即失效。
     * 此處拷貝成新的時間戳內部檔（filesDir/avatar/custom_avatar_<millis>.jpg），
     * 每次路徑都不同 -> Flow 必發新值，內外頭像即時同步且不命中 Coil 舊快取。
     * 同時寫入歷史（上限保留），回傳內部檔案絕對路徑；失敗回傳 null。
     */
    suspend fun importCustomAvatar(source: android.net.Uri): String? {
        val savedPath: String? = try {
            val dir = java.io.File(context.filesDir, "avatar").apply { mkdirs() }
            // 舊版單檔更名納入歷史
            runCatching {
                val legacy = java.io.File(dir, "custom_avatar.jpg")
                if (legacy.exists() && legacy.length() > 0) {
                    val renamed = java.io.File(dir, "custom_avatar_${legacy.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()}.jpg")
                    if (!renamed.exists()) legacy.renameTo(renamed)
                }
            }
            val dest = java.io.File(dir, "custom_avatar_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(System.currentTimeMillis())
                // 只裁多餘歷史，不刪當次
                dir.listFiles()
                    ?.filter {
                        it.name.startsWith("custom_avatar") &&
                            it.name.endsWith(".jpg") && it.absolutePath != dest.absolutePath
                    }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(com.reater.app.ui.AvatarStorage.MAX_HISTORY - 1)
                    ?.forEach { runCatching { it.delete() } }
                dest.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
        if (savedPath != null) {
            context.dataStore.edit { prefs ->
                prefs[KEY_CUSTOM_AVATAR_URI] = savedPath
                val hist = (listOf(savedPath) + (prefs[KEY_CUSTOM_AVATAR_HISTORY]
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()))
                    .distinct()
                    .take(com.reater.app.ui.AvatarStorage.MAX_HISTORY)
                prefs[KEY_CUSTOM_AVATAR_HISTORY] = hist.joinToString("\n")
            }
        }
        return savedPath
    }

    /** 裁切編輯器產出的 Bitmap 存成新的時間戳檔並設為使用中（同樣寫歷史、保證同步刷新）。 */
    suspend fun importAvatarBitmap(bitmap: android.graphics.Bitmap): String? {
        val savedPath: String? = try {
            val dir = java.io.File(context.filesDir, "avatar").apply { mkdirs() }
            val dest = java.io.File(dir, "custom_avatar_${System.currentTimeMillis()}.jpg")
            dest.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (dest.exists() && dest.length() > 0) {
                dest.setLastModified(System.currentTimeMillis())
                dir.listFiles()
                    ?.filter {
                        it.name.startsWith("custom_avatar") &&
                            it.name.endsWith(".jpg") && it.absolutePath != dest.absolutePath
                    }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(com.reater.app.ui.AvatarStorage.MAX_HISTORY - 1)
                    ?.forEach { runCatching { it.delete() } }
                dest.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
        if (savedPath != null) {
            context.dataStore.edit { prefs ->
                prefs[KEY_CUSTOM_AVATAR_URI] = savedPath
                val hist = (listOf(savedPath) + (prefs[KEY_CUSTOM_AVATAR_HISTORY]
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()))
                    .distinct()
                    .take(com.reater.app.ui.AvatarStorage.MAX_HISTORY)
                prefs[KEY_CUSTOM_AVATAR_HISTORY] = hist.joinToString("\n")
            }
        }
        return savedPath
    }

    /**
     * 開機自檢：舊版存的是暫時 content://，重開後讀不到會變空白圖。
     * 偵測到失效就清除該筆，自動退回內建圖示；同時過濾已遺失的歷史。
     */
    suspend fun validateCustomAvatar() {
        val prefs = context.dataStore.data.first()
        val stored = prefs[KEY_CUSTOM_AVATAR_URI]
        val hist = prefs[KEY_CUSTOM_AVATAR_HISTORY]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()
        // 磁碟實際存在的歷史（含舊版單檔殘留）
        val onDisk = try {
            java.io.File(context.filesDir, "avatar").listFiles()
                ?.filter {
                    (it.name.startsWith("custom_avatar") && it.name.endsWith(".jpg") ||
                        it.name == "custom_avatar.jpg") && it.exists() && it.length() > 0
                }
                ?.map { it.absolutePath } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val validHist = (hist + onDisk).distinct()
            .filter { p ->
                if (p.startsWith("content://")) {
                    try {
                        context.contentResolver.openInputStream(android.net.Uri.parse(p))?.close()
                        true
                    } catch (_: Exception) {
                        false
                    }
                } else if (p.startsWith("file://")) true
                else {
                    val f = java.io.File(p)
                    f.exists() && f.length() > 0
                }
            }
            .take(com.reater.app.ui.AvatarStorage.MAX_HISTORY)
        val currentValid = stored?.let { s ->
            if (s.startsWith("content://")) {
                try {
                    context.contentResolver.openInputStream(android.net.Uri.parse(s))?.close()
                    true
                } catch (_: Exception) {
                    false
                }
            } else if (s.startsWith("file://")) true
            else {
                val f = java.io.File(s)
                f.exists() && f.length() > 0
            }
        } ?: false
        context.dataStore.edit { e ->
            if (!currentValid) e.remove(KEY_CUSTOM_AVATAR_URI)
            if (validHist.isEmpty()) e.remove(KEY_CUSTOM_AVATAR_HISTORY)
            else e[KEY_CUSTOM_AVATAR_HISTORY] = validHist.joinToString("\n")
        }
    }

    suspend fun setOpenAiApiKey(apiKey: String) {
        val encryptedBase64 = if (apiKey.isNotBlank()) {
            val ciphertext = aead.encrypt(apiKey.toByteArray(StandardCharsets.UTF_8), null)
            Base64.getEncoder().encodeToString(ciphertext)
        } else {
            ""
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_API_KEY] = encryptedBase64
        }
    }

    suspend fun getOpenAiApiKey(): String? {
        val encrypted = context.dataStore.data.first()[KEY_ENCRYPTED_API_KEY]
            ?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val rawBytes = Base64.getDecoder().decode(encrypted)
            val decrypted = aead.decrypt(rawBytes, null)
            String(decrypted, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    suspend fun setCustomBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trim() }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }

    suspend fun setAutoFetchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_FETCH_ENABLED] = enabled }
    }

    suspend fun setAiTransmissionConsent(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AI_CONSENT] = enabled }
    }

    suspend fun setProEntitlementFromPlay(owned: Boolean) {
        context.dataStore.edit { it[KEY_PRO_PLAY] = owned }
    }

    /**
     * 舊版遷移：pro_unlocked=true 但新旗標皆 false（啟用碼用戶升級上來），
     * 補寫 pro_licensed=true，避免未來移除 legacy key 時掉授權。
     * App 啟動時呼叫一次即可。
     */
    suspend fun migrateLegacyProIfNeeded() {
        context.dataStore.edit { prefs ->
            val legacy = prefs[KEY_PRO_UNLOCKED] ?: false
            val play = prefs[KEY_PRO_PLAY] ?: false
            val licensed = prefs[KEY_PRO_LICENSED] ?: false
            if (legacy && !play && !licensed) {
                prefs[KEY_PRO_LICENSED] = true
            }
        }
    }

    suspend fun setThemeMode(mode: String) {
        val safe = when (mode) {
            "light", "dark" -> mode
            else -> "system"
        }
        context.dataStore.edit { it[KEY_THEME_MODE] = safe }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = scale.coerceIn(0.85f, 1.3f) }
    }

    suspend fun setUnreadNudgeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_UNREAD_NUDGE_ENABLED] = enabled }
    }

    suspend fun setUnreadNudgeDelayMin(min: Int) {
        context.dataStore.edit { it[KEY_UNREAD_NUDGE_DELAY_MIN] = min.coerceIn(1, 120) }
    }

    suspend fun setReviewDigestEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REVIEW_DIGEST_ENABLED] = enabled }
    }

    suspend fun setReviewDigestHour(hour: Int) {
        context.dataStore.edit { it[KEY_REVIEW_DIGEST_HOUR] = hour.coerceIn(0, 23) }
    }

    fun verifyPasscode(code: String, email: String = ""): Boolean {
        // 新制:委派給 LicenseVerifier (HMAC 離線驗證)。
        // 舊的萬用明文密碼與 salt 雜湊已全部移除,此處不再出現任何密碼字串。
        return LicenseVerifier.verify(email, code)
    }

    suspend fun unlockProWithPasscode(code: String, email: String = ""): Boolean {
        if (verifyPasscode(code, email)) {
            val mail = LicenseVerifier.canonicalizeEmail(email)
            val normalized = LicenseVerifier.normalize(code)
            context.dataStore.edit { prefs ->
                prefs[KEY_PRO_LICENSED] = true
                prefs[KEY_PRO_UNLOCKED] = true // legacy 相容：舊版讀此 key 的裝置也認得
                if (mail != null) prefs[KEY_PRO_EMAIL] = mail
                if (normalized.isNotBlank()) prefs[KEY_PRO_CODE] = normalized
            }
            return true
        }
        return false
    }
}
