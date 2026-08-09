package com.filodot.noscroll.core.tasks

import com.filodot.noscroll.core.model.ArithmeticOperation
import com.filodot.noscroll.core.model.PendingTask
import com.filodot.noscroll.core.model.TaskChoice
import com.filodot.noscroll.core.model.TaskCompletionMode
import com.filodot.noscroll.core.model.TaskDifficulty
import com.filodot.noscroll.core.model.TaskTarget
import com.filodot.noscroll.core.model.TaskTrigger
import com.filodot.noscroll.core.model.TaskType
import java.time.Instant

/** Small, deterministic offline vocabulary lessons that never require a network request. */
internal object EnglishVocabularyTaskFactory {
    fun create(
        id: String,
        createdAt: Instant,
        difficulty: TaskDifficulty,
        trigger: TaskTrigger,
        target: TaskTarget,
        sequence: Int,
    ): PendingTask {
        val level = VocabularyLevel.forDifficulty(difficulty)
        val catalog = entries.getValue(level)
        val start = Math.floorMod(sequence * WORD_STEP, catalog.size)
        val lessonWords = List(WORDS_PER_LESSON) { offset ->
            catalog[(start + offset) % catalog.size]
        }
        val testedIndex = Math.floorMod(sequence, lessonWords.size)
        val tested = lessonWords[testedIndex]
        val askForWord = Math.floorMod(sequence, 2) == 1
        val choices = lessonWords.map { entry ->
            TaskChoice(
                id = entry.word,
                text = if (askForWord) entry.word else entry.meaning,
            )
        }
        val prompt = if (askForWord) {
            "Какое английское слово означает «${tested.meaning}»?"
        } else {
            "Выберите значение слова «${tested.word}»."
        }
        val material = buildString {
            append("Уровень ").append(level.label).append('\n')
            lessonWords.forEach { entry ->
                append("• ").append(entry.word).append(" — ").append(entry.meaning).append('\n')
                append("  ").append(entry.example).append('\n')
            }
        }.trim()

        return PendingTask(
            id = id,
            operation = ArithmeticOperation.ADD,
            leftOperand = 0,
            rightOperand = 0,
            expectedAnswer = 0,
            createdAt = createdAt,
            difficulty = difficulty,
            trigger = trigger,
            target = target,
            type = TaskType.ENGLISH_VOCABULARY,
            completionMode = TaskCompletionMode.SINGLE_CHOICE,
            prompt = prompt,
            choices = choices,
            expectedChoiceId = tested.word,
            learningMaterial = material,
            learningExplanation = "Слово ${tested.word} означает «${tested.meaning}».",
        )
    }
}

private enum class VocabularyLevel(val label: String) {
    B1("B1"),
    B2("B2"),
    C1("C1"),
    ;

    companion object {
        fun forDifficulty(difficulty: TaskDifficulty): VocabularyLevel = when (difficulty) {
            TaskDifficulty.EASY -> B1
            TaskDifficulty.MEDIUM -> B2
            TaskDifficulty.HARD -> C1
        }
    }
}

private data class VocabularyEntry(
    val word: String,
    val meaning: String,
    val example: String,
)

private val entries = mapOf(
    VocabularyLevel.B1 to listOf(
        VocabularyEntry("achieve", "достигать", "She achieved her goal after months of practice."),
        VocabularyEntry("afford", "позволить себе", "We cannot afford a longer trip this year."),
        VocabularyEntry("avoid", "избегать", "Try to avoid checking your phone during meals."),
        VocabularyEntry("benefit", "приносить пользу", "Regular breaks benefit both memory and attention."),
        VocabularyEntry("challenge", "сложная задача", "Learning ten new words is a useful challenge."),
        VocabularyEntry("depend", "зависеть", "The result depends on how often you practise."),
        VocabularyEntry("improve", "улучшать", "Reading every day will improve your vocabulary."),
        VocabularyEntry("likely", "вероятный; вероятно", "It is likely to rain later today."),
        VocabularyEntry("notice", "замечать", "Did you notice the change in his voice?"),
        VocabularyEntry("provide", "предоставлять", "The guide provides several practical examples."),
        VocabularyEntry("realize", "осознавать", "I realized that I had left my keys at home."),
        VocabularyEntry("suggest", "предлагать", "She suggested taking a short walk."),
    ),
    VocabularyLevel.B2 to listOf(
        VocabularyEntry("approach", "подход; способ решения", "We need a different approach to this problem."),
        VocabularyEntry("assume", "предполагать", "Do not assume that the first answer is correct."),
        VocabularyEntry("circumstance", "обстоятельство", "The plan may change depending on the circumstances."),
        VocabularyEntry("consequence", "последствие", "Every decision has a possible consequence."),
        VocabularyEntry("considerable", "значительный", "The project required considerable effort."),
        VocabularyEntry("despite", "несмотря на", "Despite the delay, we finished on time."),
        VocabularyEntry("emerge", "возникать; становиться известным", "A clear pattern began to emerge."),
        VocabularyEntry("ensure", "обеспечивать; убеждаться", "Check the address to ensure it is correct."),
        VocabularyEntry("maintain", "поддерживать; сохранять", "It is hard to maintain focus without breaks."),
        VocabularyEntry("relevant", "относящийся к делу", "Please include only relevant information."),
        VocabularyEntry("require", "требовать", "This task requires careful attention."),
        VocabularyEntry("significant", "существенный; значимый", "They reported a significant improvement."),
    ),
    VocabularyLevel.C1 to listOf(
        VocabularyEntry("advocate", "поддерживать; выступать за", "Many experts advocate regular digital breaks."),
        VocabularyEntry("compelling", "убедительный; захватывающий", "She presented a compelling argument."),
        VocabularyEntry("controversial", "спорный", "The proposal remains highly controversial."),
        VocabularyEntry("deteriorate", "ухудшаться", "His concentration began to deteriorate."),
        VocabularyEntry("discrepancy", "несоответствие; расхождение", "We found a discrepancy in the two reports."),
        VocabularyEntry("elaborate", "подробно объяснять", "Could you elaborate on your main point?"),
        VocabularyEntry("feasible", "осуществимый", "The team agreed that the plan was feasible."),
        VocabularyEntry("inherent", "неотъемлемый; присущий", "Uncertainty is inherent in every forecast."),
        VocabularyEntry("inevitable", "неизбежный", "Some change is inevitable over time."),
        VocabularyEntry("mitigate", "смягчать; уменьшать вред", "Short pauses can mitigate mental fatigue."),
        VocabularyEntry("perspective", "точка зрения", "The discussion gave me a new perspective."),
        VocabularyEntry("subsequent", "последующий", "Subsequent tests confirmed the result."),
    ),
)

private const val WORDS_PER_LESSON = 4
private const val WORD_STEP = 3
