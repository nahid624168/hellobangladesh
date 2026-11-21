package com.exambase.ai.screens

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
import com.exambase.ai.auth.SessionsViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun SessionsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SessionsViewModel(it.authRepository) }
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.sessions_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.sessions_subtitle), style = MaterialTheme.typography.bodyMedium)

            when {
                state.loading && state.sessions.isEmpty() -> Text(stringResource(R.string.loading))
                state.error != null && state.sessions.isEmpty() -> {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(R.string.retry), onClick = vm::load)
                }
                state.sessions.isEmpty() -> Text(stringResource(R.string.sessions_empty))
                else -> state.sessions.forEach { session ->
                    Card(Modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                session.deviceName?.ifBlank { null } ?: stringResource(R.string.sessions_unknown_device),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.sessions_last_used, session.lastUsedAt),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (session.current) {
                                Text(stringResource(R.string.sessions_this_device), color = MaterialTheme.colorScheme.primary)
                            } else {
                                SecondaryButton(
                                    text = stringResource(R.string.sessions_revoke),
                                    onClick = { vm.revoke(session.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (state.error != null && state.sessions.isNotEmpty()) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }

            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}
