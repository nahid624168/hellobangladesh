package com.exambase.ai.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import com.exambase.ai.auth.HomeState
import com.exambase.ai.auth.HomeViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing

/** Step 29 home dashboard: greeting, search, streak/goal, continue, shortcuts, empty/offline states. */
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    onBrowse: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenFlashcards: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenPracticeHistory: () -> Unit = {},
    onOpenContests: () -> Unit = {},
    onOpenExams: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenAiContent: () -> Unit = {},
    onOpenAiTutor: () -> Unit = {},
    onOpenLesson: (String) -> Unit = {},
    onOpenSubject: (String) -> Unit = {},
    onVerifyEmail: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val homeVm = appViewModel { c ->
        HomeViewModel(
            c.authRepository,
            dashboardLoader = { c.dashboardRepository.dashboard() },
        )
    }
    val state by homeVm.state.collectAsState()

    LaunchedEffect(state) { if (state is HomeState.SignedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        when (val s = state) {
            HomeState.Loading, HomeState.SignedOut -> LoadingView()
            is HomeState.Error -> ErrorView(
                message = if (s.network) stringResource(R.string.error_network) else (s.message ?: stringResource(R.string.error_generic)),
                retryLabel = stringResource(R.string.retry),
                onRetry = homeVm::load,
            )
            is HomeState.Ready -> Column(
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    stringResource(R.string.home_greeting_named, s.dashboard?.greetingName ?: s.user.displayName),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (s.offline) {
                    Text(
                        stringResource(R.string.home_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.search_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }

                Card(onClick = onOpenAi, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.home_ai),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
                Card(onClick = onOpenAiContent, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_ai_content), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenAiTutor, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_ai_tutor), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }

                val dash = s.dashboard
                if (dash != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.home_streak, dash.currentStreakDays))
                            Text(stringResource(R.string.home_daily_goal, dash.dailyGoalProgress, dash.dailyGoalTarget))
                            Text(stringResource(R.string.home_points, dash.totalPoints))
                        }
                    }
                    dash.banners.forEach { b ->
                        Card(
                            onClick = {
                                when (b.action) {
                                    "verify_email" -> onVerifyEmail()
                                    "analytics" -> onOpenAnalytics()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(Spacing.md)) {
                                Text(b.title, style = MaterialTheme.typography.titleMedium)
                                if (b.body.isNotBlank()) Text(b.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (dash.continueLearning.isNotEmpty()) {
                        Text(stringResource(R.string.home_continue), style = MaterialTheme.typography.titleSmall)
                        dash.continueLearning.forEach { item ->
                            Card(
                                Modifier.fillMaxWidth().clickable {
                                    if (item.type == "lesson") onOpenLesson(item.id)
                                },
                            ) {
                                Column(Modifier.padding(Spacing.md)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                    if (dash.subjectShortcuts.isNotEmpty()) {
                        Text(stringResource(R.string.home_subjects), style = MaterialTheme.typography.titleSmall)
                        dash.subjectShortcuts.forEach { sub ->
                            Card(Modifier.fillMaxWidth().clickable { onOpenSubject(sub.id) }) {
                                Text(sub.title, Modifier.padding(Spacing.md), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (dash.isNewUser) {
                        Text(
                            stringResource(R.string.home_new_user),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (!s.user.emailVerified) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(stringResource(R.string.email_not_verified), style = MaterialTheme.typography.titleMedium)
                            LinkButton(stringResource(R.string.verify_title), onVerifyEmail)
                        }
                    }
                }

                Card(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.browse_subjects), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenQuizzes, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_quiz_sets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenFlashcards, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_flashcard_sets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenNotes, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenContests, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.contests_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenExams, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exams_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenAnalytics, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                Card(onClick = onOpenPracticeHistory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.practice_history_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md))
                }
                LinkButton(stringResource(R.string.profile), onOpenProfile)
            }
        }
    }
}
