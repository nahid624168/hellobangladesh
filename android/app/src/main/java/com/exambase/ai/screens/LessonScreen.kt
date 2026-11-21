package com.exambase.ai.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.academic.LessonViewModel
import com.exambase.ai.academic.LoadState
import com.exambase.ai.academic.localized
import com.exambase.ai.questions.LessonPracticeViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.AttachmentDto
import com.exambase.core.network.LessonDetail
import com.exambase.core.network.PracticeSummary
import com.exambase.core.network.ProgressStatus

/** Attachment links come from the server; only web links are opened, never other schemes (file:, intent:, ...). */
internal fun isSafeWebUrl(url: String): Boolean {
    val scheme = runCatching { java.net.URI(url.trim()).scheme }.getOrNull()?.lowercase()
    return scheme == "https" || scheme == "http"
}

internal fun openUrl(context: Context, url: String) {
    if (!isSafeWebUrl(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app can open this link; nothing useful to show.
    }
}

@Composable
fun LessonScreen(id: String, onBack: () -> Unit, onOpenLesson: (String) -> Unit, onPractice: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { LessonViewModel(id, it.academicRepository) }
    val practiceVm = appViewModel { LessonPracticeViewModel(id, it.questionRepository) }
    val practiceState by practiceVm.state.collectAsState()
    // Coming back from practice updates "3 of 10 tried" without a spinner.
    RefreshOnReturn(practiceVm::refresh)
    val state by vm.state.collectAsState()
    val actionFailed by vm.actionFailed.collectAsState()
    val title = (state as? LoadState.Ready)?.data?.lesson?.let { localized(it.title, it.titleBn) } ?: ""

    ContentScaffold(title, onBack) {
        LoadStateHost(state, vm::load, onSignedOut) { detail ->
            LessonBody(
                detail = detail,
                practice = (practiceState as? LoadState.Ready)?.data,
                actionFailed = actionFailed,
                onToggleBookmark = vm::toggleBookmark,
                onToggleCompleted = { vm.setCompleted(detail.lesson.progress != ProgressStatus.COMPLETED) },
                onOpenLesson = onOpenLesson,
                onPractice = { onPractice(id) },
            )
        }
    }
}

@Composable
private fun LessonBody(
    detail: LessonDetail,
    practice: PracticeSummary?,
    actionFailed: Boolean,
    onToggleBookmark: () -> Unit,
    onToggleCompleted: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onPractice: () -> Unit,
) {
    val context = LocalContext.current
    val lesson = detail.lesson
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Breadcrumbs(detail.breadcrumb)
        Text(localized(lesson.title, lesson.titleBn), style = MaterialTheme.typography.headlineSmall)
        val minutes = lesson.estimatedMinutes?.let { stringResource(R.string.meta_minutes, it) }
        Text(
            listOfNotNull(minutes, progressLabel(lesson.progress)).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                text = stringResource(if (lesson.bookmarked) R.string.bookmark_remove else R.string.bookmark_add),
                onClick = onToggleBookmark,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(if (lesson.progress == ProgressStatus.COMPLETED) R.string.mark_incomplete else R.string.mark_complete),
                onClick = onToggleCompleted,
                modifier = Modifier.weight(1f),
            )
        }
        if (actionFailed) Text(stringResource(R.string.action_failed), color = MaterialTheme.colorScheme.error)

        if (detail.learningObjectives.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.lesson_objectives), style = MaterialTheme.typography.titleMedium)
                    detail.learningObjectives.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        // Shown only when the lesson has published questions; a failed summary load simply hides this card.
        if (practice != null && practice.totalQuestions > 0) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.practice_lesson_card), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.practice_lesson_summary, practice.attemptedQuestions, practice.totalQuestions, practice.correctQuestions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(stringResource(R.string.practice_start), onPractice, Modifier.fillMaxWidth())
                }
            }
        }

        MarkdownText(localized(detail.content, detail.contentBn))

        if (detail.attachments.isNotEmpty()) {
            Text(stringResource(R.string.lesson_attachments), style = MaterialTheme.typography.titleMedium)
            detail.attachments.forEach { attachment -> AttachmentRow(attachment) { openUrl(context, attachment.url) } }
        }

        // Properties of a class from another module cannot be smart-cast, so copy them first.
        val previousId = detail.previousLessonId
        val nextId = detail.nextLessonId
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (previousId != null) LinkButton(stringResource(R.string.prev_lesson), { onOpenLesson(previousId) }) else Spacer(Modifier)
            if (nextId != null) LinkButton(stringResource(R.string.next_lesson), { onOpenLesson(nextId) })
        }
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentDto, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                Text(attachment.title, style = MaterialTheme.typography.bodyLarge)
                Text(attachment.kind.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSafeWebUrl(attachment.url)) LinkButton(stringResource(R.string.open_attachment), onOpen)
        }
    }
}
