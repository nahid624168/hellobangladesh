package com.exambase.ai.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.exambase.ai.contests.ContestDetailViewModel
import com.exambase.ai.contests.ContestLeaderboardViewModel
import com.exambase.ai.contests.ContestListViewModel
import com.exambase.ai.contests.ContestPlayerViewModel
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.ContestQuestionDto

@Composable
fun ContestListScreen(
    onBack: () -> Unit,
    onOpenContest: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ContestListViewModel(it.contestRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.contests_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.contests_online_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                state.loading && state.list.items.isEmpty() -> Text(stringResource(R.string.loading))
                state.error != null && state.list.items.isEmpty() -> {
                    Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.list.items.isEmpty() -> Text(stringResource(R.string.contests_empty))
                else -> state.list.items.forEach { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenContest(c.id) },
                    ) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.contests_status_line, c.status, c.participantCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(R.string.contests_window, c.startsAt, c.endsAt),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (state.list.hasMore) {
                SecondaryButton(stringResource(R.string.load_more), onClick = vm::loadMore, modifier = Modifier.fillMaxWidth())
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}

@Composable
fun ContestDetailScreen(
    contestId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onLeaderboard: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ContestDetailViewModel(contestId, it.contestRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.contest_detail_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.contests_online_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null -> {
                    Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.detail != null -> {
                    val d = state.detail!!
                    Text(d.contest.title, style = MaterialTheme.typography.titleLarge)
                    Text(d.contest.description, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.contests_status_line, d.contest.status, d.contest.participantCount))
                    Text(stringResource(R.string.contests_duration, d.contest.durationSeconds / 60))
                    d.participant?.let {
                        Text(stringResource(R.string.contest_my_status, it.status))
                    }
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    when {
                        d.participant == null -> PrimaryButton(
                            stringResource(R.string.contest_join),
                            onClick = vm::join,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        d.participant!!.status == "joined" -> {
                            PrimaryButton(
                                stringResource(R.string.contest_start),
                                onClick = { onPlay(contestId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SecondaryButton(
                                stringResource(R.string.contest_leave),
                                onClick = vm::leave,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        d.participant!!.status == "in_progress" -> PrimaryButton(
                            stringResource(R.string.contest_continue),
                            onClick = { onPlay(contestId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        d.participant!!.status == "submitted" -> {
                            Text(stringResource(R.string.contest_score, d.participant!!.score ?: 0.0, d.participant!!.percent ?: 0.0))
                        }
                    }
                    if (d.showLeaderboard) {
                        SecondaryButton(
                            stringResource(R.string.contest_leaderboard),
                            onClick = { onLeaderboard(contestId) },
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
fun ContestPlayerScreen(
    contestId: String,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ContestPlayerViewModel(contestId, it.contestRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.result) {
        if (state.result != null) onFinished(contestId)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.contest_player_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.contests_online_only), style = MaterialTheme.typography.bodySmall)

            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null && state.questions.isEmpty() -> {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::start)
                }
                state.questions.isNotEmpty() -> {
                    val q = state.questions[state.index]
                    state.remainingMs?.let {
                        Text(stringResource(R.string.contest_time_left, it / 1000), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.contest_progress, state.index + 1, state.questions.size))
                    ContestQuestionBlock(
                        question = q,
                        selected = state.answers[q.id].orEmpty(),
                        text = state.textAnswers[q.id].orEmpty(),
                        onSelect = { opt, multi -> vm.selectOption(q.id, opt, multi) },
                        onText = { vm.setText(q.id, it); vm.saveText(q.id) },
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (state.index > 0) LinkButton(stringResource(R.string.prev_lesson), vm::prev)
                        else Spacer(Modifier)
                        if (state.index < state.questions.lastIndex) LinkButton(stringResource(R.string.next_lesson), vm::next)
                    }
                    PrimaryButton(
                        stringResource(R.string.contest_submit),
                        onClick = vm::submit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}

@Composable
private fun ContestQuestionBlock(
    question: ContestQuestionDto,
    selected: List<String>,
    text: String,
    onSelect: (String, Boolean) -> Unit,
    onText: (String) -> Unit,
) {
    val multi = question.type == "multiple_select"
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(question.questionText, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.contest_marks, question.marks), style = MaterialTheme.typography.bodySmall)
        if (question.options.isNotEmpty()) {
            question.options.forEach { opt ->
                FilterChip(
                    selected = opt.id in selected,
                    onClick = { onSelect(opt.id, multi) },
                    label = { Text(opt.text) },
                )
            }
        } else {
            ExamBaseTextField(
                value = text,
                onValueChange = onText,
                label = stringResource(R.string.contest_text_answer),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ContestLeaderboardScreen(
    contestId: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ContestLeaderboardViewModel(contestId, it.contestRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.contest_leaderboard), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.contests_online_only), style = MaterialTheme.typography.bodySmall)
            when {
                state.loading && state.page == null -> Text(stringResource(R.string.loading))
                state.error != null && state.page == null -> {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = { vm.reload() })
                }
                state.page != null -> {
                    val page = state.page!!
                    Text(stringResource(R.string.contest_leaderboard_total, page.totalSubmitted))
                    page.me?.let {
                        Text(stringResource(R.string.contest_my_rank, it.rank, it.score, it.percent))
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    page.entries.forEach { e ->
                        Text(
                            stringResource(
                                R.string.contest_leaderboard_row,
                                e.rank,
                                e.displayName,
                                e.score,
                                e.percent,
                            ),
                        )
                    }
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}
