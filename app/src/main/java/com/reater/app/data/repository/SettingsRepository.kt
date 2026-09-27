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
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "reater_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_ENCRYPTED_API_KEY = stringPreferencesKey("openai_api_key_enc")
    private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_ai_model")
    private val KEY_AUTO_FETCH_ENABLED = booleanPreferencesKey("auto_fetch_enabled")
    private val KEY_PRO_UNLOCKED = booleanPreferencesKey("pro_unlocked")

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

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }

    suspend fun setAutoFetchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_FETCH_ENABLED] = enabled }
    }

    suspend fun setProUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[KEY_PRO_UNLOCKED] = unlocked }
    }
}
