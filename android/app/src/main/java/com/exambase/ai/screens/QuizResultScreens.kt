package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.quizzes.QuizHistoryViewModel
import com.exambase.ai.quizzes.QuizResultViewModel
import com.exambase.ai.quizzes.QuizRules
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.AttemptStatus
import com.exambase.core.network.QuizAnswerResult

@Composable
fun QuizResultScreen(attemptId: String, onBack: () -> Unit, onSignedOut: () -> Unit, onRetryQuiz: (String) -> Unit) {
    val vm = appViewModel { QuizResultViewModel(attemptId, it.quizRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    ContentScaffold(stringResource(R.string.quiz_result_title), onBack) {
        when {
            state.loading -> Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            state.attempt?.result != null -> {
                val attempt = state.attempt!!
                val result = attempt.result!!
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(if (attempt.status == AttemptStatus.EXPIRED) stringResource(R.string.quiz_expired_result) else stringResource(R.string.quiz_result_title), style = MaterialTheme.typography.titleLarge)
                                Text("${result.score} / ${result.maxScore}", style = MaterialTheme.typography.headlineMedium)
                                Text(QuizRules.formatPercent(result.percent), style = MaterialTheme.typography.headlineSmall)
                                result.passed?.let { Text(if (it) stringResource(R.string.quiz_passed) else stringResource(R.string.quiz_not_passed)) }
                                Text(stringResource(R.string.quiz_review_policy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { onRetryQuiz(result.quizSetId) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.quiz_retry)) }
                            }
                        }
                    }
                    items(result.questions.sortedBy { it.position }, key = { it.questionId }) { q ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(stringResource(R.string.quiz_question_number, q.position), style = MaterialTheme.typography.labelLarge)
                                Text(q.text, style = MaterialTheme.typography.titleMedium)
                                q.textBn?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                val status = when (q.result) {
                                    QuizAnswerResult.CORRECT -> stringResource(R.string.quiz_answer_correct)
                                    QuizAnswerResult.INCORRECT -> stringResource(R.string.quiz_answer_incorrect)
                                    QuizAnswerResult.UNANSWERED -> stringResource(R.string.quiz_answer_unanswered)
                                    else -> stringResource(R.string.quiz_answer_ungraded)
                                }
                                Text("$status · ${q.awardedMarks}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                if (q.selectedOptionIds.isNotEmpty()) Text(stringResource(R.string.quiz_your_answer, q.selectedOptionIds.joinToString()), style = MaterialTheme.typography.bodySmall)
                                q.answerText?.let { Text(stringResource(R.string.quiz_your_answer, it), style = MaterialTheme.typography.bodySmall) }
                                q.review?.let { review ->
                                    if (review.correctOptionIds.isNotEmpty()) Text(stringResource(R.string.quiz_correct_options, review.correctOptionIds.joinToString()), style = MaterialTheme.typography.bodySmall)
                                    if (review.acceptedAnswers.isNotEmpty()) Text(stringResource(R.string.quiz_accepted_answers, review.acceptedAnswers.joinToString()), style = MaterialTheme.typography.bodySmall)
                                    val explanation = review.explanationBn?.takeIf { com.exambase.ai.academic.currentLanguage() == "bn" && it.isNotBlank() } ?: review.explanation
                                    if (explanation.isNotBlank()) Text(explanation, style = MaterialTheme.typography.bodyMedium)
                                } ?: Text(stringResource(R.string.quiz_explanation_hidden), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            else -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(state.error ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::reload) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
fun QuizHistoryScreen(quizSetId: String, onBack: () -> Unit, onSignedOut: () -> Unit, onOpenResult: (String) -> Unit) {
    val vm = appViewModel { QuizHistoryViewModel(quizSetId, it.quizRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    ContentScaffold(stringResource(R.string.quiz_history_title), onBack) {
        when {
            state.loading -> Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(state.error ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::reload) { Text(stringResource(R.string.retry)) }
            }
            state.items.isEmpty() -> Text(stringResource(R.string.quiz_no_attempts), modifier = Modifier.padding(Spacing.md))
            else -> LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.items, key = { it.attemptId }) { item ->
                    Card(onClick = { if (item.status != AttemptStatus.IN_PROGRESS) onOpenResult(item.attemptId) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(QuizRules.shortDate(item.startedAt), style = MaterialTheme.typography.titleSmall)
                                Text(when (item.status) {
                                    AttemptStatus.IN_PROGRESS -> stringResource(R.string.attempt_in_progress)
                                    AttemptStatus.EXPIRED -> stringResource(R.string.attempt_expired)
                                    else -> stringResource(R.string.attempt_submitted)
                                }, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(item.percent?.let { QuizRules.formatPercent(it) } ?: "—", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (state.nextCursor != null) item {
                    OutlinedButton(onClick = vm::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                        if (state.loadingMore) CircularProgressIndicator() else Text(stringResource(R.string.load_more))
                    }
                }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}
