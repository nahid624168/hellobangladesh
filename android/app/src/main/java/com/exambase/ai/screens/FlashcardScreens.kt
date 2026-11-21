package com.exambase.ai.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.exambase.ai.R
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.flashcards.FlashcardDetailViewModel
import com.exambase.ai.flashcards.FlashcardEditorViewModel
import com.exambase.ai.flashcards.FlashcardListViewModel
import com.exambase.ai.flashcards.StudyViewModel
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.FlashcardSetListItem
import com.exambase.core.network.FlashcardSetStatus
import com.exambase.core.network.FlashcardSort
import com.exambase.core.network.ReviewRating

@Composable
fun FlashcardListScreen(
    onBack: () -> Unit,
    onOpenSet: (String) -> Unit,
    onCreate: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { FlashcardListViewModel(it.flashcardRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }

    ContentScaffold(stringResource(R.string.my_flashcard_sets), onBack) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCreate) { Text(stringResource(R.string.flashcard_create)) }
            }
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQueryChange,
                label = { Text(stringResource(R.string.flashcard_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(selected = s.statusFilter == null, onClick = { vm.onStatusFilter(null) }, label = { Text(stringResource(R.string.quiz_filter_all)) })
                FilterChip(selected = s.statusFilter == FlashcardSetStatus.DRAFT, onClick = { vm.onStatusFilter(FlashcardSetStatus.DRAFT) }, label = { Text(stringResource(R.string.quiz_filter_draft)) })
                FilterChip(selected = s.statusFilter == FlashcardSetStatus.PRIVATE, onClick = { vm.onStatusFilter(FlashcardSetStatus.PRIVATE) }, label = { Text(stringResource(R.string.quiz_filter_private)) })
                FilterChip(selected = s.statusFilter == FlashcardSetStatus.ARCHIVED, onClick = { vm.onStatusFilter(FlashcardSetStatus.ARCHIVED) }, label = { Text(stringResource(R.string.quiz_filter_archived)) })
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                listOf(
                    FlashcardSort.RECENT to R.string.sort_recent,
                    FlashcardSort.TITLE to R.string.sort_title,
                    FlashcardSort.CARDS to R.string.flashcard_sort_cards,
                ).forEach { (v, label) ->
                    FilterChip(selected = s.sort == v, onClick = { vm.onSortChange(v) }, label = { Text(stringResource(label)) })
                }
                TextButton(onClick = vm::submitSearch) { Text(stringResource(R.string.search)) }
            }
            when {
                s.loading -> LoadingView()
                s.error != null -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!.message ?: "Error", onRetry = vm::reload)
                s.list.items.isEmpty() -> EmptyView(
                    title = stringResource(R.string.flashcards_empty_title),
                    body = stringResource(R.string.flashcards_empty_body),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(s.list.items, key = { it.set.id }) { item ->
                        FlashcardSetCard(
                            item = item,
                            onOpen = { onOpenSet(item.set.id) },
                            onDuplicate = { vm.duplicateSet(item.set.id, onOpenSet) },
                            onDelete = { vm.deleteSet(item.set.id) },
                        )
                    }
                    if (s.list.nextCursor != null) {
                        item {
                            if (s.loadingMore) CircularProgressIndicator(Modifier.padding(Spacing.md))
                            else LinkButton(stringResource(R.string.load_more), onClick = vm::loadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardSetCard(
    item: FlashcardSetListItem,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val set = item.set
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(set.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.flashcard_meta, set.cardCount, item.dueCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = onDuplicate) { Text(stringResource(R.string.quiz_duplicate)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.quiz_delete)) }
            }
        }
    }
}

@Composable
fun FlashcardDetailScreen(
    setId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStudy: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { FlashcardDetailViewModel(setId, it.flashcardRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }

    ContentScaffold(s.detail?.set?.title ?: stringResource(R.string.flashcard_set_title), onBack) {
        when {
            s.loading -> LoadingView()
            s.error != null && s.detail == null -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = vm::reload)
            s.detail != null -> {
                val detail = s.detail!!
                Column(Modifier.fillMaxSize().padding(Spacing.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { onStudy(setId) }, enabled = detail.cards.isNotEmpty()) {
                            Text(stringResource(R.string.flashcard_study))
                        }
                        TextButton(onClick = { onEdit(setId) }) { Text(stringResource(R.string.quiz_edit)) }
                    }
                    Text(stringResource(R.string.flashcard_cards_count, detail.cards.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = Spacing.sm))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items(detail.cards, key = { it.id }) { card ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(Spacing.sm)) {
                                    Text(card.frontText, style = MaterialTheme.typography.bodyMedium)
                                    Text(card.backText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { vm.deleteCard(card.id) }) { Text(stringResource(R.string.remove)) }
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.flashcard_add_card), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = front, onValueChange = { front = it }, label = { Text(stringResource(R.string.flashcard_front)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = back, onValueChange = { back = it }, label = { Text(stringResource(R.string.flashcard_back)) }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (front.isNotBlank() && back.isNotBlank()) {
                                vm.addCard(front.trim(), back.trim()) { front = ""; back = "" }
                            }
                        },
                        enabled = !s.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.flashcard_add)) }
                }
            }
        }
    }
}

@Composable
fun FlashcardEditorScreen(
    setId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { FlashcardEditorViewModel(setId, it.flashcardRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    LaunchedEffect(s.savedId) { s.savedId?.let(onSaved) }

    ContentScaffold(stringResource(if (setId == null) R.string.flashcard_create_title else R.string.flashcard_edit_title), onBack) {
        if (s.loading) LoadingView()
        else Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(value = s.title, onValueChange = vm::onTitle, label = { Text(stringResource(R.string.quiz_field_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = s.description, onValueChange = vm::onDescription, label = { Text(stringResource(R.string.quiz_field_description)) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(FlashcardSetStatus.DRAFT, FlashcardSetStatus.PRIVATE, FlashcardSetStatus.ARCHIVED).forEach { st ->
                    FilterChip(selected = s.status == st, onClick = { vm.onStatus(st) }, label = { Text(st) })
                }
            }
            if (s.error != null) Text(s.error!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = vm::save, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (s.saving) R.string.saving else R.string.save))
            }
        }
    }
}

@Composable
fun StudyScreen(
    setId: String,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { StudyViewModel(setId, it.flashcardRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }

    ContentScaffold(stringResource(R.string.flashcard_study), onBack) {
        when {
            s.loading -> LoadingView()
            s.error != null && s.queue.isEmpty() -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = vm::loadDue)
            s.done -> Column(Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.flashcard_study_done, s.reviewedCount), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = vm::loadDue, modifier = Modifier.padding(top = Spacing.md)) { Text(stringResource(R.string.flashcard_study_again)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            s.queue.isNotEmpty() && s.index in s.queue.indices -> {
                val study = s.queue[s.index]
                val card = study.card
                val rotation by animateFloatAsState(if (s.flipped) 180f else 0f, label = "flip")
                Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.flashcard_progress, s.index + 1, s.queue.size), style = MaterialTheme.typography.labelMedium)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12 * density
                            }
                            .clickable { vm.flip() },
                    ) {
                        Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                            val showBack = rotation > 90f
                            Text(
                                text = if (showBack) card.backText else card.frontText,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer { if (showBack) rotationY = 180f },
                            )
                        }
                    }
                    Text(stringResource(R.string.flashcard_tap_flip), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.flipped) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { vm.rate(ReviewRating.AGAIN) }, enabled = !s.reviewing) { Text(stringResource(R.string.flashcard_again)) }
                            TextButton(onClick = { vm.rate(ReviewRating.HARD) }, enabled = !s.reviewing) { Text(stringResource(R.string.flashcard_hard)) }
                            TextButton(onClick = { vm.rate(ReviewRating.GOOD) }, enabled = !s.reviewing) { Text(stringResource(R.string.flashcard_good)) }
                            TextButton(onClick = { vm.rate(ReviewRating.EASY) }, enabled = !s.reviewing) { Text(stringResource(R.string.flashcard_easy)) }
                        }
                    }
                }
            }
        }
    }
}
