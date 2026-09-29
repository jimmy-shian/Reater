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
    // 外觀
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // system / light / dark
    private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
    // 通知：儲存後未讀提醒 / 每日回顧
    private val KEY_UNREAD_NUDGE_ENABLED = booleanPreferencesKey("unread_nudge_enabled")
    private val KEY_UNREAD_NUDGE_DELAY_MIN = intPreferencesKey("unread_nudge_delay_min")
    private val KEY_REVIEW_DIGEST_ENABLED = booleanPreferencesKey("review_digest_enabled")
    private val KEY_REVIEW_DIGEST_HOUR = intPreferencesKey("review_digest_hour")

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
        prefs[KEY_PRO_UNLOCKED] ?: false
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
        context.dataStore.edit { it[KEY_PRO_UNLOCKED] = owned }
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
            setProEntitlementFromPlay(true)
            return true
        }
        return false
    }
}
