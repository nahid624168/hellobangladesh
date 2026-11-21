package com.exambase.ai.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.exambase.ai.search.SearchViewModel
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenHit: (type: String, id: String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { SearchViewModel(it.searchRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium)
            ExamBaseTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = stringResource(R.string.search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(stringResource(R.string.search_action), onClick = vm::submit, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(null to stringResource(R.string.search_type_all),
                    "lesson" to stringResource(R.string.search_type_lesson),
                    "note" to stringResource(R.string.search_type_note),
                    "quiz" to stringResource(R.string.search_type_quiz),
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.typeFilter == key,
                        onClick = { vm.onTypeFilter(key) },
                        label = { Text(label) },
                    )
                }
            }

            when {
                state.loading -> Text(stringResource(R.string.loading))
                state.error != null -> Text(state.error?.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                state.results.isEmpty() && state.query.isNotBlank() && !state.loading ->
                    Text(stringResource(R.string.search_empty))
                state.results.isNotEmpty() -> state.results.forEach { hit ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenHit(hit.type, hit.id) },
                    ) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(hit.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.search_hit_meta, hit.type, if (hit.privateOwned) stringResource(R.string.search_private) else ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            hit.snippet?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                else -> {
                    if (state.recent.isNotEmpty()) {
                        Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleSmall)
                        state.recent.forEach { q ->
                            Text(q, Modifier.clickable { vm.useSuggestion(q) })
                        }
                    }
                    if (state.suggested.isNotEmpty()) {
                        Text(stringResource(R.string.search_suggested), style = MaterialTheme.typography.titleSmall)
                        state.suggested.forEach { q ->
                            Text(q, Modifier.clickable { vm.useSuggestion(q) })
                        }
                    }
                }
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}
