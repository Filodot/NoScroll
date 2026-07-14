package com.filodot.noscroll.core.emergency

import com.filodot.noscroll.core.model.EmergencyActivationSource
import com.filodot.noscroll.core.model.EmergencyEvent
import com.filodot.noscroll.core.model.UserSettings
import com.filodot.noscroll.core.testing.FakeWallClock
import com.filodot.noscroll.core.testing.InMemoryEmergencyRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyStopDomainTest {
    private val instant = Instant.parse("2026-07-14T12:00:00Z")

    @Test
    fun `reason is trimmed and accepts exact five and three hundred boundaries`() {
        val domain = domain().domain

        val five = domain.validateReason("  12345  ")
        val threeHundred = domain.validateReason("x".repeat(300))

        assertTrue(five.isValid)
        assertEquals("12345", five.normalizedReason)
        assertTrue(threeHundred.isValid)
    }

    @Test
    fun `blank four-character and overlong reasons are rejected`() {
        val domain = domain().domain

        val blank = domain.validateReason("   ")
        val four = domain.validateReason("1234")
        val overlong = domain.validateReason("x".repeat(301))

        assertTrue(blank.error is EmergencyReasonError.TooShort)
        assertTrue(four.error is EmergencyReasonError.TooShort)
        assertTrue(overlong.error is EmergencyReasonError.TooLong)
    }

    @Test
    fun `activation is blocked when both limits are already disabled`() = runTest {
        val fixture = domain()

        val result = fixture.domain.activate(
            reason = "Рабочая задача",
            source = EmergencyActivationSource.DASHBOARD,
            settings = UserSettings(shortsGateEnabled = false, dailyLimitEnabled = false),
        )

        assertTrue(result is EmergencyActivationResult.LimitsAlreadyDisabled)
        assertFalse(fixture.repository.state.value.isActive)
        assertTrue(fixture.repository.history.first().isEmpty())
    }

    @Test
    fun `invalid activation and cancel leave enforcement state unchanged`() = runTest {
        val fixture = domain()

        val result = fixture.domain.activate(
            reason = " no ",
            source = EmergencyActivationSource.TASK_GATE,
            settings = UserSettings(),
        )
        val cancelled = fixture.domain.cancelActivation()

        assertTrue(result is EmergencyActivationResult.InvalidReason)
        assertFalse(cancelled.isActive)
        assertTrue(fixture.repository.history.first().isEmpty())
    }

    @Test
    fun `activation persists normalized reason source and active state`() = runTest {
        EmergencyActivationSource.entries.forEach { source ->
            val fixture = domain()

            val result = fixture.domain.activate(
                reason = "  Учебная трансляция  ",
                source = source,
                settings = UserSettings(),
            ) as EmergencyActivationResult.Activated

            assertEquals("emergency-1", result.event.id)
            assertEquals("Учебная трансляция", result.event.reason)
            assertEquals(source, result.event.activationSource)
            assertEquals(instant, result.event.activatedAt)
            assertTrue(fixture.repository.state.value.isActive)
            assertEquals(listOf(result.event), fixture.repository.history.first())
        }
    }

    @Test
    fun `second activation returns existing event without replacing its reason`() = runTest {
        val fixture = domain()
        val first = fixture.domain.activate(
            reason = "Первая причина",
            source = EmergencyActivationSource.DASHBOARD,
            settings = UserSettings(),
        ) as EmergencyActivationResult.Activated

        val second = fixture.domain.activate(
            reason = "Другая причина",
            source = EmergencyActivationSource.DAILY_LIMIT,
            settings = UserSettings(),
        ) as EmergencyActivationResult.AlreadyActive

        assertEquals(first.event, second.event)
        assertEquals(listOf(first.event), fixture.repository.history.first())
    }

    @Test
    fun `YouTube aggregate is persisted incrementally and ignores non-positive deltas`() = runTest {
        val fixture = domain()
        fixture.activate()

        fixture.domain.addYoutubeSeconds(30)
        fixture.domain.addYoutubeSeconds(-20)
        val result = fixture.domain.addYoutubeSeconds(15) as EmergencyUsageUpdateResult.Updated

        assertEquals(45L, result.event.youtubeSecondsDuring)
        assertEquals(45L, fixture.repository.state.value.activeEvent?.youtubeSecondsDuring)
        assertEquals(45L, fixture.repository.history.first().single().youtubeSecondsDuring)
    }

    @Test
    fun `active event restores in a new domain instance after process or reboot shape`() = runTest {
        val active = event(reason = "Срочная инструкция", youtubeSeconds = 40)
        val repository = InMemoryEmergencyRepository(listOf(active))

        val restored = EmergencyStopDomain(
            repository = repository,
            wallClock = FakeWallClock(instant.plusSeconds(120)),
            idGenerator = EmergencyIdGenerator { "unused" },
        )

        assertTrue(restored.state().isActive)
        assertEquals(active, restored.state().activeEvent)
        assertEquals(120, restored.metrics(active).durationSeconds)
    }

    @Test
    fun `deactivation persists completion metrics and keeps history`() = runTest {
        val fixture = domain()
        fixture.activate()
        fixture.domain.addYoutubeSeconds(75)
        fixture.clock.advanceBy(Duration.ofMinutes(5))

        val result = fixture.domain.deactivate() as EmergencyDeactivationResult.Deactivated

        assertFalse(fixture.repository.state.value.isActive)
        assertEquals(instant.plusSeconds(300), result.event.deactivatedAt)
        assertEquals(300, result.metrics.durationSeconds)
        assertEquals(75, result.metrics.youtubeSecondsDuring)
        assertEquals(result.event, fixture.repository.history.first().single())
        assertEquals(EmergencyCommand.REEVALUATE_POLICY, result.command)
    }

    @Test
    fun `deactivation with a rolled back wall clock never creates negative duration`() = runTest {
        val active = event(reason = "Срочная инструкция")
        val repository = InMemoryEmergencyRepository(listOf(active))
        val clock = FakeWallClock(instant.minusSeconds(60))
        val domain = EmergencyStopDomain(repository, clock)

        val result = domain.deactivate() as EmergencyDeactivationResult.Deactivated

        assertEquals(instant, result.event.deactivatedAt)
        assertEquals(0, result.metrics.durationSeconds)
    }

    @Test
    fun `deactivation without active event is an explicit no-op`() = runTest {
        val fixture = domain()

        assertTrue(fixture.domain.deactivate() is EmergencyDeactivationResult.NoActiveEmergency)
        assertTrue(
            fixture.domain.addYoutubeSeconds(10) is
                EmergencyUsageUpdateResult.NoActiveEmergency,
        )
    }

    @Test
    fun `history deletion preserves the active event until manual deactivation`() = runTest {
        val completed = event(
            id = "old",
            reason = "Старая причина",
            deactivatedAt = instant.minusSeconds(60),
        )
        val active = event(id = "active", reason = "Текущая причина")
        val repository = InMemoryEmergencyRepository(listOf(completed, active))
        val domain = EmergencyStopDomain(repository, FakeWallClock(instant))

        domain.deleteHistory()

        assertTrue(repository.state.value.isActive)
        assertEquals(listOf(active), repository.history.first())
    }

    private fun domain(): Fixture {
        val repository = InMemoryEmergencyRepository()
        val clock = FakeWallClock(instant)
        return Fixture(
            repository = repository,
            clock = clock,
            domain = EmergencyStopDomain(
                repository = repository,
                wallClock = clock,
                idGenerator = EmergencyIdGenerator { "emergency-1" },
            ),
        )
    }

    private fun event(
        id: String = "emergency-1",
        reason: String,
        youtubeSeconds: Long = 0,
        deactivatedAt: Instant? = null,
    ) = EmergencyEvent(
        id = id,
        reason = reason,
        activatedAt = instant,
        deactivatedAt = deactivatedAt,
        activationSource = EmergencyActivationSource.DASHBOARD,
        youtubeSecondsDuring = youtubeSeconds,
    )

    private data class Fixture(
        val repository: InMemoryEmergencyRepository,
        val clock: FakeWallClock,
        val domain: EmergencyStopDomain,
    ) {
        suspend fun activate() {
            domain.activate(
                reason = "Учебная трансляция",
                source = EmergencyActivationSource.DASHBOARD,
                settings = UserSettings(),
            )
        }
    }
}
