package com.filodot.noscroll.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.filodot.noscroll.core.model.LimitPreset
import com.filodot.noscroll.core.model.UserSettings
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun emptyStorePublishesFrozenDefaults() = runBlocking {
        val repository = createRepository()

        val actual = awaitSettings(repository, UserSettings())

        assertEquals(UserSettings(), actual)
    }

    @Test
    fun allSettingsRoundTripAndAreVisibleToRecreatedAdapter() = runBlocking {
        val dataStore = createDataStore()
        val repository = DataStoreSettingsRepository(dataStore, scope)
        val expected = UserSettings(
            onboardingCompleted = true,
            shortsGateEnabled = false,
            shortsIntervalMinutes = 17,
            dailyLimitEnabled = true,
            dailyLimitMinutes = 135,
            preset = LimitPreset.CUSTOM,
            emergencyActive = true,
            accessibilityDisclosureAcceptedAt = Instant.parse("2026-07-14T01:02:03Z"),
            usageDisclosureSeenAt = Instant.parse("2026-07-14T04:05:06Z"),
            detectorRulesVersion = 9,
            settingsSchemaVersion = 3,
        )

        repository.save(expected)
        assertEquals(expected, awaitSettings(repository, expected))

        val recreatedRepository = DataStoreSettingsRepository(dataStore, scope)
        assertEquals(expected, awaitSettings(recreatedRepository, expected))
    }

    @Test
    fun savingNullDisclosureTimestampsRemovesOldValues() = runBlocking {
        val repository = DataStoreSettingsRepository(InMemoryPreferencesDataStore(), scope)
        val withTimestamps = UserSettings(
            accessibilityDisclosureAcceptedAt = Instant.parse("2026-01-01T00:00:00Z"),
            usageDisclosureSeenAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        repository.save(withTimestamps)
        awaitSettings(repository, withTimestamps)

        val cleared = withTimestamps.copy(
            accessibilityDisclosureAcceptedAt = null,
            usageDisclosureSeenAt = null,
        )
        repository.save(cleared)

        assertEquals(cleared, awaitSettings(repository, cleared))
    }

    @Test
    fun saveIsVisibleToRuntimeBeforeSlowStorageCommitFinishes() = runBlocking {
        val storage = DelayedPreferencesDataStore()
        val repository = DataStoreSettingsRepository(storage, scope)
        awaitSettings(repository, UserSettings())
        val expected = UserSettings(
            onboardingCompleted = true,
            instagramIntervalMinutes = 17,
        )

        val saveJob = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.save(expected)
        }
        storage.writeStarted.await()

        assertEquals(expected, repository.settings.value)
        storage.allowWrite.complete(Unit)
        saveJob.join()
        assertEquals(expected, repository.settings.value)
    }

    private fun createRepository(): DataStoreSettingsRepository =
        DataStoreSettingsRepository(createDataStore(), scope)

    private fun createDataStore() = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = {
            File(temporaryFolder.root, "settings-${System.nanoTime()}.preferences_pb")
        },
    )

    private suspend fun awaitSettings(
        repository: DataStoreSettingsRepository,
        expected: UserSettings,
    ): UserSettings = withTimeout(5_000) {
        repository.settings.filter { it == expected }.first()
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private class DelayedPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            writeStarted.complete(Unit)
            allowWrite.await()
            state.value = updated
            return updated
        }
    }
}
