package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.exambase.ai.questions.PracticeHistoryViewModel
import com.exambase.ai.questions.PracticeRules
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.PracticeAttemptDto

@Composable
fun PracticeHistoryScreen(
    lessonId: String? = null,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { PracticeHistoryViewModel(lessonId, it.questionRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }

    ContentScaffold(stringResource(R.string.practice_history_title), onBack) {
        when {
            s.loading -> LoadingView()
            s.error != null && s.items.isEmpty() -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = vm::reload)
            s.items.isEmpty() -> EmptyView(
                title = stringResource(R.string.practice_history_empty_title),
                body = stringResource(R.string.practice_history_empty_body),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(s.items, key = { it.attemptId }) { item ->
                    PracticeAttemptCard(item)
                }
                if (s.nextCursor != null) {
                    item {
                        if (s.loadingMore) {
                            CircularProgressIndicator(Modifier.padding(Spacing.md))
                        } else {
                            LinkButton(stringResource(R.string.load_more), onClick = vm::loadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeAttemptCard(item: PracticeAttemptDto) {
    val resultLabel = when (item.result) {
        "correct" -> stringResource(R.string.practice_result_correct)
        "incorrect" -> stringResource(R.string.practice_result_incorrect)
        "unanswered" -> stringResource(R.string.practice_result_unanswered)
        else -> stringResource(R.string.practice_result_ungraded)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.text.take(160), style = MaterialTheme.typography.bodyMedium)
            Text(
                "$resultLabel · ${PracticeRules.formatAwarded(item.awardedMarks)} · ${item.createdAt.take(10)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
