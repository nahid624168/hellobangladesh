package com.exambase.ai.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.exams.ExamDetailViewModel
import com.exambase.ai.exams.ExamListViewModel
import com.exambase.ai.exams.ExamPlayerViewModel
import com.exambase.ai.exams.ExamResultViewModel
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun ExamListScreen(onBack: () -> Unit, onOpenExam: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { ExamListViewModel(it.examRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.exams_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.loading && state.list.items.isEmpty() -> Text(stringResource(R.string.loading))
                state.error != null && state.list.items.isEmpty() -> {
                    Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.list.items.isEmpty() -> Text(stringResource(R.string.exams_empty))
                else -> state.list.items.forEach { e ->
                    Card(Modifier = Modifier.fillMaxWidth().clickable { onOpenExam(e.id) }) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.exams_status_line, e.status, e.attemptsUsed, e.maxAttempts))
                        }
                    }
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}

@Composable
fun ExamDetailScreen(
    examId: String,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onResult: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ExamDetailViewModel(examId, it.examRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.exam_detail_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null -> {
                    Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.detail != null -> {
                    val d = state.detail!!
                    Text(d.exam.title, style = MaterialTheme.typography.titleLarge)
                    Text(d.instructions, style = MaterialTheme.typography.bodyMedium)
                    d.sections.forEach { s ->
                        Text(stringResource(R.string.exam_section_line, s.position, s.title, s.questionCount))
                    }
                    when (d.attempt?.status) {
                        "in_progress" -> PrimaryButton(
                            stringResource(R.string.exam_continue),
                            onClick = { onStart(examId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        "submitted" -> {
                            SecondaryButton(
                                stringResource(R.string.exam_view_result),
                                onClick = { onResult(d.attempt!!.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (d.exam.attemptsUsed < d.exam.maxAttempts) {
                                PrimaryButton(
                                    stringResource(R.string.exam_start),
                                    onClick = { onStart(examId) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        else -> PrimaryButton(
                            stringResource(R.string.exam_start),
                            onClick = { onStart(examId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}

@Composable
fun ExamPlayerScreen(
    examId: String,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ExamPlayerViewModel(examId, it.examRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.result) {
        state.result?.attempt?.id?.let { onFinished(it) }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.exam_player_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null && state.questions.isEmpty() -> {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::start)
                }
                state.questions.isNotEmpty() -> {
                    val q = state.questions[state.index]
                    state.remainingMs?.let {
                        Text(stringResource(R.string.exam_time_left, it / 1000), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.exam_progress, state.index + 1, state.questions.size))
                    Text(q.questionText, style = MaterialTheme.typography.titleMedium)
                    val multi = q.type == "multiple_select"
                    if (q.options.isNotEmpty()) {
                        q.options.forEach { opt ->
                            FilterChip(
                                selected = opt.id in state.answers[q.id].orEmpty(),
                                onClick = { vm.selectOption(q.id, opt.id, multi) },
                                label = { Text(opt.text) },
                            )
                        }
                    } else {
                        ExamBaseTextField(
                            value = state.textAnswers[q.id].orEmpty(),
                            onValueChange = { vm.setText(q.id, it); vm.saveText(q.id) },
                            label = stringResource(R.string.exam_text_answer),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (state.index > 0) LinkButton(stringResource(R.string.prev_lesson), vm::prev) else Spacer(Modifier)
                        if (state.index < state.questions.lastIndex) LinkButton(stringResource(R.string.next_lesson), vm::next)
                    }
                    PrimaryButton(stringResource(R.string.exam_submit), onClick = vm::submit, modifier = Modifier.fillMaxWidth())
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}

@Composable
fun ExamResultScreen(attemptId: String, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { ExamResultViewModel(attemptId, it.examRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.exam_result_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null -> {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.result != null -> {
                    val r = state.result!!
                    if (!r.resultsVisible) {
                        Text(stringResource(R.string.exam_results_pending))
                    } else {
                        Text(
                            stringResource(
                                R.string.exam_score_line,
                                r.attempt.score ?: 0.0,
                                r.attempt.percent ?: 0.0,
                                r.attempt.passed?.toString() ?: "—",
                            ),
                        )
                        r.perQuestion.forEach { q ->
                            Text("${q.questionId.take(8)}… ${q.result} (${q.marksAwarded ?: 0.0})")
                        }
                    }
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}
