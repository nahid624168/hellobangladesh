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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.exambase.ai.R
import com.exambase.ai.ai.AiAssistantViewModel
import com.exambase.ai.ai.AiError
import com.exambase.ai.ai.AiRules
import com.exambase.ai.ai.AiTasks
import com.exambase.ai.ai.AiUiState
import com.exambase.ai.ai.InputProblem
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.ErrorView
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.LoadingView
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun AiAssistantScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { AiAssistantViewModel(it.aiRepository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

    Surface(Modifier.fillMaxSize()) {
        when {
            state.loading && state.consent == null && state.quota == null -> LoadingView()
            state.consent == null && state.quota == null && state.error != null -> ErrorView(
                message = aiErrorText(state.error!!),
                retryLabel = stringResource(R.string.retry),
                onRetry = vm::load,
            )
            else -> AiAssistantContent(state, vm, onBack)
        }
        if (state.showConsent) {
            AiConsentDialog(busy = state.consentBusy, onAgree = vm::acceptConsent, onDecline = vm::declineConsent)
        }
    }
}

@Composable
private fun AiAssistantContent(state: AiUiState, vm: AiAssistantViewModel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.ai_intro), style = MaterialTheme.typography.bodyMedium)

        AiQuotaCard(state)

        Text(stringResource(R.string.ai_task_label), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AiTasks.textTasks.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    row.forEach { task ->
                        FilterChip(
                            selected = state.task == task,
                            onClick = { vm.onTask(task) },
                            label = { Text(stringResource(aiTaskLabel(task))) },
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.ai_language_label), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(state.language == "en", { vm.onLanguage("en") }, { Text(stringResource(R.string.ai_language_en)) })
            FilterChip(state.language == "bn", { vm.onLanguage("bn") }, { Text(stringResource(R.string.ai_language_bn)) })
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = vm::onInput,
            label = { Text(stringResource(R.string.ai_input_label)) },
            isError = state.inputProblem != null,
            supportingText = {
                Text(
                    when (state.inputProblem) {
                        InputProblem.EMPTY -> stringResource(R.string.ai_input_empty)
                        InputProblem.TOO_LONG -> stringResource(R.string.ai_input_too_long, AiRules.MAX_INPUT_CHARS)
                        null -> stringResource(R.string.ai_input_counter, state.input.length, AiRules.MAX_INPUT_CHARS)
                    },
                )
            },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = stringResource(R.string.ai_run),
            onClick = vm::run,
            enabled = state.canRun && !state.quotaExhausted,
            loading = state.running,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.running) {
            Text(
                stringResource(R.string.ai_running),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = vm::cancelRun) { Text(stringResource(R.string.ai_cancel)) }
        }

        state.error?.let { e ->
            Card(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(aiErrorText(e), color = MaterialTheme.colorScheme.error)
                    LinkButton(stringResource(R.string.retry), vm::run)
                }
            }
        }

        if (state.hasOutput) {
            OutlinedTextField(
                value = state.output,
                onValueChange = { copied = false; vm.onOutput(it) },
                label = { Text(stringResource(R.string.ai_output_label)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(state.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton(
                text = stringResource(if (copied) R.string.ai_copied else R.string.ai_copy),
                onClick = { clipboard.setText(AnnotatedString(state.output)); copied = true },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // The "AI may be wrong" notice is visible before the first answer too.
            Text(stringResource(R.string.ai_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (AiRules.consentActive(state.consent)) {
            Text(stringResource(R.string.ai_consent_active), style = MaterialTheme.typography.bodySmall)
            LinkButton(stringResource(R.string.ai_turn_off), vm::revokeConsent)
        } else {
            Text(stringResource(R.string.ai_consent_off), style = MaterialTheme.typography.bodySmall)
        }
        LinkButton(stringResource(R.string.back), onBack)
    }
}

@Composable
internal fun AiQuotaCard(state: AiUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.ai_quota_title), style = MaterialTheme.typography.titleSmall)
            val q = state.quota
            if (q == null) {
                Text(stringResource(R.string.ai_quota_unknown), style = MaterialTheme.typography.bodySmall)
            } else {
                val resets = AiRules.resetTimeLabel(q.resetsAt)
                Text(stringResource(R.string.ai_quota_requests, q.requestsUsed, q.requestsLimit))
                Text(stringResource(R.string.ai_quota_tokens, q.tokensUsed, q.tokensLimit), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { AiRules.quotaUsedFraction(q) }, modifier = Modifier.fillMaxWidth())
                when {
                    AiRules.quotaExhausted(q) -> Text(
                        stringResource(R.string.ai_quota_exhausted, resets ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    AiRules.quotaLow(q) -> Text(stringResource(R.string.ai_quota_low), style = MaterialTheme.typography.bodySmall)
                    resets != null -> Text(stringResource(R.string.ai_quota_resets, resets), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Shared with Step 33b/33c screens: the one consent dialog. Nothing is sent while it is open. */
@Composable
fun AiConsentDialog(busy: Boolean, onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDecline() },
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ai_consent_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree, enabled = !busy) { Text(stringResource(R.string.ai_consent_agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline, enabled = !busy) { Text(stringResource(R.string.ai_consent_decline)) }
        },
    )
}

internal fun aiTaskLabel(task: String): Int = when (task) {
    AiTasks.SUMMARIZE -> R.string.ai_task_summarize
    AiTasks.EXPLAIN -> R.string.ai_task_explain
    AiTasks.OUTLINE -> R.string.ai_task_outline
    AiTasks.REVISION_NOTES -> R.string.ai_task_revision_notes
    AiTasks.SIMPLIFY -> R.string.ai_task_simplify
    else -> R.string.ai_task_translate
}

@Composable
internal fun aiErrorText(error: AiError): String = when (error) {
    AiError.Offline -> stringResource(R.string.ai_error_offline)
    AiError.QuotaExceeded -> stringResource(R.string.ai_error_quota)
    AiError.TemporarilyBlocked -> stringResource(R.string.ai_error_blocked_temp)
    AiError.RateLimited -> stringResource(R.string.ai_error_rate)
    AiError.ContentBlocked -> stringResource(R.string.ai_error_content)
    AiError.Disabled -> stringResource(R.string.ai_error_disabled)
    AiError.Unavailable -> stringResource(R.string.ai_error_unavailable)
    is AiError.InvalidInput -> error.message
    AiError.ConsentRequired -> stringResource(R.string.ai_consent_off)
    AiError.Other -> stringResource(R.string.ai_error_other)
}
