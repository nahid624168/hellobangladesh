package com.exambase.ai.screens

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
import com.exambase.ai.analytics.AchievementsViewModel
import com.exambase.ai.analytics.AnalyticsViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun AnalyticsScreen(onBack: () -> Unit, onAchievements: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { AnalyticsViewModel(it.analyticsRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.loading && state.overview == null -> Text(stringResource(R.string.loading))
                state.error != null && state.overview == null -> {
                    Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.overview != null -> {
                    val o = state.overview!!
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.analytics_points, o.totalPoints), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.analytics_accuracy, o.accuracy ?: 0.0, o.correct, o.answered))
                            Text(stringResource(R.string.analytics_streak, o.currentStreakDays, o.longestStreakDays))
                            Text(
                                stringResource(
                                    R.string.analytics_daily_goal,
                                    o.dailyGoalProgress,
                                    o.dailyGoalTarget,
                                    if (o.dailyGoalMet) stringResource(R.string.analytics_goal_met) else "",
                                ),
                            )
                            Text(
                                stringResource(
                                    R.string.analytics_activity_counts,
                                    o.quizAttempts,
                                    o.contestSubmissions,
                                    o.flashcardReviews,
                                    o.examAttempts,
                                ),
                            )
                        }
                    }
                    state.trends?.days?.takeLast(7)?.forEach { d ->
                        Text("${d.date}: ${d.answered} ans · ${d.pointsEarned} pts")
                    }
                    if (state.subjects.isNotEmpty()) {
                        Text(stringResource(R.string.analytics_subjects), style = MaterialTheme.typography.titleSmall)
                        state.subjects.forEach { s ->
                            Text("${s.subjectId.take(8)}… ${s.accuracy ?: 0.0}% (${s.correct}/${s.answered})")
                        }
                    }
                    PrimaryButton(stringResource(R.string.achievements_title), onClick = onAchievements, modifier = Modifier.fillMaxWidth())
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}

@Composable
fun AchievementsScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { AchievementsViewModel(it.analyticsRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.achievements_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null -> {
                    Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::reload)
                }
                state.data != null -> {
                    val d = state.data!!
                    Text(stringResource(R.string.analytics_points, d.totalPoints))
                    Text(stringResource(R.string.achievements_earned, d.earnedCount, d.badges.size))
                    d.badges.forEach { b ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md)) {
                                Text(b.title, style = MaterialTheme.typography.titleMedium)
                                Text(b.description, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (b.earnedAt != null) stringResource(R.string.achievements_earned_at, b.earnedAt!!)
                                    else stringResource(R.string.achievements_locked),
                                )
                            }
                        }
                    }
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}
