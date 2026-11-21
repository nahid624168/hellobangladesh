package com.exambase.ai.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.exambase.ai.R
import com.exambase.ai.academic.localized
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.questions.PracticeRules
import com.exambase.ai.questions.PracticeStep
import com.exambase.ai.questions.PracticeViewModel
import com.exambase.ai.questions.Problem
import com.exambase.ai.questions.Tally
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.CheckAnswerResponse
import com.exambase.core.network.CheckResults
import com.exambase.core.network.QuestionDetail
import com.exambase.core.network.QuestionImageDto
import com.exambase.core.network.QuestionTypes

@Composable
fun PracticeScreen(lessonId: String, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { PracticeViewModel(lessonId, it.questionRepository) }
    val ui by vm.state.collectAsState()
    ContentScaffold(stringResource(R.string.practice_title), onBack) {
        when (val step = ui.step) {
            PracticeStep.Loading -> LoadingView()
            PracticeStep.SignedOut -> {
                LaunchedEffect(Unit) { onSignedOut() }
                LoadingView()
            }
            PracticeStep.Empty -> EmptyView(
                title = stringResource(R.string.practice_empty_title),
                message = stringResource(R.string.practice_empty_body),
                actionLabel = stringResource(R.string.back_to_lesson),
                onAction = onBack,
            )
            is PracticeStep.Error -> ErrorView(
                message = if (step.error.network) stringResource(R.string.error_network) else (step.error.message ?: stringResource(R.string.error_generic)),
                retryLabel = stringResource(R.string.retry),
                onRetry = vm::retry,
            )
            is PracticeStep.Asking -> QuestionPage(
                position = ui.position, total = ui.total, detail = step.detail, selected = step.selected, text = step.text,
                submitting = step.submitting, problem = step.problem, feedback = null, isLast = false,
                onSelect = vm::select, onText = vm::onText, onSubmit = vm::submit, onNext = vm::next,
            )
            is PracticeStep.Feedback -> QuestionPage(
                position = ui.position, total = ui.total, detail = step.detail, selected = step.selected, text = step.text,
                submitting = false, problem = null, feedback = step.response, isLast = step.isLast,
                onSelect = vm::select, onText = vm::onText, onSubmit = vm::submit, onNext = vm::next,
            )
            is PracticeStep.Done -> DoneBody(step.tally, onRestart = vm::restart, onBack = onBack)
        }
    }
}

@Composable
private fun QuestionPage(
    position: Int,
    total: Int,
    detail: QuestionDetail,
    selected: Set<String>,
    text: String,
    submitting: Boolean,
    problem: Problem?,
    feedback: CheckAnswerResponse?,
    isLast: Boolean,
    onSelect: (String) -> Unit,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val q = detail.question
    val checked = feedback != null
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.practice_position, position, total), style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = if (total == 0) 0f else position.toFloat() / total, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.practice_meta, difficultyLabel(q.difficulty), PracticeRules.formatMarks(q.marks), PracticeRules.formatMarks(q.negativeMarks)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(localized(q.text, q.textBn), style = MaterialTheme.typography.titleMedium)
        QuestionImages(detail.images)

        if (PracticeRules.isChoice(q.type)) {
            val single = PracticeRules.isSingle(q.type)
            Text(
                stringResource(if (single) R.string.hint_single else R.string.hint_multiple),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.options.forEach { option ->
                val chosen = option.id in selected
                val isCorrect = feedback?.correctOptionIds?.contains(option.id) == true
                val note = when {
                    !checked -> null
                    chosen && isCorrect -> stringResource(R.string.label_your_correct)
                    isCorrect -> stringResource(R.string.label_correct_option)
                    chosen -> stringResource(R.string.label_your_choice)
                    else -> null
                }
                OptionRow(localized(option.text, option.textBn), chosen, single, enabled = !checked && !submitting, note = note) { onSelect(option.id) }
            }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                enabled = !checked && !submitting,
                label = { Text(stringResource(if (q.type == QuestionTypes.SHORT_ANSWER) R.string.answer_hint_short else R.string.answer_hint_written)) },
                singleLine = q.type == QuestionTypes.SHORT_ANSWER,
                minLines = if (q.type == QuestionTypes.WRITTEN) 4 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (problem) {
            Problem.Network -> Text(stringResource(R.string.error_network), color = MaterialTheme.colorScheme.error)
            is Problem.Message -> Text(problem.text, color = MaterialTheme.colorScheme.error)
            null -> Unit
        }

        if (feedback == null) {
            PrimaryButton(
                text = stringResource(R.string.check_answer),
                onClick = onSubmit,
                enabled = PracticeRules.canSubmit(q.type, selected, text),
                loading = submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FeedbackCard(feedback, context)
            PrimaryButton(
                text = stringResource(if (isLast) R.string.finish else R.string.next_question),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, single: Boolean, enabled: Boolean, note: String?, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().selectable(
            selected = selected, enabled = enabled, role = if (single) Role.RadioButton else Role.Checkbox, onClick = onClick,
        ),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (single) RadioButton(selected = selected, onClick = null, enabled = enabled)
            else Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                // The result is also written out, so it never depends on colour alone.
                if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FeedbackCard(feedback: CheckAnswerResponse, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(
                    when (feedback.result) {
                        CheckResults.CORRECT -> R.string.result_correct
                        CheckResults.INCORRECT -> R.string.result_incorrect
                        else -> R.string.result_ungraded
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (feedback.result == CheckResults.INCORRECT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (feedback.result != CheckResults.UNGRADED) {
                Text(stringResource(R.string.practice_marks, PracticeRules.formatAwarded(feedback.awardedMarks)), style = MaterialTheme.typography.labelMedium)
            }
            if (feedback.acceptedAnswers.isNotEmpty()) {
                Text(stringResource(R.string.accepted_answers, feedback.acceptedAnswers.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            }
            val explanation = localized(feedback.explanation, feedback.explanationBn)
            if (explanation.isNotBlank()) {
                Text(stringResource(R.string.explanation_title), style = MaterialTheme.typography.titleSmall)
                MarkdownText(explanation)
            }
            QuestionImages(feedback.explanationImages)
        }
    }
}

/** No image loader is in the project yet (Coil arrives with the media work), so pictures open in the browser. */
@Composable
private fun ImageLink(image: QuestionImageDto, context: Context) {
    if (!isSafeWebUrl(image.url)) return
    val alt = image.altText?.let { localized(it, image.altTextBn) }
    LinkButton(
        text = if (alt.isNullOrBlank()) stringResource(R.string.open_image) else stringResource(R.string.open_image_alt, alt),
        onClick = { openUrl(context, image.url) },
    )
}

@Composable
private fun DoneBody(tally: Tally, onRestart: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.practice_done_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.practice_done_body, tally.correct, tally.incorrect, tally.ungraded), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.practice_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(stringResource(R.string.practice_again), onRestart, Modifier.fillMaxWidth())
        SecondaryButton(stringResource(R.string.back_to_lesson), onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun difficultyLabel(difficulty: String): String = stringResource(
    when (difficulty) {
        "easy" -> R.string.difficulty_easy
        "hard" -> R.string.difficulty_hard
        else -> R.string.difficulty_medium
    },
)
