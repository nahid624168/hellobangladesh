package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.academic.LibraryViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.theme.Spacing

private const val TAB_SAVED = 0
private const val TAB_RECENT = 1

/** Bookmarked lessons and recently viewed lessons. */
@Composable
fun LibraryScreen(onBack: () -> Unit, onOpenLesson: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.academicRepository) }
    val saved by vm.saved.collectAsState()
    val recent by vm.recent.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(TAB_SAVED) }
    RefreshOnReturn { vm.load(silent = true) }

    ContentScaffold(stringResource(R.string.library_title), onBack) {
        Row(Modifier.padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(selected = tab == TAB_SAVED, onClick = { tab = TAB_SAVED }, label = { Text(stringResource(R.string.tab_saved)) })
            FilterChip(selected = tab == TAB_RECENT, onClick = { tab = TAB_RECENT }, label = { Text(stringResource(R.string.tab_recent)) })
        }
        Box(Modifier.weight(1f)) {
            if (tab == TAB_SAVED) {
                LoadStateHost(saved, onRetry = { vm.load(silent = false) }, onSignedOut = onSignedOut) { list ->
                    if (list.items.isEmpty()) {
                        EmptyView(stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_body))
                    } else {
                        LazyColumn(contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(list.items, key = { it.id }) { lesson -> LessonCard(lesson) { onOpenLesson(lesson.id) } }
                            if (list.hasMore) {
                                item(key = "footer") {
                                    // Reached only when scrolled to the end, so this fetches the next page.
                                    LaunchedEffect(list.nextCursor) { vm.loadMoreSaved() }
                                    if (loadingMore) {
                                        Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LoadStateHost(recent, onRetry = { vm.load(silent = false) }, onSignedOut = onSignedOut) { lessons ->
                    if (lessons.isEmpty()) {
                        EmptyView(stringResource(R.string.recent_empty_title), stringResource(R.string.recent_empty_body))
                    } else {
                        LazyColumn(contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(lessons, key = { it.id }) { lesson -> LessonCard(lesson) { onOpenLesson(lesson.id) } }
                        }
                    }
                }
            }
        }
    }
}
