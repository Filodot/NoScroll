package com.filodot.noscroll.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.filodot.noscroll.core.learning.ai.AiCredentialRepository
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.ai.AiProviderSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SecureAiCredentialRepository(
    context: Context,
    private val cipher: SecretCipher = AndroidKeystoreSecretCipher(),
) : AiCredentialRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutableSettings = MutableStateFlow(readSettings())
    override val settings: StateFlow<List<AiProviderSettings>> = mutableSettings.asStateFlow()

    override suspend fun saveApiKey(providerId: AiProviderId, apiKey: String) {
        val normalized = apiKey.trim()
        require(normalized.length in 8..512) { "Некорректная длина API-ключа" }
        withContext(Dispatchers.IO) {
            preferences.edit { putString(keyName(providerId), cipher.encrypt(normalized)) }
        }
        publish()
    }

    override suspend fun clearApiKey(providerId: AiProviderId) {
        withContext(Dispatchers.IO) {
            preferences.edit { remove(keyName(providerId)) }
        }
        publish()
    }

    override suspend fun setEnabled(providerId: AiProviderId, enabled: Boolean) {
        preferences.edit { putBoolean(enabledName(providerId), enabled) }
        publish()
    }

    override suspend fun setModel(providerId: AiProviderId, modelId: String) {
        val normalized = modelId.trim()
        require(
            normalized.length in 3..120 &&
                normalized.matches(Regex("[A-Za-z0-9._:/-]+")),
        ) {
            "Некорректный идентификатор модели"
        }
        preferences.edit { putString(modelName(providerId), normalized) }
        publish()
    }

    override suspend fun getApiKey(providerId: AiProviderId): String? =
        withContext(Dispatchers.IO) {
            val encrypted = preferences.safeString(keyName(providerId)) ?: return@withContext null
            try {
                cipher.decrypt(encrypted)
            } catch (_: Exception) {
                preferences.edit { remove(keyName(providerId)) }
                publish()
                null
            }
        }

    private fun publish() {
        mutableSettings.value = readSettings()
    }

    private fun readSettings(): List<AiProviderSettings> = DEFAULTS.mapIndexed { index, default ->
        val modelId = preferences.safeString(modelName(default.id))
            ?.trim()
            ?.takeIf(::isValidModelId)
            ?: default.modelId
        AiProviderSettings(
            id = default.id,
            enabled = preferences.safeBoolean(enabledName(default.id), true),
            priority = index,
            modelId = modelId,
            hasApiKey = !preferences.safeString(keyName(default.id)).isNullOrBlank(),
        )
    }

    private data class DefaultProvider(
        val id: AiProviderId,
        val modelId: String,
    )

    companion object {
        internal const val PREFERENCES_NAME = "ai_credentials"
        private val DEFAULTS = listOf(
            DefaultProvider(AiProviderId.GEMINI, "gemini-3.6-flash"),
            DefaultProvider(AiProviderId.GROQ, "openai/gpt-oss-20b"),
            DefaultProvider(AiProviderId.OPENROUTER, "openrouter/free"),
        )

        private fun keyName(id: AiProviderId) = "key_${id.name.lowercase()}"
        private fun enabledName(id: AiProviderId) = "enabled_${id.name.lowercase()}"
        private fun modelName(id: AiProviderId) = "model_${id.name.lowercase()}"

        private fun isValidModelId(modelId: String): Boolean =
            modelId.length in 3..120 && modelId.matches(Regex("[A-Za-z0-9._:/-]+"))
    }
}

private fun android.content.SharedPreferences.safeString(key: String): String? =
    runCatching { getString(key, null) }.getOrNull()

private fun android.content.SharedPreferences.safeBoolean(key: String, default: Boolean): Boolean =
    runCatching { getBoolean(key, default) }.getOrDefault(default)

interface SecretCipher {
    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): String
}

class AndroidKeystoreSecretCipher : SecretCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String {
        val payload = Base64.decode(cipherText, Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES)
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "noscroll_ai_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
    }
}
