package com.filodot.noscroll.data.local.room

import androidx.room.Room
import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.DailyUsage
import com.filodot.noscroll.core.model.CustomTaskPreset
import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.GateCycle
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.data.local.repository.RoomEmergencyRepository
import com.filodot.noscroll.data.local.repository.RoomTaskGrantTransaction
import com.filodot.noscroll.data.local.repository.RoomTaskRepository
import com.filodot.noscroll.data.local.repository.RoomTaskPresetRepository
import com.filodot.noscroll.data.local.repository.RoomUsageRepository
import com.filodot.noscroll.data.local.retention.LocalDataRetention
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositoriesTest {
    private lateinit var database: NoScrollDatabase
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NoScrollDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun repositoryCrudRoundTripsAndRestoresPersistedState() = runBlocking {
        val date = LocalDate.of(2026, 7, 14)
        val now = Instant.parse("2026-07-14T05:00:00Z")
        val fallbackDaily = dailyUsage(date, now)
        val fallbackCycle = gateCycle(date, now)
        val usageRepository = RoomUsageRepository(
            database.dailyUsageDao(),
            database.gateCycleDao(),
            scope,
            fallbackDaily,
            fallbackCycle,
        )
        val taskRepository = RoomTaskRepository(database.pendingTaskDao(), scope)
        val emergencyRepository = RoomEmergencyRepository(database.emergencyEventDao(), scope)
        val storedDaily = fallbackDaily.copy(
            youtubeSeconds = 1_234,
            shortsSeconds = 456,
            emergencyYoutubeSeconds = 78,
            gatesShown = 4,
            tasksSolved = 3,
            taskExits = 1,
            lastUpdatedElapsedMillis = 9_876,
        )
        val task = pendingTask("task-1", now).copy(difficulty = TaskDifficulty.HARD)
        val storedCycle = fallbackCycle.copy(
            usedSeconds = 299,
            pendingTaskId = task.id,
            intervalBlockStreak = 2,
            lastIntervalBlockAt = now.minusSeconds(60),
            entryCooldownUntil = now.plusSeconds(300),
        )
        val activeEmergency = emergency("emergency-1", now, deactivatedAt = null)

        usageRepository.saveDailyUsage(storedDaily)
        taskRepository.save(task)
        usageRepository.saveGateCycle(storedCycle)
        emergencyRepository.activate(activeEmergency)

        assertEquals(storedDaily, awaitValue(usageRepository.dailyUsage, storedDaily))
        assertEquals(storedCycle, awaitValue(usageRepository.gateCycle, storedCycle))
        assertEquals(task, awaitValue(taskRepository.pendingTask, task))
        assertEquals(
            activeEmergency,
            withTimeout(5_000) {
                emergencyRepository.state.filter { it.activeEvent == activeEmergency }.first()
            }.activeEvent,
        )

        val recreatedTaskRepository = RoomTaskRepository(database.pendingTaskDao(), scope)
        val recreatedEmergencyRepository = RoomEmergencyRepository(database.emergencyEventDao(), scope)
        val recreatedUsageRepository = RoomUsageRepository(
            database.dailyUsageDao(),
            database.gateCycleDao(),
            scope,
            fallbackDaily,
            fallbackCycle,
        )
        assertEquals(storedDaily, awaitValue(recreatedUsageRepository.dailyUsage, storedDaily))
        assertEquals(storedCycle, awaitValue(recreatedUsageRepository.gateCycle, storedCycle))
        assertEquals(task, awaitValue(recreatedTaskRepository.pendingTask, task))
        assertEquals(
            activeEmergency,
            withTimeout(5_000) {
                recreatedEmergencyRepository.state
                    .filter { it.activeEvent == activeEmergency }
                    .first()
            }.activeEvent,
        )

        taskRepository.clear("different-id")
        assertEquals(task, database.pendingTaskDao().get(task.id)?.toModel())
        taskRepository.clear(task.id)
        assertNull(database.pendingTaskDao().get(task.id))

        emergencyRepository.deleteHistory()
        assertEquals(activeEmergency, database.emergencyEventDao().get(activeEmergency.id)?.toModel())

        val closedEmergency = activeEmergency.copy(
            deactivatedAt = now.plusSeconds(60),
            youtubeSecondsDuring = 42,
        )
        emergencyRepository.deactivate(closedEmergency)
        assertEquals(
            closedEmergency,
            withTimeout(5_000) {
                emergencyRepository.history.filter { it.singleOrNull() == closedEmergency }.first()
            }.single(),
        )
        emergencyRepository.deleteHistory()
        assertTrue(withTimeout(5_000) { emergencyRepository.history.filter { it.isEmpty() }.first() }.isEmpty())
    }

    @Test
    fun taskGrantIsAtomicAndIdempotent() = runBlocking {
        val date = LocalDate.of(2026, 7, 14)
        val createdAt = Instant.parse("2026-07-14T05:00:00Z")
        val grantedAt = createdAt.plusSeconds(30)
        val task = pendingTask("task-grant", createdAt)
        database.dailyUsageDao().upsert(dailyUsage(date, createdAt).toEntity())
        database.pendingTaskDao().upsert(task.toEntity())
        database.gateCycleDao().upsert(
            gateCycle(date, createdAt).copy(usedSeconds = 300, pendingTaskId = task.id).toEntity(),
        )
        val transaction = RoomTaskGrantTransaction(database.taskGrantDao())
        val cooldownUntil = grantedAt.plusSeconds(300)

        assertTrue(transaction.grant(task.id, date, grantedAt, cooldownUntil))
        assertFalse(
            transaction.grant(
                task.id,
                date,
                grantedAt.plusSeconds(1),
                cooldownUntil.plusSeconds(1),
            ),
        )

        val daily = requireNotNull(database.dailyUsageDao().get(date.toString())).toModel()
        val cycle = requireNotNull(database.gateCycleDao().get(GateCycle.CURRENT_GATE_CYCLE_ID))
            .toModel()
        assertEquals(1, daily.tasksSolved)
        assertEquals(grantedAt, daily.updatedAt)
        assertEquals(0, cycle.usedSeconds)
        assertEquals(cooldownUntil, cycle.entryCooldownUntil)
        assertNull(cycle.pendingTaskId)
        assertEquals(grantedAt, cycle.updatedAt)
        assertNull(database.pendingTaskDao().get(task.id))
    }

    @Test
    fun rapidUsageUpdatesAreImmediatelyVisibleAndNeverOverwriteEachOther() = runBlocking {
        val date = LocalDate.of(2026, 7, 22)
        val now = Instant.parse("2026-07-22T08:00:00Z")
        val repository = RoomUsageRepository(
            database.dailyUsageDao(),
            database.gateCycleDao(),
            scope,
            dailyUsage(date, now),
            gateCycle(date, now),
        )
        withTimeout(5_000) {
            repository.dailyInitialized.filter { it }.first()
            repository.gateInitialized.filter { it }.first()
        }

        repeat(100) { index ->
            val seconds = (index + 1).toLong()
            repository.saveDailyUsage(
                repository.dailyUsage.value.copy(
                    youtubeSeconds = seconds,
                    updatedAt = now.plusSeconds(seconds),
                ),
            )
            repository.saveGateCycle(
                repository.gateCycle.value.copy(
                    instagramUsedSeconds = seconds,
                    updatedAt = now.plusSeconds(seconds),
                ),
            )
            assertEquals(seconds, repository.dailyUsage.value.youtubeSeconds)
            assertEquals(seconds, repository.gateCycle.value.instagramUsedSeconds)
        }

        assertEquals(
            100L,
            requireNotNull(database.dailyUsageDao().get(date.toString())).youtubeSeconds,
        )
        assertEquals(
            100L,
            requireNotNull(database.gateCycleDao().get(GateCycle.CURRENT_GATE_CYCLE_ID))
                .instagramUsedSeconds,
        )
    }

    @Test
    fun entryTaskGrantResetsIntervalAndPersistsCooldown() = runBlocking {
        val date = LocalDate.of(2026, 7, 14)
        val createdAt = Instant.parse("2026-07-14T05:00:00Z")
        val grantedAt = createdAt.plusSeconds(30)
        val cooldownUntil = grantedAt.plusSeconds(300)
        val task = pendingTask("entry-task", createdAt).copy(
            difficulty = TaskDifficulty.EASY,
            trigger = TaskTrigger.ENTRY,
        )
        database.dailyUsageDao().upsert(dailyUsage(date, createdAt).toEntity())
        database.pendingTaskDao().upsert(task.toEntity())
        database.gateCycleDao().upsert(
            gateCycle(date, createdAt).copy(
                usedSeconds = 123,
                pendingTaskId = task.id,
                intervalBlockStreak = 2,
                lastIntervalBlockAt = createdAt.minusSeconds(60),
            ).toEntity(),
        )

        assertTrue(
            RoomTaskGrantTransaction(database.taskGrantDao())
                .grant(
                    taskId = task.id,
                    localDate = date,
                    updatedAt = grantedAt,
                    entryCooldownUntil = cooldownUntil,
                ),
        )

        val cycle = requireNotNull(database.gateCycleDao().get(GateCycle.CURRENT_GATE_CYCLE_ID))
            .toModel()
        assertEquals(0L, cycle.usedSeconds)
        assertEquals(2, cycle.intervalBlockStreak)
        assertEquals(cooldownUntil, cycle.entryCooldownUntil)
        assertNull(cycle.pendingTaskId)
        assertNull(database.pendingTaskDao().get(task.id))
    }

    @Test
    fun instagramGrantResetsOnlyInstagramInterval() = runBlocking {
        val date = LocalDate.of(2026, 7, 14)
        val now = Instant.parse("2026-07-14T05:00:00Z")
        val cooldownUntil = now.plusSeconds(600)
        val task = pendingTask("instagram-task", now).copy(target = TaskTarget.INSTAGRAM)
        database.dailyUsageDao().upsert(dailyUsage(date, now).toEntity())
        database.pendingTaskDao().upsert(task.toEntity())
        database.gateCycleDao().upsert(
            gateCycle(date, now).copy(
                usedSeconds = 111,
                instagramUsedSeconds = 600,
                pendingTaskId = task.id,
            ).toEntity(),
        )

        assertTrue(
            RoomTaskGrantTransaction(database.taskGrantDao()).grant(
                task.id,
                date,
                now,
                cooldownUntil,
            ),
        )

        val cycle = requireNotNull(database.gateCycleDao().get(GateCycle.CURRENT_GATE_CYCLE_ID))
            .toModel()
        assertEquals(111L, cycle.usedSeconds)
        assertEquals(0L, cycle.instagramUsedSeconds)
        assertEquals(cooldownUntil, cycle.instagramEntryCooldownUntil)
    }

    @Test
    fun customTaskPresetsRoundTripAndDelete() = runBlocking {
        val repository = RoomTaskPresetRepository(database.customTaskPresetDao(), scope)
        val preset = CustomTaskPreset(
            id = "preset-1",
            title = "Вода",
            instruction = "Выпить стакан воды",
            createdAt = Instant.parse("2026-07-14T05:00:00Z"),
        )

        repository.save(preset)
        assertEquals(preset, withTimeout(5_000) { repository.presets.filter { it == listOf(preset) }.first() }.single())
        repository.delete(preset.id)
        assertTrue(withTimeout(5_000) { repository.presets.filter(List<CustomTaskPreset>::isEmpty).first() }.isEmpty())
    }

    @Test
    fun taskGrantDoesNotMutateAnythingWhenRequiredAggregateIsMissing() = runBlocking {
        val date = LocalDate.of(2026, 7, 14)
        val now = Instant.parse("2026-07-14T05:00:00Z")
        val task = pendingTask("task-no-daily", now)
        val cycle = gateCycle(date, now).copy(usedSeconds = 300, pendingTaskId = task.id)
        database.pendingTaskDao().upsert(task.toEntity())
        database.gateCycleDao().upsert(cycle.toEntity())

        val granted = RoomTaskGrantTransaction(database.taskGrantDao())
            .grant(
                task.id,
                date,
                now.plusSeconds(10),
                entryCooldownUntil = now.plusSeconds(310),
            )

        assertFalse(granted)
        assertEquals(task, database.pendingTaskDao().get(task.id)?.toModel())
        assertEquals(cycle, database.gateCycleDao().get(cycle.id)?.toModel())
    }

    @Test
    fun retentionDeletesOnlyExpiredHistoryAndPreservesActiveState() = runBlocking {
        val now = Instant.parse("2026-07-14T12:00:00Z")
        val zone = ZoneId.of("Europe/Moscow")
        val today = now.atZone(zone).toLocalDate()
        val expiredDate = today.minusDays(91)
        val boundaryDate = today.minusDays(90)
        database.dailyUsageDao().upsert(dailyUsage(expiredDate, now).toEntity())
        database.dailyUsageDao().upsert(dailyUsage(boundaryDate, now).toEntity())

        val expiredClosed = emergency(
            id = "closed-expired",
            activatedAt = now.minus(92, ChronoUnit.DAYS),
            deactivatedAt = now.minus(91, ChronoUnit.DAYS),
        )
        val boundaryClosed = emergency(
            id = "closed-boundary",
            activatedAt = now.minus(91, ChronoUnit.DAYS),
            deactivatedAt = now.minus(90, ChronoUnit.DAYS),
        )
        val oldActive = emergency(
            id = "active-old",
            activatedAt = now.minus(120, ChronoUnit.DAYS),
            deactivatedAt = null,
        )
        database.emergencyEventDao().upsert(expiredClosed.toEntity())
        database.emergencyEventDao().upsert(boundaryClosed.toEntity())
        database.emergencyEventDao().upsert(oldActive.toEntity())

        val oldSolved = pendingTask("solved-old", now.minus(100, ChronoUnit.DAYS)).copy(solved = true)
        val oldPending = pendingTask("pending-old", now.minus(100, ChronoUnit.DAYS))
        database.pendingTaskDao().upsert(oldSolved.toEntity())
        database.pendingTaskDao().upsert(oldPending.toEntity())

        val result = LocalDataRetention(database.retentionDao()).deleteExpired(now, zone)

        assertEquals(1, result.dailyRowsDeleted)
        assertEquals(1, result.emergencyRowsDeleted)
        assertEquals(1, result.solvedTaskRowsDeleted)
        assertNull(database.dailyUsageDao().get(expiredDate.toString()))
        assertEquals(boundaryDate, database.dailyUsageDao().get(boundaryDate.toString())?.toModel()?.localDate)
        assertNull(database.emergencyEventDao().get(expiredClosed.id))
        assertEquals(boundaryClosed, database.emergencyEventDao().get(boundaryClosed.id)?.toModel())
        assertEquals(oldActive, database.emergencyEventDao().get(oldActive.id)?.toModel())
        assertNull(database.pendingTaskDao().get(oldSolved.id))
        assertEquals(oldPending, database.pendingTaskDao().get(oldPending.id)?.toModel())
    }

    private suspend fun <T> awaitValue(
        stateFlow: kotlinx.coroutines.flow.StateFlow<T>,
        expected: T,
    ): T = withTimeout(5_000) { stateFlow.filter { it == expected }.first() }

    private fun dailyUsage(localDate: LocalDate, updatedAt: Instant): DailyUsage = DailyUsage(
        localDate = localDate,
        updatedAt = updatedAt,
    )

    private fun gateCycle(localDate: LocalDate, updatedAt: Instant): GateCycle = GateCycle(
        localDate = localDate,
        updatedAt = updatedAt,
    )

    private fun pendingTask(id: String, createdAt: Instant): PendingTask = PendingTask(
        id = id,
        operation = ArithmeticOperation.ADD,
        leftOperand = 21,
        rightOperand = 35,
        expectedAnswer = 56,
        createdAt = createdAt,
    )

    private fun emergency(
        id: String,
        activatedAt: Instant,
        deactivatedAt: Instant?,
    ): EmergencyEvent = EmergencyEvent(
        id = id,
        reason = "Нужное видео",
        activatedAt = activatedAt,
        deactivatedAt = deactivatedAt,
        activationSource = EmergencyActivationSource.DASHBOARD,
    )
}
