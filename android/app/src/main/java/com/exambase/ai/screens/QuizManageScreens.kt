package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.exambase.ai.R
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.quizzes.QuizEditorViewModel
import com.exambase.ai.quizzes.QuizQuestionsViewModel
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.QuizStatus

@Composable
fun QuizEditorScreen(
    quizSetId: String?,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm = appViewModel { QuizEditorViewModel(quizSetId, it.quizRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    LaunchedEffect(s.savedId) { s.savedId?.let(onSaved) }

    when {
        s.loading -> LoadingView()
        s.error != null && s.title.isEmpty() -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = { /* reload via new VM */ onBack() })
        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(if (quizSetId == null) R.string.quiz_create_title else R.string.quiz_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = s.title,
                onValueChange = vm::onTitle,
                label = { Text(stringResource(R.string.quiz_field_title)) },
                isError = s.fieldErrors.containsKey("title"),
                supportingText = s.fieldErrors["title"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = s.description,
                onValueChange = vm::onDescription,
                label = { Text(stringResource(R.string.quiz_field_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text(stringResource(R.string.quiz_field_status), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(QuizStatus.DRAFT, QuizStatus.PRIVATE, QuizStatus.ARCHIVED).forEach { st ->
                    FilterChip(
                        selected = s.status == st,
                        onClick = { vm.onStatus(st) },
                        label = { Text(statusLabel(st)) },
                    )
                }
            }
            OutlinedTextField(
                value = s.timeLimitSeconds,
                onValueChange = vm::onTimeLimit,
                label = { Text(stringResource(R.string.quiz_field_time_limit)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = s.passingPercent,
                onValueChange = vm::onPassingPercent,
                label = { Text(stringResource(R.string.quiz_field_passing)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = s.maxAttempts,
                onValueChange = vm::onMaxAttempts,
                label = { Text(stringResource(R.string.quiz_field_max_attempts)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.shuffleQuestions, onCheckedChange = vm::onShuffleQuestions)
                Text(stringResource(R.string.quiz_field_shuffle_questions))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.shuffleOptions, onCheckedChange = vm::onShuffleOptions)
                Text(stringResource(R.string.quiz_field_shuffle_options))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.showExplanations, onCheckedChange = vm::onShowExplanations)
                Text(stringResource(R.string.quiz_field_show_explanations))
            }
            if (s.error != null) {
                Text(s.error!!, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = vm::save,
                enabled = !s.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (s.saving) R.string.saving else R.string.save))
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    QuizStatus.DRAFT -> stringResource(R.string.quiz_status_draft)
    QuizStatus.PRIVATE -> stringResource(R.string.quiz_status_private)
    QuizStatus.ARCHIVED -> stringResource(R.string.quiz_status_archived)
    else -> status
}

@Composable
fun QuizQuestionsScreen(
    quizSetId: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel {
        QuizQuestionsViewModel(quizSetId, it.quizRepository, it.questionRepository)
    }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    LaunchedEffect(s.saved) { if (s.saved) onBack() }

    Column(Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(
            stringResource(R.string.quiz_manage_questions_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.quiz_questions_selected_count, s.selectedIds.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::onQuery,
            label = { Text(stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TextButton(onClick = vm::searchBank) { Text(stringResource(R.string.search)) }
        if (s.loading) {
            LoadingView()
        } else if (s.error != null) {
            ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = vm::searchBank)
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    Text(stringResource(R.string.quiz_selected_questions), style = MaterialTheme.typography.titleSmall)
                }
                items(s.selectedIds, key = { it }) { id ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(Spacing.sm).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(id.take(8) + "…", modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.moveUp(id) }) { Text("↑") }
                            TextButton(onClick = { vm.moveDown(id) }) { Text("↓") }
                            TextButton(onClick = { vm.remove(id) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.quiz_bank_questions), style = MaterialTheme.typography.titleSmall)
                }
                items(s.bankItems, key = { it.id }) { q ->
                    val already = s.selectedIds.contains(q.id)
                    Card(
                        onClick = { if (!already) vm.add(q.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Spacing.sm)) {
                            Text(q.text.take(120), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (already) stringResource(R.string.quiz_already_added) else q.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = vm::save,
            enabled = !s.saving,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) {
            Text(stringResource(if (s.saving) R.string.saving else R.string.save))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
    }
}
