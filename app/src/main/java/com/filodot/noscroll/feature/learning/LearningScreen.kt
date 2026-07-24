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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filodot.noscroll.core.learning.content.StaticLearningCatalog
import com.filodot.noscroll.core.learning.model.SingleChoiceContent

private enum class LearningPreviewStep {
    COURSE,
    LESSON,
    COMPLETED,
}

private enum class PreviewAnswerStatus {
    UNCHECKED,
    INCORRECT,
    CORRECT,
}

/**
 * First UI shell intentionally uses validated static content. Persistence and AI are connected in
 * later EDU packages without changing this screen's user-facing flow.
 */
@Composable
fun LearningScreen(modifier: Modifier = Modifier) {
    var step by rememberSaveable { mutableStateOf(LearningPreviewStep.COURSE) }
    var selectedOptionId by rememberSaveable { mutableStateOf<String?>(null) }
    var answerStatus by rememberSaveable { mutableStateOf(PreviewAnswerStatus.UNCHECKED) }
    var suspiciousReplacementShown by rememberSaveable { mutableStateOf(false) }

    val course = StaticLearningCatalog.pythonCourse
    val lesson = StaticLearningCatalog.firstLesson
    val activity = lesson.activities.first()
    val content = activity.content as SingleChoiceContent

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            LearningPreviewStep.COURSE -> {
                Text(
                    text = "Обучение",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Короткие уроки готовятся заранее и доступны без сети во время " +
                        "разблокировки.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                AiKnowledgeNotice()
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
                            "План: 2 понятия · офлайн: 1 урок",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Button(
                            onClick = {
                                selectedOptionId = null
                                answerStatus = PreviewAnswerStatus.UNCHECKED
                                step = LearningPreviewStep.LESSON
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Начать тестовый урок")
                        }
                    }
                }
                Text(
                    "Импорт материалов и создание курсов появятся в следующем этапе.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LearningPreviewStep.LESSON -> {
                Text(
                    text = lesson.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(lesson.introduction, style = MaterialTheme.typography.bodyLarge)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(activity.prompt, style = MaterialTheme.typography.titleLarge)
                        content.options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedOptionId == option.id,
                                        role = Role.RadioButton,
                                        onClick = {
                                            selectedOptionId = option.id
                                            answerStatus = PreviewAnswerStatus.UNCHECKED
                                        },
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedOptionId == option.id,
                                    onClick = null,
                                )
                                Text(option.text, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                        Button(
                            onClick = {
                                answerStatus = if (
                                    selectedOptionId == content.correctOptionId
                                ) {
                                    PreviewAnswerStatus.CORRECT
                                } else {
                                    PreviewAnswerStatus.INCORRECT
                                }
                            },
                            enabled = selectedOptionId != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Проверить")
                        }
                        when (answerStatus) {
                            PreviewAnswerStatus.UNCHECKED -> Unit
                            PreviewAnswerStatus.INCORRECT -> Text(
                                "Пока неверно. Измените ответ и попробуйте снова.",
                                color = MaterialTheme.colorScheme.error,
                            )

                            PreviewAnswerStatus.CORRECT -> {
                                Text(
                                    "Правильно. ${activity.explanation}",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Button(
                                    onClick = { step = LearningPreviewStep.COMPLETED },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("Завершить урок")
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                selectedOptionId = null
                                answerStatus = PreviewAnswerStatus.UNCHECKED
                                suspiciousReplacementShown = true
                            },
                        ) {
                            Text("Задание выглядит некорректным")
                        }
                        if (suspiciousReplacementShown) {
                            Text(
                                "Задание помечено для замены без штрафа. В статическом прототипе " +
                                    "показан резервный экземпляр.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { step = LearningPreviewStep.COURSE },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Вернуться к курсу")
                }
            }

            LearningPreviewStep.COMPLETED -> {
                Text(
                    text = "Урок завершён",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Первый правильный ответ сохранит прогресс после подключения EDU-02. " +
                        "Подозрительные задания не будут влиять на усвоение.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = {
                        step = LearningPreviewStep.COURSE
                        selectedOptionId = null
                        answerStatus = PreviewAnswerStatus.UNCHECKED
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("К списку курсов")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
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
            text = "Тестовый курс создан без внешних источников и опирается на знания модели.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
