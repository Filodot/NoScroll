package com.filodot.noscroll.feature.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.learning.model.CodeCompletionContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.EvidenceSelectionContent
import com.filodot.noscroll.core.learning.model.FillBlankContent
import com.filodot.noscroll.core.learning.model.FlashcardContent
import com.filodot.noscroll.core.learning.model.LearningActivity
import com.filodot.noscroll.core.learning.model.MatchingContent
import com.filodot.noscroll.core.learning.model.MiniCodeContent
import com.filodot.noscroll.core.learning.model.MultipleChoiceContent
import com.filodot.noscroll.core.learning.model.NumericAnswerContent
import com.filodot.noscroll.core.learning.model.OrderingContent
import com.filodot.noscroll.core.learning.model.ScenarioContent
import com.filodot.noscroll.core.learning.model.ShortAnswerContent
import com.filodot.noscroll.core.learning.model.SingleChoiceContent
import com.filodot.noscroll.core.learning.model.TeachBackContent
import com.filodot.noscroll.core.learning.model.TrueFalseContent

@Composable
fun LearningRoute(
    repository: LearningRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(repository, scope) {
        LearningStateHolder(repository = repository, scope = scope)
    }
    val state by holder.state.collectAsStateWithLifecycle()
    LearningScreen(state = state, onAction = holder::dispatch, modifier = modifier)
}

@Composable
fun LearningScreen(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.loading) {
            Text("Загружаем курсы…", style = MaterialTheme.typography.titleLarge)
        } else {
            when (state.pane) {
                LearningPane.COURSES -> CoursesPane(state, onAction)
                LearningPane.COURSE -> CoursePane(state, onAction)
                LearningPane.LESSON -> LessonPane(state, onAction)
                LearningPane.COMPLETED -> CompletedPane(state, onAction)
            }
        }
        state.message?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(message)
                    TextButton(onClick = { onAction(LearningAction.DismissMessage) }) {
                        Text("Понятно")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CoursesPane(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
) {
    Heading("Обучение")
    Text(
        "Курсы постепенно открывают новые темы и возвращают материал к повторению.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
    if (state.courses.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Курсов пока нет", style = MaterialTheme.typography.titleLarge)
                Text("Добавьте демонстрационный курс, чтобы проверить локальный движок.")
                Button(
                    onClick = { onAction(LearningAction.CreateDemoCourse) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Добавить демо-курс Python")
                }
            }
        }
    } else {
        state.courses.forEach { course ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(course.title, style = MaterialTheme.typography.headlineSmall)
                    Text(course.description)
                    Text(
                        "Усвоение: ${course.masteryPercent}% · офлайн-уроков: " +
                            course.readyLessons,
                    )
                    if (course.aiKnowledgeOnly) AiKnowledgeLabel()
                    Button(
                        onClick = { onAction(LearningAction.OpenCourse(course.id)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Открыть курс")
                    }
                }
            }
        }
        if (state.courses.none { it.id == "course-python-basics" }) {
            OutlinedButton(
                onClick = { onAction(LearningAction.CreateDemoCourse) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Добавить демо-курс")
            }
        }
    }
}

@Composable
private fun CoursePane(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
) {
    val content = state.selectedCourse ?: return
    TextButton(onClick = { onAction(LearningAction.BackToCourses) }) {
        Text("← Все курсы")
    }
    Heading(content.course.title)
    Text(content.course.description, style = MaterialTheme.typography.bodyLarge)
    if (content.course.groundingMode ==
        com.filodot.noscroll.core.learning.model.GroundingMode.AI_KNOWLEDGE
    ) {
        AiKnowledgeNotice()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Прогресс", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { state.selectedCourseMasteryPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${state.selectedCourseMasteryPercent}% усвоено")
            Text("Готово офлайн: ${state.readyLessons}")
            Button(
                onClick = { onAction(LearningAction.StartLesson) },
                enabled = state.readyLessons > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (state.readyLessons > 0) "Начать следующий урок" else "Нет готовых уроков")
            }
        }
    }
    Heading("План курса", small = true)
    content.curriculumNodes.forEach { node ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(node.title, style = MaterialTheme.typography.titleMedium)
                Text(node.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${node.estimatedMinutes} мин · ${node.conceptIds.size} понятия")
            }
        }
    }
}

@Composable
private fun LessonPane(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
) {
    val lesson = state.lesson ?: return
    val activity = state.currentActivity ?: return
    TextButton(onClick = { onAction(LearningAction.BackToCourse) }) {
        Text("← Вернуться к курсу")
    }
    Heading(lesson.title)
    Text(
        "Задание ${state.activityIndex + 1} из ${lesson.activities.size}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(lesson.introduction, style = MaterialTheme.typography.bodyLarge)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(activity.prompt, style = MaterialTheme.typography.titleLarge)
            ActivityInput(state, activity, onAction)
            Button(
                onClick = { onAction(LearningAction.CheckAnswer) },
                enabled = state.answerStatus != LearningAnswerStatus.CORRECT &&
                    hasAnswer(state, activity),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Проверить")
            }
            when (state.answerStatus) {
                LearningAnswerStatus.UNCHECKED -> Unit
                LearningAnswerStatus.INCORRECT -> Text(
                    "Пока неверно. Измените ответ и попробуйте снова.",
                    color = MaterialTheme.colorScheme.error,
                )

                LearningAnswerStatus.CORRECT -> {
                    Text(
                        "Правильно. ${activity.explanation}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = { onAction(LearningAction.ContinueLesson) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (state.activityIndex == lesson.activities.lastIndex) {
                                "Завершить урок"
                            } else {
                                "Следующее задание"
                            },
                        )
                    }
                }

                LearningAnswerStatus.REQUIRES_EXTERNAL -> Text(
                    "Формат будет доступен после подключения безопасной проверки.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = { onAction(LearningAction.ReplaceSuspicious) }) {
                Text("Задание выглядит некорректным")
            }
        }
    }
}

@Composable
private fun ActivityInput(
    state: LearningUiState,
    activity: LearningActivity,
    onAction: (LearningAction) -> Unit,
) {
    when (val content = activity.content) {
        is SingleChoiceContent -> ChoiceOptions(
            options = content.options,
            selected = state.selectedOptionIds,
            multiple = false,
            onAction = onAction,
        )

        is ScenarioContent -> ChoiceOptions(
            options = content.options,
            selected = state.selectedOptionIds,
            multiple = false,
            onAction = onAction,
        )

        is MultipleChoiceContent -> ChoiceOptions(
            options = content.options,
            selected = state.selectedOptionIds,
            multiple = true,
            onAction = onAction,
        )

        is EvidenceSelectionContent -> ChoiceOptions(
            options = content.options,
            selected = state.selectedOptionIds,
            multiple = true,
            onAction = onAction,
        )

        is TrueFalseContent -> {
            Text(content.statement)
            ChoiceOptions(
                options = listOf(
                    com.filodot.noscroll.core.learning.model.ChoiceOption("true", "Верно"),
                    com.filodot.noscroll.core.learning.model.ChoiceOption("false", "Неверно"),
                ),
                selected = state.booleanAnswer?.let { setOf(it.toString()) }.orEmpty(),
                multiple = false,
                onAction = { action ->
                    if (action is LearningAction.SelectOption) {
                        onAction(LearningAction.SetBooleanAnswer(action.optionId.toBoolean()))
                    }
                },
            )
        }

        is OrderingContent -> OrderingInput(state, content, onAction)
        is FillBlankContent -> TextAnswerInput(
            state,
            "Введите пропущенное значение",
            onAction,
        )

        is ShortAnswerContent -> TextAnswerInput(state, "Короткий ответ", onAction)
        is NumericAnswerContent -> TextAnswerInput(state, "Число", onAction)
        is CodeOutputContent -> {
            CodeBlock(content.code)
            TextAnswerInput(state, "Что выведет код?", onAction)
        }

        is CodeCompletionContent -> {
            CodeBlock(content.codeWithBlank)
            TextAnswerInput(state, "Код вместо {{code}}", onAction, minLines = 2)
        }

        is CodeFixContent -> {
            CodeBlock(content.brokenCode)
            TextAnswerInput(state, "Исправленный код", onAction, minLines = 4)
        }

        is MiniCodeContent -> {
            CodeBlock(content.starterCode)
            TextAnswerInput(state, "Ваш код", onAction, minLines = 4)
        }

        is TeachBackContent -> TextAnswerInput(
            state,
            "Объясните своими словами",
            onAction,
            minLines = 3,
        )

        is FlashcardContent -> {
            Text("Вспомните ответ самостоятельно.")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(content.answer, modifier = Modifier.padding(16.dp))
            }
        }

        is MatchingContent -> Text(
            "Интерактивное сопоставление пар подключается в следующей версии UI.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceOptions(
    options: List<com.filodot.noscroll.core.learning.model.ChoiceOption>,
    selected: Set<String>,
    multiple: Boolean,
    onAction: (LearningAction) -> Unit,
) {
    options.forEach { option ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = option.id in selected,
                    role = if (multiple) Role.Checkbox else Role.RadioButton,
                    onClick = {
                        onAction(LearningAction.SelectOption(option.id, multiple))
                    },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiple) {
                Checkbox(checked = option.id in selected, onCheckedChange = null)
            } else {
                RadioButton(selected = option.id in selected, onClick = null)
            }
            Text(option.text, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun OrderingInput(
    state: LearningUiState,
    content: OrderingContent,
    onAction: (LearningAction) -> Unit,
) {
    val byId = content.items.associateBy { it.id }
    state.orderedItemIds.forEachIndexed { index, id ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${index + 1}. ${byId[id]?.text.orEmpty()}", modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { onAction(LearningAction.MoveOrderedItem(id, -1)) },
                enabled = index > 0,
            ) {
                Text("↑")
            }
            OutlinedButton(
                onClick = { onAction(LearningAction.MoveOrderedItem(id, 1)) },
                enabled = index < state.orderedItemIds.lastIndex,
            ) {
                Text("↓")
            }
        }
    }
}

@Composable
private fun TextAnswerInput(
    state: LearningUiState,
    label: String,
    onAction: (LearningAction) -> Unit,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = state.textAnswer,
        onValueChange = { onAction(LearningAction.SetTextAnswer(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = 8,
    )
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = code,
            modifier = Modifier.padding(16.dp),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CompletedPane(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
) {
    Heading("Урок завершён")
    Text(
        "Попытки и прогресс сохранены на устройстве. Следующее повторение будет выбрано " +
            "планировщиком.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text("Текущее усвоение курса: ${state.selectedCourseMasteryPercent}%")
    Button(
        onClick = { onAction(LearningAction.BackToCourse) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("Вернуться к курсу")
    }
}

@Composable
private fun Heading(
    text: String,
    small: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = if (small) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.headlineLarge
        },
    )
}

@Composable
private fun AiKnowledgeNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            text = "Материал создан без проверяемой базы знаний и опирается на знания модели.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun AiKnowledgeLabel() {
    Text(
        "Без внешних источников",
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

private fun hasAnswer(
    state: LearningUiState,
    activity: LearningActivity,
): Boolean = when (val content = activity.content) {
    is SingleChoiceContent,
    is MultipleChoiceContent,
    is EvidenceSelectionContent,
    is ScenarioContent,
    -> state.selectedOptionIds.isNotEmpty()

    is TrueFalseContent -> state.booleanAnswer != null
    is OrderingContent -> state.orderedItemIds.size == content.items.size
    is FlashcardContent -> true
    is MatchingContent -> false
    is FillBlankContent,
    is ShortAnswerContent,
    is NumericAnswerContent,
    is CodeOutputContent,
    is CodeCompletionContent,
    is CodeFixContent,
    is MiniCodeContent,
    is TeachBackContent,
    -> state.textAnswer.isNotBlank()
}
