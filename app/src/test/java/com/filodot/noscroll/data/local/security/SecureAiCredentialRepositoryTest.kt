package com.filodot.noscroll.data.local.security

import com.filodot.noscroll.core.learning.ai.AiProviderId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureAiCredentialRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun clearPreferences() {
        context.getSharedPreferences(
            SecureAiCredentialRepository.PREFERENCES_NAME,
            0,
        ).edit().clear().commit()
    }

    @Test
    fun `stores only encrypted key and exposes redacted state`() = runBlocking {
        val repository = SecureAiCredentialRepository(context, ReversingCipher)

        repository.saveApiKey(AiProviderId.GEMINI, "secret-key-123")

        assertEquals("secret-key-123", repository.getApiKey(AiProviderId.GEMINI))
        assertTrue(repository.settings.value.first { it.id == AiProviderId.GEMINI }.hasApiKey)
        val storedValues = context.getSharedPreferences(
            SecureAiCredentialRepository.PREFERENCES_NAME,
            0,
        ).all.values.map(Any?::toString)
        assertFalse(storedValues.any { it.contains("secret-key-123") })
    }

    @Test
    fun `clearing key preserves model configuration`() = runBlocking {
        val repository = SecureAiCredentialRepository(context, ReversingCipher)
        repository.setModel(AiProviderId.GROQ, "custom/model")
        repository.saveApiKey(AiProviderId.GROQ, "another-secret")

        repository.clearApiKey(AiProviderId.GROQ)

        val settings = repository.settings.value.first { it.id == AiProviderId.GROQ }
        assertEquals("custom/model", settings.modelId)
        assertFalse(settings.hasApiKey)
        assertNull(repository.getApiKey(AiProviderId.GROQ))
    }

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText.reversed()

        override fun decrypt(cipherText: String): String = cipherText.reversed()
    }
}
