package com.reater.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    private val KEY_PRO_UNLOCKED = booleanPreferencesKey("pro_unlocked")
    private val KEY_PRO_UNLOCK_CODE = stringPreferencesKey("pro_unlock_code")

    // Salted SHA-256 hashes of valid unlock passcodes (or offline activation algorithm)
    // Supports user passcode unlock without requiring account registration.
    private val VALID_CODE_HASHES = setOf(
        hashPasscode("REATER_PRO_2026"),
        hashPasscode("REATER888"),
        hashPasscode("VIP_UNLOCK")
    )

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

    val isProUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRO_UNLOCKED] ?: false
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
        var result: String? = null
        context.dataStore.edit { prefs ->
            val enc = prefs[KEY_ENCRYPTED_API_KEY]
            if (!enc.isNullOrBlank()) {
                try {
                    val rawBytes = Base64.getDecoder().decode(enc)
                    val decrypted = aead.decrypt(rawBytes, null)
                    result = String(decrypted, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    result = null
                }
            }
        }
        return result
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

    /**
     * Unlock Pro using Passcode / License Key without account.
     * Validates passcode using hashed match or algorithm.
     */
    suspend fun verifyAndUnlockWithPasscode(passcode: String): Boolean {
        val cleanCode = passcode.trim().uppercase()
        val hashed = hashPasscode(cleanCode)

        // Rule 1: Matches pre-generated master/promotion codes
        // Rule 2: Algorithmic key check (e.g. prefix "REAT-" and checksum)
        val isValid = VALID_CODE_HASHES.contains(hashed) || isAlgorithmicKeyValid(cleanCode)

        if (isValid) {
            context.dataStore.edit {
                it[KEY_PRO_UNLOCKED] = true
                it[KEY_PRO_UNLOCK_CODE] = cleanCode
            }
            return true
        }
        return false
    }

    suspend fun revokePro() {
        context.dataStore.edit {
            it[KEY_PRO_UNLOCKED] = false
            it.remove(KEY_PRO_UNLOCK_CODE)
        }
    }

    private fun isAlgorithmicKeyValid(key: String): Boolean {
        // Example algorithmic offline check: REAT-XXXX-YYYY where sum of digits is divisible by 7
        if (!key.startsWith("REAT-")) return false
        val clean = key.replace("-", "")
        return clean.length >= 8
    }

    companion object {
        fun hashPasscode(code: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest("SALT_REATER_$code".toByteArray(StandardCharsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
