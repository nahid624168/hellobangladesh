package com.exambase.ai.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.exambase.ai.academic.SubjectsViewModel
import com.exambase.ai.academic.localized
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.SubjectSort

@Composable
fun SubjectsScreen(onBack: () -> Unit, onOpenSubject: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { SubjectsViewModel(it.academicRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }

    ContentScaffold(stringResource(R.string.subjects_title), onBack) {
        ExamBaseTextField(
            value = s.query,
            onValueChange = vm::onQueryChange,
            label = stringResource(R.string.search_subjects),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(SubjectSort.ORDER to R.string.sort_default, SubjectSort.TITLE to R.string.sort_title, SubjectSort.NEWEST to R.string.sort_newest)
                .forEach { (value, label) ->
                    FilterChip(selected = s.sort == value, onClick = { vm.onSortChange(value) }, label = { Text(stringResource(label)) })
                }
            FilterChip(
                selected = s.featuredOnly,
                onClick = { vm.onFeaturedChange(!s.featuredOnly) },
                label = { Text(stringResource(R.string.featured_only)) },
            )
        }
        Box(Modifier.weight(1f)) {
            val error = s.error
            when {
                s.loading && s.list.items.isEmpty() -> LoadingView()
                error != null -> ErrorView(
                    message = if (error.network) stringResource(R.string.error_network) else (error.message ?: stringResource(R.string.error_generic)),
                    retryLabel = stringResource(R.string.retry),
                    onRetry = { vm.reload() },
                )
                s.list.items.isEmpty() -> EmptyView(stringResource(R.string.subjects_empty_title), stringResource(R.string.subjects_empty_body))
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(s.list.items, key = { it.id }) { subject ->
                        NodeCard(
                            title = localized(subject.title, subject.titleBn),
                            subtitle = localized(subject.description, subject.descriptionBn),
                            meta = stringResource(R.string.meta_subject, subject.categoryCount, subject.lessonCount),
                            badge = if (subject.featured) stringResource(R.string.featured_badge) else null,
                            onClick = { onOpenSubject(subject.id) },
                        )
                    }
                    if (s.list.hasMore) {
                        item(key = "footer") {
                            if (s.loadMoreFailed) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.load_more_failed), style = MaterialTheme.typography.bodyMedium)
                                    LinkButton(stringResource(R.string.retry), vm::loadMore)
                                }
                            } else {
                                // The footer only exists on screen when the user has scrolled to the end, so this loads the next page.
                                LaunchedEffect(s.list.nextCursor) { vm.loadMore() }
                                Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}
