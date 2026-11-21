package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.exambase.ai.R
import com.exambase.ai.academic.LoadState
import com.exambase.ai.academic.currentLanguage
import com.exambase.ai.academic.localized
import com.exambase.ai.academic.pickText
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.Crumb
import com.exambase.core.network.LessonSummary
import com.exambase.core.network.ProgressStatus

/** Common frame for browsing screens: back button, title, then content filling the rest of the screen. */
@Composable
fun ContentScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                LinkButton(stringResource(R.string.back), onBack)
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            content()
        }
    }
}

/** Shows loading / error with retry / sign-out handling, and [content] once data is ready. */
@Composable
fun <T> LoadStateHost(state: LoadState<T>, onRetry: () -> Unit, onSignedOut: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> LoadingView()
        LoadState.SignedOut -> {
            LaunchedEffect(Unit) { onSignedOut() }
            LoadingView()
        }
        is LoadState.Error -> ErrorView(
            message = if (state.error.network) stringResource(R.string.error_network) else (state.error.message ?: stringResource(R.string.error_generic)),
            retryLabel = stringResource(R.string.retry),
            onRetry = onRetry,
        )
        is LoadState.Ready -> content(state.data)
    }
}

/**
 * Calls [onRefresh] when this screen comes back from a screen opened on top of it (not on first display, where the
 * view model has just loaded). The "first" flag is saved with the back stack entry, so it survives the round trip.
 */
@Composable
fun RefreshOnReturn(onRefresh: () -> Unit) {
    var first by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { if (first) first = false else onRefresh() }
}

@Composable
fun NodeCard(title: String, subtitle: String, meta: String?, onClick: () -> Unit, badge: String? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta != null) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun progressLabel(progress: String): String = stringResource(
    when (progress) {
        ProgressStatus.COMPLETED -> R.string.progress_completed
        ProgressStatus.IN_PROGRESS -> R.string.progress_in_progress
        else -> R.string.progress_not_started
    },
)

@Composable
fun LessonCard(lesson: LessonSummary, onClick: () -> Unit) {
    val minutes = lesson.estimatedMinutes?.let { stringResource(R.string.meta_minutes, it) }
    val meta = listOfNotNull(minutes, progressLabel(lesson.progress), if (lesson.bookmarked) stringResource(R.string.bookmark_remove) else null)
        .joinToString("  ·  ")
    NodeCard(
        title = localized(lesson.title, lesson.titleBn),
        subtitle = localized(lesson.summary, lesson.summaryBn),
        meta = meta,
        onClick = onClick,
    )
}

@Composable
fun Breadcrumbs(crumbs: List<Crumb>) {
    if (crumbs.isEmpty()) return
    val language = currentLanguage()
    Text(
        crumbs.joinToString("  ›  ") { pickText(it.title, it.titleBn, language) },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
