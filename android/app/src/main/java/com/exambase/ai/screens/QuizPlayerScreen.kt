package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.quizzes.QuizPlayerViewModel
import com.exambase.core.network.QuizAnswerRequest
import com.exambase.core.network.QuestionTypes
import com.exambase.core.designsystem.theme.Spacing
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun QuizPlayerScreen(quizSetId: String, attemptId: String?, onBack: () -> Unit, onSignedOut: () -> Unit, onOpenResult: (String) -> Unit) {
    val vm = appViewModel { QuizPlayerViewModel(quizSetId, attemptId, it.quizRepository) }
    val state by vm.state.collectAsState()
    var index by rememberSaveable(attemptId) { mutableIntStateOf(0) }
    // Save the answer map in a Bundle-safe string so Android can restore it after process death.
    var savedAnswers by rememberSaveable(attemptId) { mutableStateOf("") }
    var savedTextAnswers by rememberSaveable(attemptId) { mutableStateOf("") }
    var confirmSubmit by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(-1) }
    val answers = remember(savedAnswers) {
        savedAnswers.split(";").mapNotNull { row ->
            val p = row.split("=", limit = 2)
            if (p.size == 2) p[0] to p[1].split(",").filter(String::isNotBlank) else null
        }.toMap()
    }
    val textAnswers = remember(savedTextAnswers) {
        savedTextAnswers.split(";").mapNotNull { row ->
            val p = row.split("=", limit = 2)
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()
    }
    val latestAnswers by rememberUpdatedState(answers)
    val latestTextAnswers by rememberUpdatedState(textAnswers)
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.attempt?.deadlineAt, state.attempt?.serverNow) {
        val a = state.attempt ?: return@LaunchedEffect
        val deadline = runCatching { Instant.parse(a.deadlineAt) }.getOrNull()
        val server = runCatching { Instant.parse(a.serverNow) }.getOrNull()
        if (deadline != null && server != null) {
            val initial = Duration.between(server, deadline).seconds.coerceAtLeast(0)
            val started = System.currentTimeMillis()
            while (true) {
                secondsLeft = (initial - (System.currentTimeMillis() - started) / 1000).toInt().coerceAtLeast(0)
                if (secondsLeft == 0) { vm.submit(a.questions.map { q -> QuizAnswerRequest(q.question.id, latestAnswers[q.question.id].orEmpty(), latestTextAnswers[q.question.id]) }); break }
                delay(1000)
            }
        }
    }
    ContentScaffold(stringResource(R.string.quiz_player_title), onBack) {
        when {
            state.loading -> Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            state.result != null -> {
                val result = state.result!!
                Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.quiz_submitted), style = MaterialTheme.typography.headlineSmall)
                    Text("${result.score} / ${result.maxScore}", style = MaterialTheme.typography.headlineMedium)
                    Text("${result.percent}%")
                    result.passed?.let { Text(if (it) stringResource(R.string.quiz_passed) else stringResource(R.string.quiz_not_passed)) }
                    Button(onClick = { onOpenResult(result.attemptId) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.quiz_review_answers)) }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
                }
            }
            state.attempt == null -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(state.error ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::reload) { Text(stringResource(R.string.retry)) }
            }
            else -> {
                val attempt = state.attempt!!
                val question = attempt.questions.getOrNull(index)
                Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1} / ${attempt.questions.size}", style = MaterialTheme.typography.titleMedium)
                        if (secondsLeft >= 0) Text("%02d:%02d".format(secondsLeft / 60, secondsLeft % 60), color = if (secondsLeft < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(question?.question?.text.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            question?.question?.textBn?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    if (question?.question?.type == QuestionTypes.SHORT_ANSWER || question?.question?.type == QuestionTypes.WRITTEN) {
                        OutlinedTextField(
                            value = textAnswers[question.question.id].orEmpty(),
                            onValueChange = { value ->
                                savedTextAnswers = textAnswers.toMutableMap().apply { put(question.question.id, value) }
                                    .entries.joinToString(";") { "${it.key}=${it.value}" }
                            },
                            label = { Text(stringResource(R.string.quiz_answer_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    question?.options?.forEach { option ->
                        val selected = option.id in answers[question.question.id].orEmpty()
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                if (question.question.type == QuestionTypes.MULTIPLE_SELECT) {
                                    Checkbox(selected, onCheckedChange = { checked ->
                                        val next = answers[question.question.id].orEmpty().toMutableSet().apply { if (checked) add(option.id) else remove(option.id) }
                                        savedAnswers = answers.toMutableMap().apply { put(question.question.id, next.toList()) }.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }
                                    })
                                } else {
                                    RadioButton(selected, onClick = {
                                        savedAnswers = answers.toMutableMap().apply { put(question.question.id, listOf(option.id)) }.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }
                                    })
                                }
                                Column {
                                    Text(option.text)
                                    option.textBn?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { index = (index - 1).coerceAtLeast(0) }, enabled = index > 0) { Text(stringResource(R.string.previous)) }
                        OutlinedButton(onClick = { index = (index + 1).coerceAtMost(attempt.questions.lastIndex) }, enabled = index < attempt.questions.lastIndex) { Text(stringResource(R.string.next)) }
                    }
                    Text("${(answers.keys + textAnswers.keys).distinct().size} ${stringResource(R.string.quiz_answered_count)}", style = MaterialTheme.typography.labelMedium)
                    Button(onClick = { confirmSubmit = true }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.submitting) stringResource(R.string.quiz_submitting) else stringResource(R.string.quiz_submit))
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmSubmit) AlertDialog(
        onDismissRequest = { confirmSubmit = false },
        title = { Text(stringResource(R.string.quiz_confirm_submit_title)) },
        text = { Text(stringResource(R.string.quiz_confirm_submit_body)) },
        confirmButton = { TextButton(onClick = {
            confirmSubmit = false
            val a = state.attempt
            if (a != null) vm.submit(a.questions.map { q -> QuizAnswerRequest(q.question.id, answers[q.question.id].orEmpty(), textAnswers[q.question.id]) })
        }) { Text(stringResource(R.string.quiz_submit)) } },
        dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
