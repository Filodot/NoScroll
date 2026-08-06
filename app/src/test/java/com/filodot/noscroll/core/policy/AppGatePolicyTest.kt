package com.filodot.noscroll.core.policy

import com.filodot.noscroll.core.model.TaskTrigger
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AppGatePolicyTest {
    private val policy = AppGatePolicy()
    private val now = Instant.parse("2026-08-06T10:00:00Z")

    @Test
    fun `first entry requires a task and solved cooldown allows access`() {
        val input = enabledInput()

        assertEquals(
            AppGateDecision.RequireTask(TaskTrigger.ENTRY),
            policy.decide(input, now),
        )
        assertEquals(
            AppGateDecision.Allow,
            policy.decide(input.copy(cooldownUntil = now.plusSeconds(600)), now),
        )
    }

    @Test
    fun `reaching interval overrides an unexpired entry cooldown`() {
        val decision = policy.decide(
            enabledInput().copy(
                usedSeconds = 10 * 60,
                cooldownUntil = now.plusSeconds(600),
            ),
            now,
        )

        assertEquals(AppGateDecision.RequireTask(TaskTrigger.INTERVAL), decision)
    }

    @Test
    fun `pending task survives counters and a bypass always wins`() {
        val pending = enabledInput().copy(
            cooldownUntil = now.plusSeconds(600),
            pendingTrigger = TaskTrigger.ENTRY,
        )

        assertEquals(
            AppGateDecision.RequireTask(TaskTrigger.ENTRY),
            policy.decide(pending, now),
        )
        assertEquals(AppGateDecision.Allow, policy.decide(pending.copy(bypassActive = true), now))
    }

    @Test
    fun `disabled invalid or inaccessible gates fail open`() {
        assertEquals(AppGateDecision.Allow, policy.decide(enabledInput().copy(enabled = false), now))
        assertEquals(
            AppGateDecision.Allow,
            policy.decide(enabledInput().copy(accessibilityGranted = false), now),
        )
        assertEquals(
            AppGateDecision.Allow,
            policy.decide(enabledInput().copy(intervalMinutes = 0), now),
        )
    }

    private fun enabledInput() = AppGateInput(
        enabled = true,
        accessibilityGranted = true,
        bypassActive = false,
        intervalMinutes = 10,
        usedSeconds = 0,
        cooldownUntil = null,
    )
}
