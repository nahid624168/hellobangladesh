package com.exambase.ai.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.exambase.ai.quizzes.QuizDetailData
import com.exambase.ai.quizzes.QuizDetailViewModel
import com.exambase.ai.quizzes.QuizListViewModel
import com.exambase.ai.quizzes.QuizRules
import com.exambase.ai.quizzes.Startability
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.AttemptStatus
import com.exambase.core.network.QuizAttemptSummary
import com.exambase.core.network.QuizSetListItem
import com.exambase.core.network.QuizSort
import com.exambase.core.network.QuizStatus

/** Quiz lis/** Quiz list opened from Home (My Quiz Sets). Each quiz set is its own card; sets are never merged with flashcard sets. */
@Composable
fun QuizListScreen(
    onBack: () -> Unit,
    onOpenQuiz: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { QuizListViewModel(it.quizRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    RefreshOnReturn(vm::refreshQuietly)

    ContentScaffold(stringResource(R.string.my_quiz_sets), onBack) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCreate) { Text(stringResource(R.string.quiz_create)) }
            }
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQueryChange,
                label = { Text(stringResource(R.string.quiz_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                singleLine = true,
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = s.statusFilter == null,
                    onClick = { vm.onStatusFilter(null) },
                    label = { Text(stringResource(R.string.quiz_filter_all)) },
                )
                FilterChip(
                    selected = s.statusFilter == QuizStatus.DRAFT,
                    onClick = { vm.onStatusFilter(QuizStatus.DRAFT) },
                    label = { Text(stringResource(R.string.quiz_filter_draft)) },
                )
                FilterChip(
                    selected = s.statusFilter == QuizStatus.PRIVATE,
                    onClick = { vm.onStatusFilter(QuizStatus.PRIVATE) },
                    label = { Text(stringResource(R.string.quiz_filter_private)) },
                )
                FilterChip(
                    selected = s.statusFilter == QuizStatus.ARCHIVED,
                    onClick = { vm.onStatusFilter(QuizStatus.ARCHIVED) },
                    label = { Text(stringResource(R.string.quiz_filter_archived)) },
                )
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    QuizSort.RECENT to R.string.sort_recent,
                    QuizSort.TITLE to R.string.sort_title,
                    QuizSort.PLAYED to R.string.sort_played,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = s.sort == value,
                        onClick = { vm.onSortChange(value) },
                        label = { Text(stringResource(label)) },
                    )
                }
                androidx.compose.material3.TextButton(onClick = vm::submitSearch) {
                    Text(stringResource(R.string.search))
                }
            }
            if (s.actionError != null) {
                Text(
                    s.actionError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
            when {
                s.loading -> LoadingView()
                s.error != null -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!.message ?: "Something went wrong", onRetry = vm::reload)
                s.list.items.isEmpty() -> EmptyView(
                    title = stringResource(R.string.quizzes_empty_title),
                    body = stringResource(R.string.quizzes_empty_body),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(s.list.items, key = { it.quizSet.id }) { item ->
                        QuizSetCard(
                            item = item,
                            onOpen = { onOpenQuiz(item.quizSet.id) },
                            onEdit = { onEdit(item.quizSet.id) },
                            onDuplicate = { vm.duplicateSet(item.quizSet.id) { onOpenQuiz(it) } },
                            onArchive = { vm.archiveSet(item.quizSet.id) },
                            onDelete = { vm.deleteSet(item.quizSet.id) },
                        )
                    }
                    if (s.list.nextCursor != null) {
                        item {
                            if (s.loadingMore) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                LinkButton(
                                    text = stringResource(R.string.load_more),
                                    onClick = vm::loadMore,
                                )
                            }
                        }
                    }
                    if (s.loadMoreFailed) {
                        item {
                            LinkButton(text = stringResource(R.string.retry), onClick = vm::loadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizSetCard(
    item: QuizSetListItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val qs = item.quizSet
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(qs.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (qs.status != QuizStatus.PRIVATE) {
                    Text(
                        when (qs.status) {
                            QuizStatus.DRAFT -> stringResource(R.string.quiz_status_draft)
                            QuizStatus.ARCHIVED -> stringResource(R.string.quiz_status_archived)
                            else -> qs.status
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                buildString {
                    append(stringResource(R.string.quiz_meta_questions, qs.questionCount))
                    append(" · ")
                    append(stringResource(R.string.quiz_meta_attempts, item.attemptCount))
                    item.bestPercent?.let {
                        append(" · ")
                        append(stringResource(R.string.quiz_meta_best, QuizRules.formatPercent(it)))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.quiz_edit)) }
                TextButton(onClick = onDuplicate) { Text(stringResource(R.string.quiz_duplicate)) }
                if (qs.status != QuizStatus.ARCHIVED) {
                    TextButton(onClick = onArchive) { Text(stringResource(R.string.quiz_archive)) }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.quiz_delete)) }
            }
        }
    }
}

ions: what the quiz is, its rules, why it cannot be started (if so), and the recent attempts. */
@Composable
fun QuizDetailScreen(id: String, onBack: () -> Unit, onSignedOut: () -> Unit, onStart: (String) -> Unit, onHistory: (String) -> Unit, onOpenResult: (String) -> Unit, onEdit: (String) -> Unit = {}, onManageQuestions: (String) -> Unit = {}) {
    val vm = appViewModel { QuizDetailViewModel(id, it.quizRepository) }
    val state by vm.state.collectAsState()
    RefreshOnReturn(vm::refresh)
    ContentScaffold(stringResource(R.string.quiz_detail_title), onBack) {
        LoadStateHost(state, onRetry = vm::load, onSignedOut = onSignedOut) { data -> QuizDetailBody(data, onStart, onHistory, onOpenResult, onEdit, onManageQuestions) }
    }
}

@Composable
private fun QuizDetailBody(data: QuizDetailData, onStart: (String) -> Unit, onHistory: (String) -> Unit, onOpenResult: (String) -> Unit, onEdit: (String) -> Unit, onManageQuestions: (String) -> Unit) {
    val set = data.set
    val settings = set.settings
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        statusLabel(set.status)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        Text(set.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        set.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.quiz_rules_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                RuleRow(stringResource(R.string.quiz_rule_questions), set.questionCount.toString())
                RuleRow(
                    stringResource(R.string.quiz_rule_time),
                    settings.timeLimitSeconds?.let { QuizRules.formatClock(it.toLong()) } ?: stringResource(R.string.quiz_no_limit),
                )
                RuleRow(
                    stringResource(R.string.quiz_rule_pass),
                    settings.passingPercent?.let { "$it%" } ?: stringResource(R.string.quiz_none_set),
                )
                RuleRow(
                    stringResource(R.string.quiz_rule_attempts),
                    settings.maxAttempts?.let { stringResource(R.string.quiz_attempts_used, data.finishedCount, it) } ?: stringResource(R.string.quiz_unlimited),
                )
                RuleRow(stringResource(R.string.quiz_rule_shuffle_questions), yesNo(settings.shuffleQuestions))
                RuleRow(stringResource(R.string.quiz_rule_shuffle_options), yesNo(settings.shuffleOptions))
                RuleRow(
                    stringResource(R.string.quiz_rule_explanations),
                    stringResource(if (settings.showExplanations) R.string.quiz_explanations_after else R.string.quiz_explanations_hidden),
                )
                data.bestPercent?.let { RuleRow(stringResource(R.string.quiz_rule_best), QuizRules.formatPercent(it)) }
            }
        }

        Text(stringResource(R.string.quiz_instructions_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        startabilityMessage(data.startability)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
        if (data.startability == Startability.RESUME) {
            Text(stringResource(R.string.quiz_resume_note), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (data.startability == Startability.READY || data.startability == Startability.RESUME) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { onEdit(set.id) }) { Text(stringResource(R.string.quiz_edit)) }
                TextButton(onClick = { onManageQuestions(set.id) }) { Text(stringResource(R.string.quiz_manage_questions)) }
            }
                        androidx.compose.material3.Button(onClick = { onStart(set.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (data.startability == Startability.RESUME) R.string.quiz_resume else R.string.quiz_start))
            }
        }

        Text(stringResource(R.string.quiz_recent_attempts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (data.attempts.isEmpty()) {
            Text(stringResource(R.string.quiz_no_attempts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            data.attempts.take(RECENT_SHOWN).forEach { attempt -> AttemptRow(attempt) { if (attempt.status != AttemptStatus.IN_PROGRESS) onOpenResult(attempt.attemptId) } }
            androidx.compose.material3.TextButton(onClick = { onHistory(set.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.quiz_view_history)) }
            if (data.attempts.size > RECENT_SHOWN) {
                Text(
                    stringResource(R.string.quiz_attempts_more, data.attempts.size - RECENT_SHOWN),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val RECENT_SHOWN = 5

@Composable
private fun yesNo(value: Boolean) = stringResource(if (value) R.string.yes else R.string.no)

@Composable
private fun startabilityMessage(startability: Startability): String? = when (startability) {
    Startability.DRAFT -> stringResource(R.string.quiz_blocked_draft)
    Startability.ARCHIVED -> stringResource(R.string.quiz_blocked_archived)
    Startability.EMPTY -> stringResource(R.string.quiz_blocked_empty)
    Startability.LIMIT_REACHED -> stringResource(R.string.quiz_blocked_limit)
    Startability.READY, Startability.RESUME -> null
}

@Composable
private fun RuleRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One finished or running attempt. Opening the result from here arrives with the result screens (Step 16, part 3). */
@Composable
private fun AttemptRow(attempt: QuizAttemptSummary, onClick: () -> Unit) {
    val status = when (attempt.status) {
        AttemptStatus.IN_PROGRESS -> stringResource(R.string.attempt_in_progress)
        AttemptStatus.EXPIRED -> stringResource(R.string.attempt_expired)
        else -> attempt.percent?.let { QuizRules.formatPercent(it) } ?: stringResource(R.string.attempt_submitted)
    }
    val passed = when (attempt.passed) {
        true -> stringResource(R.string.attempt_passed)
        false -> stringResource(R.string.attempt_not_passed)
        null -> null
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(QuizRules.shortDate(attempt.startedAt), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(listOfNotNull(status, passed).joinToString("  ·  "), style = MaterialTheme.typography.titleSmall)
        }
    }
}
