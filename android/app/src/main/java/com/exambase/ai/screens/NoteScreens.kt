package com.exambase.ai.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.exambase.ai.R
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.notes.NoteEditorViewModel
import com.exambase.ai.ai.NoteAiRules
import com.exambase.ai.ai.NoteAiViewModel
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.exambase.ai.notes.NoteVersionsViewModel
import com.exambase.ai.notes.noteExportText
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.exambase.ai.notes.NotesListViewModel
import com.exambase.ai.notes.SaveStatus
import com.exambase.ai.offline.SyncStatusViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.NoteSummary

@Composable
fun NotesListScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { NotesListViewModel(it.noteRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    var newFolder by remember { mutableStateOf("") }

    ContentScaffold(stringResource(R.string.my_notes), onBack) {
        val syncVm: SyncStatusViewModel = viewModel()
        val sync by syncVm.state.collectAsState()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { vm.createNote(onOpenNote) }) {
                    Text(stringResource(R.string.note_new))
                }
                TextButton(onClick = syncVm::requestSync, enabled = !sync.running) {
                    Text(stringResource(R.string.note_sync_now))
                }
            }
            val syncLine = buildString {
                append(stringResource(R.string.note_sync_status))
                append(": ")
                append(sync.status ?: stringResource(R.string.note_sync_never))
                sync.lastSyncAt?.let { append(" · "); append(it.take(19)) }
                if (sync.conflictCount > 0) {
                    append(" · ")
                    append(stringResource(R.string.note_sync_conflicts, sync.conflictCount))
                }
            }
            Text(
                syncLine,
                style = MaterialTheme.typography.labelSmall,
                color = if (sync.conflictCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQuery,
                label = { Text(stringResource(R.string.note_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilterChip(
                    selected = s.folderId == null,
                    onClick = { vm.selectFolder(null) },
                    label = { Text(stringResource(R.string.note_all_folders)) },
                )
                s.folders.filter { !it.deleted }.forEach { f ->
                    FilterChip(
                        selected = s.folderId == f.id,
                        onClick = { vm.selectFolder(f.id) },
                        label = { Text(f.name) },
                    )
                }
                FilterChip(selected = s.pinnedOnly, onClick = vm::togglePinned, label = { Text(stringResource(R.string.note_pinned)) })
                FilterChip(selected = s.favoriteOnly, onClick = vm::toggleFavorite, label = { Text(stringResource(R.string.note_favorite)) })
                TextButton(onClick = vm::submitSearch) { Text(stringResource(R.string.search)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text(stringResource(R.string.note_new_folder)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (newFolder.isNotBlank()) {
                            vm.createFolder(newFolder.trim())
                            newFolder = ""
                        }
                    },
                ) { Text(stringResource(R.string.note_add_folder)) }
            }
            when {
                s.loading -> LoadingView()
                s.error != null && s.list.items.isEmpty() -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = vm::reload)
                s.list.items.isEmpty() -> EmptyView(
                    title = stringResource(R.string.notes_empty_title),
                    body = stringResource(R.string.notes_empty_body),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    items(s.list.items, key = { it.id }) { item ->
                        NoteCard(item, onOpen = { onOpenNote(item.id) }, onDelete = { vm.deleteNote(item.id) })
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
private fun NoteCard(item: NoteSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    item.title.ifBlank { stringResource(R.string.note_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val badges = buildList {
                    if (item.pinned) add(stringResource(R.string.note_pinned))
                    if (item.favorite) add(stringResource(R.string.note_favorite))
                }
                if (badges.isNotEmpty()) {
                    Text(badges.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (item.preview.isNotBlank()) {
                Text(item.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.quiz_delete)) }
        }
    }
}

@Composable
fun NoteEditorScreen(
    noteId: String?,
    onBack: () -> Unit,
    onVersions: (String) -> Unit = {},
    onOpenAiContent: (String) -> Unit = {},
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { NoteEditorViewModel(noteId, it.noteRepository) }
    val s by vm.state.collectAsState()
    val aiVm = appViewModel { NoteAiViewModel(it.aiRepository) }
    val aiState by aiVm.ai.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    LaunchedEffect(aiState.signedOut) { if (aiState.signedOut) onSignedOut() }
    // The text field keeps its own selection; the view model keeps the text. They meet here.
    var body by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(s.loading, s.bodyRevision) {
        if (!s.loading && body.text != s.body) body = TextFieldValue(s.body, TextRange(s.cursor.coerceIn(0, s.body.length)))
    }

    ContentScaffold(stringResource(R.string.note_editor_title), onBack) {
        if (s.loading) {
            LoadingView()
        } else {
            Column(Modifier.fillMaxSize().padding(Spacing.md)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        saveLabel(s.saveStatus),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (s.saveStatus) {
                            SaveStatus.Error, SaveStatus.Conflict -> MaterialTheme.colorScheme.error
                            SaveStatus.Saved -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.semantics {
                            contentDescription = saveLabel(s.saveStatus)
                        },
                    )
                    Text(
                        stringResource(R.string.note_counts, s.wordCount, s.charCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = s.title,
                    onValueChange = vm::onTitle,
                    label = { Text(stringResource(R.string.note_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                // Formatting toolbar (markdown helpers)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    TextButton(onClick = { vm.insertFormat("**", "**", "bold") }) { Text("B") }
                    TextButton(onClick = { vm.insertFormat("_", "_", "italic") }) { Text("I") }
                    TextButton(onClick = { vm.insertFormat("~~", "~~", "strike") }) { Text("S") }
                    TextButton(onClick = { vm.insertFormat("\n## ", "", "Heading") }) { Text("H") }
                    TextButton(onClick = { vm.insertFormat("\n- ", "", "item") }) { Text("•") }
                    TextButton(onClick = { vm.insertFormat("\n- [ ] ", "", "task") }) { Text("☑") }
                    TextButton(onClick = { vm.insertFormat("\n> ", "", "quote") }) { Text("❝") }
                    TextButton(onClick = { vm.insertFormat("[", "](https://)", "link") }) { Text("🔗") }
                    TextButton(onClick = vm::togglePinned) {
                        Text(if (s.pinned) stringResource(R.string.note_pinned) else stringResource(R.string.note_pin))
                    }
                    TextButton(onClick = vm::toggleFavorite) {
                        Text(if (s.favorite) "★" else "☆")
                    }
                    TextButton(onClick = vm::saveNow) { Text(stringResource(R.string.save)) }
                    TextButton(
                        onClick = {
                            vm.clearNotice()
                            aiVm.open(NoteAiRules.target(body.text, body.selection.start, body.selection.end))
                        },
                    ) { Text(stringResource(R.string.note_ai_button)) }
                    if (s.undoBody != null) {
                        TextButton(onClick = vm::undoAi) { Text(stringResource(R.string.note_ai_undo)) }
                    }
                    val ctx = LocalContext.current
                    TextButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, s.title.ifBlank { ctx.getString(R.string.note_untitled) })
                                putExtra(Intent.EXTRA_TEXT, noteExportText(s.title, s.body, s.version))
                            }
                            ctx.startActivity(Intent.createChooser(share, ctx.getString(R.string.note_share)))
                        },
                    ) { Text(stringResource(R.string.note_share)) }
                    if (s.noteId != null) {
                        TextButton(onClick = { onVersions(s.noteId!!) }) {
                            Text(stringResource(R.string.note_history))
                        }
                    }
                }
                if (s.error != null) {
                    Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                s.notice?.let { key ->
                    Text(
                        stringResource(
                            when (key) {
                                "ai_applied_at_end" -> R.string.note_ai_applied_at_end
                                "new_note_saved" -> R.string.note_ai_new_saved
                                else -> R.string.note_ai_new_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (key == "new_note_failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; if (it.text != s.body) vm.onBody(it.text) },
                    label = { Text(stringResource(R.string.note_body)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    minLines = 12,
                )
            }
        }
    }
    NoteAiSheet(
        vm = aiVm,
        onApply = { target, output, mode ->
            vm.applyAiResult(NoteAiRules.apply(s.body, target, output, mode))
        },
        onSaveAsNew = { title, text -> vm.saveAsNewNote(title, text) },
        onOpenAiContent = onOpenAiContent,
    )
}

@Composable
private fun saveLabel(status: SaveStatus): String = when (status) {
    SaveStatus.Idle -> stringResource(R.string.note_save_idle)
    SaveStatus.Saving -> stringResource(R.string.note_saving)
    SaveStatus.Saved -> stringResource(R.string.note_saved)
    SaveStatus.Error -> stringResource(R.string.note_save_error)
    SaveStatus.Conflict -> stringResource(R.string.note_save_conflict)
}

@Composable
fun NoteVersionsScreen(
    noteId: String,
    onBack: () -> Unit,
    onRestored: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { NoteVersionsViewModel(noteId, it.noteRepository) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.signedOut) { if (s.signedOut) onSignedOut() }
    LaunchedEffect(s.restored) { if (s.restored) onRestored() }

    ContentScaffold(stringResource(R.string.note_history_title), onBack) {
        when {
            s.loading -> LoadingView()
            s.error != null && s.versions.isEmpty() -> ErrorView(retryLabel = stringResource(R.string.retry), message = s.error!!, onRetry = vm::reload)
            else -> Column(Modifier.fillMaxSize().padding(Spacing.md)) {
                Text(stringResource(R.string.note_history_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.compareA != null && s.compareB != null) {
                    val a = if (s.compareA!!.version <= s.compareB!!.version) s.compareA!! else s.compareB!!
                    val b = if (s.compareA!!.version <= s.compareB!!.version) s.compareB!! else s.compareA!!
                    Card(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.note_compare_title, a.version, b.version), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.note_compare_older), style = MaterialTheme.typography.labelMedium)
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            Text(a.body, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                            Text(stringResource(R.string.note_compare_newer), style = MaterialTheme.typography.labelMedium)
                            Text(b.title, style = MaterialTheme.typography.titleMedium)
                            Text(b.body, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                            TextButton(onClick = vm::clearCompare) { Text(stringResource(R.string.note_compare_clear)) }
                        }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                    items(s.versions, key = { it.version }) { v ->
                        val selected = s.compareA?.version == v.version || s.compareB?.version == v.version
                        Card(onClick = { vm.selectForCompare(v) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    stringResource(R.string.note_version_label, v.version) + if (selected) " ✓" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(v.createdAt.take(19), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v.title.ifBlank { stringResource(R.string.note_untitled) }, style = MaterialTheme.typography.bodyMedium)
                                Text(v.body.take(120), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                                TextButton(
                                    onClick = { vm.restore(v.version) },
                                    enabled = !s.restoring,
                                ) { Text(stringResource(R.string.note_restore)) }
                            }
                        }
                    }
                }
                if (s.error != null) {
                    Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
