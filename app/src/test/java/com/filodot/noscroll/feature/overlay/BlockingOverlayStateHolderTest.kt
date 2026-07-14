package com.filodot.noscroll.feature.overlay

import com.filodot.noscroll.core.model.EmergencyActivationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingOverlayStateHolderTest {
    @Test
    fun `empty answer cannot start verification`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)

        holder.dispatch(BlockingOverlayAction.SubmitAnswer)

        assertTrue(effects.isEmpty())
        assertEquals(TaskAnswerStatus.READY, task(holder).answerStatus)
    }

    @Test
    fun `answer accepts digits only and is bounded`() {
        val holder = holder()

        holder.dispatch(BlockingOverlayAction.UpdateAnswer("a12-3456789012"))

        assertEquals("123456789", task(holder).answer)
    }

    @Test
    fun `submit emits verification without checking answer inside UI`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)
        holder.dispatch(BlockingOverlayAction.UpdateAnswer("43"))

        holder.dispatch(BlockingOverlayAction.SubmitAnswer)

        assertEquals(TaskAnswerStatus.CHECKING, task(holder).answerStatus)
        assertEquals(
            BlockingOverlayEffect.VerifyAnswer(taskId = "task-1", answer = "43"),
            effects.single(),
        )
    }

    @Test
    fun `incorrect result clears answer and increments attempt count`() {
        val holder = checkingHolder()

        holder.dispatch(BlockingOverlayAction.AnswerChecked(correct = false))

        assertEquals("", task(holder).answer)
        assertEquals(1, task(holder).wrongAttempts)
        assertEquals(TaskAnswerStatus.INCORRECT, task(holder).answerStatus)
    }

    @Test
    fun `another task is offered only after three errors`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)

        repeat(2) { failAnswer(holder) }
        holder.dispatch(BlockingOverlayAction.RequestAnotherTask)
        assertTrue(effects.filterIsInstance<BlockingOverlayEffect.RequestAnotherTask>().isEmpty())

        failAnswer(holder)
        holder.dispatch(BlockingOverlayAction.RequestAnotherTask)

        assertTrue(effects.last() is BlockingOverlayEffect.RequestAnotherTask)
    }

    @Test
    fun `correct result shows success and emits solved effect`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)
        holder.dispatch(BlockingOverlayAction.UpdateAnswer("43"))
        holder.dispatch(BlockingOverlayAction.SubmitAnswer)

        holder.dispatch(BlockingOverlayAction.AnswerChecked(correct = true))

        assertEquals(TaskAnswerStatus.CORRECT, task(holder).answerStatus)
        assertTrue(effects.last() is BlockingOverlayEffect.TaskSolved)
    }

    @Test
    fun `daily threshold atomically replaces task even while emergency form is open`() {
        val holder = holder()
        holder.dispatch(BlockingOverlayAction.OpenEmergencyForm)
        val daily = EnforcementUiState.DailyLimit(usedMinutes = 45, limitMinutes = 45)

        holder.dispatch(BlockingOverlayAction.DailyLimitReached(daily))

        assertEquals(daily, holder.state.value.enforcement)
        assertEquals(EmergencyActivationSource.DAILY_LIMIT, holder.state.value.emergencySource)
        assertTrue(holder.state.value.emergencyForm != null)
    }

    @Test
    fun `exit and Back on gate both express exit intent rather than dismiss`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)

        holder.dispatch(BlockingOverlayAction.ExitYouTube)
        holder.dispatch(BlockingOverlayAction.SystemBack)

        assertEquals(
            listOf(BlockingOverlayEffect.ExitYouTube, BlockingOverlayEffect.ExitYouTube),
            effects,
        )
        assertTrue(holder.state.value.enforcement is EnforcementUiState.TaskGate)
    }

    @Test
    fun `Back on emergency form cancels form but preserves gate`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)
        holder.dispatch(BlockingOverlayAction.OpenEmergencyForm)
        holder.dispatch(BlockingOverlayAction.UpdateEmergencyReason("Учебное видео"))

        holder.dispatch(BlockingOverlayAction.SystemBack)

        assertNull(holder.state.value.emergencyForm)
        assertTrue(holder.state.value.enforcement is EnforcementUiState.TaskGate)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `emergency reason trims for validation and requires five characters`() {
        val holder = holder()
        holder.dispatch(BlockingOverlayAction.OpenEmergencyForm)

        holder.dispatch(BlockingOverlayAction.UpdateEmergencyReason(" 1234 "))
        assertFalse(requireNotNull(holder.state.value.emergencyForm).canConfirm)

        holder.dispatch(BlockingOverlayAction.UpdateEmergencyReason(" 12345 "))
        assertTrue(requireNotNull(holder.state.value.emergencyForm).canConfirm)
        assertEquals("12345", requireNotNull(holder.state.value.emergencyForm).normalizedReason)
    }

    @Test
    fun `emergency reason input never stores more than three hundred characters`() {
        val holder = holder()
        holder.dispatch(BlockingOverlayAction.OpenEmergencyForm)

        holder.dispatch(BlockingOverlayAction.UpdateEmergencyReason("a".repeat(400)))

        assertEquals(300, requireNotNull(holder.state.value.emergencyForm).reason.length)
        assertTrue(requireNotNull(holder.state.value.emergencyForm).canConfirm)
    }

    @Test
    fun `confirm emits normalized reason and task source without dismissing early`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)
        holder.dispatch(BlockingOverlayAction.OpenEmergencyForm)
        holder.dispatch(BlockingOverlayAction.UpdateEmergencyReason("  учебное видео  "))

        holder.dispatch(BlockingOverlayAction.ConfirmEmergency)

        assertEquals(
            BlockingOverlayEffect.ConfirmEmergency(
                normalizedReason = "учебное видео",
                source = EmergencyActivationSource.TASK_GATE,
            ),
            effects.single(),
        )
        assertTrue(requireNotNull(holder.state.value.emergencyForm).submitting)
    }

    @Test
    fun `failed emergency activation keeps form and exposes retry text`() {
        val holder = submittingEmergencyHolder()

        holder.dispatch(
            BlockingOverlayAction.EmergencyActivationFinished(
                succeeded = false,
                errorMessage = "Локальное сохранение не удалось",
            ),
        )

        val form = requireNotNull(holder.state.value.emergencyForm)
        assertFalse(form.submitting)
        assertEquals("Локальное сохранение не удалось", form.submissionError)
    }

    @Test
    fun `successful emergency activation closes form only after domain confirmation`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = submittingEmergencyHolder(effects)

        holder.dispatch(
            BlockingOverlayAction.EmergencyActivationFinished(succeeded = true),
        )

        assertNull(holder.state.value.emergencyForm)
        assertTrue(effects.last() is BlockingOverlayEffect.EmergencyActivated)
    }

    @Test
    fun `cancel never emits or persists the typed emergency reason`() {
        val effects = mutableListOf<BlockingOverlayEffect>()
        val holder = holder(effects)
        holder.dispatch(BlockingOverlayAction.OpenEmergencyForm)
        holder.dispatch(BlockingOverlayAction.UpdateEmergencyReason("Приватная причина"))

        holder.dispatch(BlockingOverlayAction.CancelEmergency)

        assertNull(holder.state.value.emergencyForm)
        assertTrue(effects.isEmpty())
    }

    private fun holder(
        effects: MutableList<BlockingOverlayEffect> = mutableListOf(),
    ) = BlockingOverlayStateHolder(
        initialEnforcement = taskState(),
        emitEffect = effects::add,
    )

    private fun checkingHolder(): BlockingOverlayStateHolder = holder().also {
        it.dispatch(BlockingOverlayAction.UpdateAnswer("1"))
        it.dispatch(BlockingOverlayAction.SubmitAnswer)
    }

    private fun submittingEmergencyHolder(
        effects: MutableList<BlockingOverlayEffect> = mutableListOf(),
    ): BlockingOverlayStateHolder = holder(effects).also {
        it.dispatch(BlockingOverlayAction.OpenEmergencyForm)
        it.dispatch(BlockingOverlayAction.UpdateEmergencyReason("Учебное видео"))
        it.dispatch(BlockingOverlayAction.ConfirmEmergency)
    }

    private fun failAnswer(holder: BlockingOverlayStateHolder) {
        holder.dispatch(BlockingOverlayAction.UpdateAnswer("1"))
        holder.dispatch(BlockingOverlayAction.SubmitAnswer)
        holder.dispatch(BlockingOverlayAction.AnswerChecked(correct = false))
    }

    private fun task(holder: BlockingOverlayStateHolder) =
        holder.state.value.enforcement as EnforcementUiState.TaskGate

    private fun taskState() = EnforcementUiState.TaskGate(
        taskId = "task-1",
        visualExpression = "17 + 26",
        spokenExpression = "семнадцать плюс двадцать шесть",
        grantMinutes = 5,
    )
}
