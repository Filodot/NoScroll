package com.filodot.noscroll.data.local.room

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoScrollMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationOneToTwoPreservesUsageAndInitializesEmergencySeconds() = runBlocking {
        createVersionOneDatabase()

        val database = Room.databaseBuilder(context, NoScrollDatabase::class.java, databaseName)
            .addMigrations(NoScrollDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val migrated = requireNotNull(database.dailyUsageDao().get("2026-07-14"))

        assertEquals(LocalDate.of(2026, 7, 14), migrated.toModel().localDate)
        assertEquals(1_234L, migrated.youtubeSeconds)
        assertEquals(456L, migrated.shortsSeconds)
        assertEquals(0L, migrated.emergencyYoutubeSeconds)
        database.close()
    }

    private fun createVersionOneDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        VERSION_ONE_CREATE_STATEMENTS.forEach(db::execSQL)
                        db.execSQL(
                            "INSERT INTO daily_usage " +
                                "(local_date, youtube_seconds, shorts_seconds, gates_shown, " +
                                "tasks_solved, task_exits, last_updated_elapsed_millis, " +
                                "updated_at_epoch_millis) " +
                                "VALUES ('2026-07-14', 1234, 456, 2, 1, 0, 9876, 1784005200000)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }
    }

    companion object {
        private val VERSION_ONE_CREATE_STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS daily_usage (" +
                "local_date TEXT NOT NULL, youtube_seconds INTEGER NOT NULL, " +
                "shorts_seconds INTEGER NOT NULL, gates_shown INTEGER NOT NULL, " +
                "tasks_solved INTEGER NOT NULL, task_exits INTEGER NOT NULL, " +
                "last_updated_elapsed_millis INTEGER, updated_at_epoch_millis INTEGER NOT NULL, " +
                "PRIMARY KEY(local_date))",
            "CREATE TABLE IF NOT EXISTS gate_cycles (" +
                "id TEXT NOT NULL, local_date TEXT NOT NULL, used_seconds INTEGER NOT NULL, " +
                "pending_task_id TEXT, updated_at_epoch_millis INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS pending_tasks (" +
                "id TEXT NOT NULL, operation TEXT NOT NULL, left_operand INTEGER NOT NULL, " +
                "right_operand INTEGER NOT NULL, expected_answer INTEGER NOT NULL, " +
                "created_at_epoch_millis INTEGER NOT NULL, wrong_attempts INTEGER NOT NULL, " +
                "solved INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE IF NOT EXISTS emergency_events (" +
                "id TEXT NOT NULL, reason TEXT NOT NULL, activated_at_epoch_millis INTEGER NOT NULL, " +
                "deactivated_at_epoch_millis INTEGER, activation_source TEXT NOT NULL, " +
                "youtube_seconds_during INTEGER NOT NULL, PRIMARY KEY(id))",
        )
    }
}
