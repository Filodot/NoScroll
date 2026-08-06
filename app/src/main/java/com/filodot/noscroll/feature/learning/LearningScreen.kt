package com.filodot.noscroll.feature.learning

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filodot.noscroll.core.contracts.LearningRepository
import com.filodot.noscroll.core.learning.ai.AiCredentialRepository
import com.filodot.noscroll.core.learning.ai.AiGateway
import com.filodot.noscroll.core.learning.ai.AiProviderId
import com.filodot.noscroll.core.learning.generation.AiCurriculumGenerator
import com.filodot.noscroll.core.learning.generation.AiLessonGenerator
import com.filodot.noscroll.data.learning.AndroidLearningMaterialGateway
import com.filodot.noscroll.data.learning.code.AndroidCodeExerciseEvaluator
import com.filodot.noscroll.core.learning.model.CodeCompletionContent
import com.filodot.noscroll.core.learning.model.CodeFixContent
import com.filodot.noscroll.core.learning.model.CodeLanguage
import com.filodot.noscroll.core.learning.model.CodeOutputContent
import com.filodot.noscroll.core.learning.model.CodeTestCase
import com.filodot.noscroll.core.learning.model.CourseStatus
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
    aiCredentials: AiCredentialRepository,
    modifier: Modifier = Modifier,
    aiGateway: AiGateway? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val materialGateway = remember(context) { AndroidLearningMaterialGateway(context) }
    val curriculumGenerator = remember(aiGateway) { aiGateway?.let(::AiCurriculumGenerator) }
    val lessonGenerator = remember(aiGateway) { aiGateway?.let(::AiLessonGenerator) }
    val codeEvaluator = remember { AndroidCodeExerciseEvaluator() }
    val holder = remember(
        repository,
        scope,
        materialGateway,
        aiCredentials,
        curriculumGenerator,
        lessonGenerator,
        codeEvaluator,
    ) {
        LearningStateHolder(
            repository = repository,
            scope = scope,
            materialGateway = materialGateway,
            aiCredentials = aiCredentials,
            curriculumGenerator = curriculumGenerator,
            lessonGenerator = lessonGenerator,
            codeEvaluator = codeEvaluator,
        )
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
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.loading) {
            Text("Загружаем курсы…", style = MaterialTheme.typography.titleLarge)
        } else {
            when (state.pane) {
                LearningPane.COURSES -> CoursesPane(state, onAction)
                LearningPane.CREATE -> CreateCoursePane(state, onAction)
                LearningPane.AI_SETTINGS -> AiSettingsPane(state, onAction)
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
    Button(
        onClick = { onAction(LearningAction.StartCreateCourse) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("Создать свой курс")
    }
    if (state.courses.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Курсов пока нет", style = MaterialTheme.typography.titleLarge)
                Text("Начните с темы или загрузите свой учебный материал.")
                OutlinedButton(
                    onClick = { onAction(LearningAction.CreateDemoCourse) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Посмотреть демо-курс Python")
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
private fun CreateCoursePane(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onAction(LearningAction.ImportMaterial(it.toString())) }
    }
    TextButton(
        onClick = { onAction(LearningAction.CancelCreateCourse) },
        enabled = !state.importingMaterial,
    ) {
        Text("← Отмена")
    }
    Heading("Новый курс")
    Text(
        "Укажите тему или загрузите материал. План можно будет проверить и изменить до начала.",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedButton(
        onClick = { onAction(LearningAction.OpenAiSettings) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        val ready = state.aiProviders.count { it.enabled && it.hasApiKey }
        Text("Настроить ИИ · готово: $ready из 3")
    }
    OutlinedTextField(
        value = state.createTitle,
        onValueChange = { onAction(LearningAction.SetCreateTitle(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Название курса (необязательно)") },
        enabled = !state.importingMaterial,
        singleLine = true,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("По теме", style = MaterialTheme.typography.titleLarge)
            Text(
                "Нейросеть составит редактируемый план. Факты не будут привязаны к источнику.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.createTopic,
                onValueChange = { onAction(LearningAction.SetCreateTopic(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Например: основы SQL для аналитика") },
                enabled = !state.importingMaterial,
                minLines = 2,
                maxLines = 5,
            )
            Button(
                onClick = { onAction(LearningAction.CreateTopicCourse) },
                enabled = !state.importingMaterial,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Создать по теме")
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("По своему материалу", style = MaterialTheme.typography.titleLarge)
            Text(
                "PDF, DOCX, TXT или Markdown до 20 МБ. Текст обрабатывается на телефоне.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.importingMaterial) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Извлекаем и подготавливаем материал…")
            } else {
                OutlinedButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain",
                                "text/markdown",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Выбрать файл")
                }
            }
        }
    }
}

@Composable
private fun AiSettingsPane(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
) {
    TextButton(onClick = { onAction(LearningAction.CloseAiSettings) }) {
        Text("← Новый курс")
    }
    Heading("Провайдеры ИИ")
    Text(
        "Для отказоустойчивости NoScroll пробует включённые сервисы по порядку. " +
            "Достаточно одного ключа, лучше настроить все три.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            "Ключи шифруются Android Keystore, не попадают в резервные копии и никогда " +
                "не отправляются другому провайдеру.",
            modifier = Modifier.padding(16.dp),
        )
    }
    state.aiProviders.sortedBy { it.priority }.forEach { provider ->
        val editing = state.editingAiProvider == provider.id
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(provider.id.displayName(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (provider.hasApiKey) "Ключ сохранён" else "Нужен API-ключ",
                            color = if (provider.hasApiKey) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = {
                            onAction(LearningAction.ToggleAiProvider(provider.id, it))
                        },
                    )
                }
                Text(
                    providerHelp(provider.id),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (editing) {
                    OutlinedTextField(
                        value = state.aiModelDraft,
                        onValueChange = { onAction(LearningAction.SetAiModelDraft(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Модель") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.aiApiKeyDraft,
                        onValueChange = { onAction(LearningAction.SetAiApiKeyDraft(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (provider.hasApiKey) {
                                    "Новый ключ (оставьте пустым, чтобы не менять)"
                                } else {
                                    "API-ключ"
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = { onAction(LearningAction.SaveAiProvider) },
                        enabled = state.aiModelDraft.isNotBlank() &&
                            (provider.hasApiKey || state.aiApiKeyDraft.length >= 8),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Сохранить")
                    }
                    if (provider.hasApiKey) {
                        TextButton(
                            onClick = {
                                onAction(LearningAction.ClearAiProviderKey(provider.id))
                            },
                        ) {
                            Text("Удалить сохранённый ключ")
                        }
                    }
                } else {
                    Text("Модель: ${provider.modelId}")
                    OutlinedButton(
                        onClick = { onAction(LearningAction.EditAiProvider(provider.id)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (provider.hasApiKey) "Изменить" else "Добавить ключ")
                    }
                }
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
            if (state.readyLessons > 0) {
                Button(
                    onClick = { onAction(LearningAction.StartLesson) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Начать следующий урок")
                }
            }
            if (content.course.status == CourseStatus.READY) {
                if (state.generatingLesson) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Создаём и проверяем пакет: ${state.generatedLessonCount} из " +
                            "${state.lessonGenerationTarget}",
                    )
                } else {
                    Text("Сколько добавить в офлайн-пул")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 5, 10).forEach { count ->
                            FilterChip(
                                selected = state.lessonBatchSize == count,
                                onClick = { onAction(LearningAction.SetLessonBatchSize(count)) },
                                label = { Text(count.toString()) },
                            )
                        }
                    }
                    Button(
                        onClick = { onAction(LearningAction.GenerateLessonBatch) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Подготовить ${state.lessonBatchSize} офлайн")
                    }
                    Text(
                        "После прохождения пул автоматически пополняется до 3 уроков.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Сначала создайте и подтвердите план курса.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (content.course.status == CourseStatus.DRAFT) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Черновик программы", style = MaterialTheme.typography.titleLarge)
                Text(
                    "При запуске выбранные фрагменты материала будут отправлены внешнему " +
                        "AI-провайдеру. Проверьте результат перед подтверждением.",
                )
                state.lastPlanProviderLabel?.let { Text(it) }
                if (state.generatingPlan) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Создаём и проверяем план. Плохой ответ будет запрошен заново…")
                } else {
                    Button(
                        onClick = { onAction(LearningAction.GeneratePlan) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (content.curriculumNodes.isEmpty()) {
                                "Сгенерировать план"
                            } else {
                                "Перегенерировать план"
                            },
                        )
                    }
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = { onAction(LearningAction.BeginPlanEdit) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Редактировать план")
        }
    }
    OutlinedButton(
        onClick = { onAction(LearningAction.RequestDeleteCourse) },
        enabled = !state.generatingPlan && !state.generatingLesson && !state.deletingCourse,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(if (state.deletingCourse) "Удаляем…" else "Удалить курс")
    }
    if (state.deleteCourseConfirmation) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Удалить курс безвозвратно?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Будут удалены план, извлечённый текст материалов, уроки, попытки и прогресс.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Button(
                    onClick = { onAction(LearningAction.ConfirmDeleteCourse) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Удалить навсегда")
                }
                TextButton(
                    onClick = { onAction(LearningAction.CancelDeleteCourse) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Отмена")
                }
            }
        }
    }
    Heading("План курса", small = true)
    if (content.curriculumNodes.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Материал подготовлен", style = MaterialTheme.typography.titleMedium)
                if (content.sources.isNotEmpty()) {
                    Text(
                        "${content.sources.size} источников · " +
                            "${content.sourceChunks.size} фрагментов",
                    )
                }
                Text(
                    "Редактируемый план появится после подключения генерации.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val orderedNodes = content.curriculumNodes.sortedBy { it.position }
        orderedNodes.forEachIndexed { index, node ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (content.course.status == CourseStatus.DRAFT) {
                        OutlinedTextField(
                            value = node.title,
                            onValueChange = {
                                onAction(LearningAction.SetPlanNodeTitle(node.id, it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Тема ${index + 1}") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = node.description,
                            onValueChange = {
                                onAction(LearningAction.SetPlanNodeDescription(node.id, it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Результат обучения") },
                            minLines = 2,
                            maxLines = 5,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onAction(LearningAction.MovePlanNode(node.id, -1))
                                },
                                enabled = index > 0,
                            ) {
                                Text("↑")
                            }
                            OutlinedButton(
                                onClick = {
                                    onAction(LearningAction.MovePlanNode(node.id, 1))
                                },
                                enabled = index < orderedNodes.lastIndex,
                            ) {
                                Text("↓")
                            }
                            TextButton(
                                onClick = { onAction(LearningAction.DeletePlanNode(node.id)) },
                            ) {
                                Text("Удалить")
                            }
                        }
                    } else {
                        Text(node.title, style = MaterialTheme.typography.titleMedium)
                        Text(node.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${node.estimatedMinutes} мин · ${node.conceptIds.size} понятия")
                }
            }
        }
        if (content.course.status == CourseStatus.DRAFT) {
            OutlinedButton(
                onClick = { onAction(LearningAction.AddPlanNode) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Добавить тему")
            }
            Button(
                onClick = { onAction(LearningAction.ConfirmPlan) },
                enabled = !state.generatingPlan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Подтвердить план")
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
    if (state.showingLessonMaterial) {
        Text(
            "Мини-урок · сначала изучите материал",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Материал урока", style = MaterialTheme.typography.titleLarge)
                Text(lesson.introduction, style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { onAction(LearningAction.OpenLessonQuestions) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Перейти к заданиям")
                }
            }
        }
        return
    }
    Text(
        "Задание ${state.activityIndex + 1} из ${lesson.activities.size}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                    !state.checkingAnswer &&
                    hasAnswer(state, activity),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (state.checkingAnswer) "Проверяем…" else "Проверить")
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
            CodeSandboxNotice(content.language)
            VisibleCodeTests(content.tests)
            TextAnswerInput(state, "Исправленный код", onAction, minLines = 4)
        }

        is MiniCodeContent -> {
            CodeBlock(content.starterCode)
            CodeSandboxNotice(content.language)
            VisibleCodeTests(content.tests)
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

        is MatchingContent -> MatchingInput(state, content, onAction)
    }
}

@Composable
private fun MatchingInput(
    state: LearningUiState,
    content: MatchingContent,
    onAction: (LearningAction) -> Unit,
) {
    content.left.forEach { left ->
        Text(left.text, style = MaterialTheme.typography.titleMedium)
        content.right.forEach { right ->
            val selected = state.matchingRightIdByLeftId[left.id] == right.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onAction(LearningAction.SetMatch(left.id, right.id)) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(right.text, modifier = Modifier.padding(start = 10.dp))
            }
        }
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
private fun CodeSandboxNotice(language: CodeLanguage) {
    Text(
        text = when (language) {
            CodeLanguage.PYTHON ->
                "Безопасный Python-поднабор: одна функция и одно выражение return; " +
                    "без импортов, циклов, файлов и сети."

            CodeLanguage.SQL ->
                "Выполняется в новой in-memory SQLite: разрешён один SELECT или WITH…SELECT; " +
                    "изменение базы, файловые и системные команды запрещены."
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun VisibleCodeTests(tests: List<CodeTestCase>) {
    val visible = tests.filterNot(CodeTestCase::hidden)
    if (visible.isNotEmpty()) {
        Text("Открытые тесты", style = MaterialTheme.typography.titleMedium)
        visible.forEach { test ->
            CodeBlock("input: ${test.input.ifBlank { "—" }}\noutput: ${test.expectedOutput}")
        }
    }
    val hiddenCount = tests.count(CodeTestCase::hidden)
    if (hiddenCount > 0) {
        Text(
            "Скрытых тестов: $hiddenCount",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    is MatchingContent ->
        state.matchingRightIdByLeftId.keys == content.left.mapTo(mutableSetOf()) { it.id }
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

private fun providerHelp(providerId: AiProviderId): String = when (providerId) {
    AiProviderId.GEMINI -> "Основной: бесплатный tier Google AI Studio · aistudio.google.com"
    AiProviderId.GROQ -> "Резерв №1: быстрый free plan · console.groq.com"
    AiProviderId.OPENROUTER -> "Резерв №2: бесплатные модели, обычно 50 запросов/день · openrouter.ai"
}
