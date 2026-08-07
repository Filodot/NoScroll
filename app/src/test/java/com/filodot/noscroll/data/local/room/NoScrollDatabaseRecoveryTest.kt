package com.filodot.noscroll.data.local.room

import android.database.sqlite.SQLiteDatabaseCorruptException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoScrollDatabaseRecoveryTest {
    @Test
    fun corruptionClassifierFindsNestedSqliteFailuresOnly() {
        val nested = IllegalStateException(
            "Could not open storage",
            SQLiteDatabaseCorruptException("database disk image is malformed"),
        )

        assertTrue(DatabaseFailureClassifier.isCorruption(nested))
        assertTrue(
            DatabaseFailureClassifier.isCorruption(
                IllegalStateException("File is not a database"),
            ),
        )
        assertFalse(
            DatabaseFailureClassifier.isCorruption(
                IllegalStateException("Migration didn't properly handle a table"),
            ),
        )
    }

    @Test
    fun corruptDatabaseFileIsRecreatedAndCanPersistData() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "recovery-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.writeText("this is not a sqlite database")

        val database = NoScrollDatabase.build(context, name)
        try {
            val entity = DailyUsageEntity(
                localDate = LocalDate.of(2026, 8, 7).toString(),
                youtubeSeconds = 10,
                shortsSeconds = 5,
                emergencyYoutubeSeconds = 0,
                gatesShown = 1,
                tasksSolved = 0,
                taskExits = 0,
                lastUpdatedElapsedMillis = null,
                updatedAtEpochMillis = Instant.parse("2026-08-07T08:00:00Z").toEpochMilli(),
            )

            database.dailyUsageDao().upsert(entity)

            assertEquals(entity, database.dailyUsageDao().get(entity.localDate))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
