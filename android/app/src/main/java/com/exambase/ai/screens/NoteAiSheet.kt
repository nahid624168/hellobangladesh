package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.exambase.ai.R
import com.exambase.ai.ai.AiApplyMode
import com.exambase.ai.ai.AiRules
import com.exambase.ai.ai.AiScope
import com.exambase.ai.ai.AiTarget
import com.exambase.ai.ai.AiTasks
import com.exambase.ai.ai.InputProblem
import com.exambase.ai.ai.NoteAiRules
import com.exambase.ai.ai.NoteAiViewModel
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

/**
 * AI sheet for the note editor: run a task on the selection (or the whole note), review and edit the answer,
 * then insert it, replace the text, or save it as a new note. Nothing reaches the note until the user presses one of those.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAiSheet(
    vm: NoteAiViewModel,
    onApply: (target: AiTarget, output: String, mode: AiApplyMode) -> Unit,
    onSaveAsNew: (title: String, body: String) -> Unit,
    onOpenAiContent: (String) -> Unit = {},
) {
    val sheet by vm.sheet.collectAsState()
    val s by vm.ai.collectAsState()
    val target = sheet.target
    if (!sheet.open || target == null) return

    ModalBottomSheet(
        onDismissRequest = vm::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.note_ai_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    if (target.scope == AiScope.SELECTION) R.string.note_ai_scope_selection else R.string.note_ai_scope_note,
                    target.text.length,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(target.text.take(240) + if (target.text.length > 240) "…" else "", style = MaterialTheme.typography.bodySmall, maxLines = 4)
            SecondaryButton(
                text = stringResource(R.string.note_ai_generate_content),
                onClick = { vm.close(); onOpenAiContent(target.text) },
                modifier = Modifier.fillMaxWidth(),
            )

            AiQuotaCard(s)

            Text(stringResource(R.string.ai_task_label), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AiTasks.textTasks.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        row.forEach { task ->
                            FilterChip(s.task == task, { vm.onTask(task) }, { Text(stringResource(aiTaskLabel(task))) })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(s.language == "en", { vm.onLanguage("en") }, { Text(stringResource(R.string.ai_language_en)) })
                FilterChip(s.language == "bn", { vm.onLanguage("bn") }, { Text(stringResource(R.string.ai_language_bn)) })
            }

            when (s.inputProblem) {
                InputProblem.EMPTY -> Text(stringResource(R.string.ai_input_empty), color = MaterialTheme.colorScheme.error)
                InputProblem.TOO_LONG -> Text(
                    stringResource(R.string.ai_input_too_long, AiRules.MAX_INPUT_CHARS) + " " + stringResource(R.string.note_ai_too_long_hint),
                    color = MaterialTheme.colorScheme.error,
                )
                null -> Unit
            }

            PrimaryButton(
                text = stringResource(R.string.ai_run),
                onClick = vm::run,
                enabled = s.canRun && !s.quotaExhausted,
                loading = s.running,
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.running) {
                Text(stringResource(R.string.ai_running), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
                LinkButton(stringResource(R.string.ai_cancel), vm::cancelRun)
            }
            s.error?.let { e ->
                Card(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(aiErrorText(e), color = MaterialTheme.colorScheme.error)
                        LinkButton(stringResource(R.string.retry), vm::run)
                    }
                }
            }

            if (s.hasOutput) {
                OutlinedTextField(
                    value = s.output,
                    onValueChange = vm::onOutput,
                    label = { Text(stringResource(R.string.ai_output_label)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(s.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ok = NoteAiRules.canApply(s.output)
                PrimaryButton(
                    text = stringResource(R.string.note_ai_insert_below),
                    onClick = { onApply(target, s.output, AiApplyMode.INSERT_BELOW); vm.close() },
                    enabled = ok,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(
                        if (target.scope == AiScope.SELECTION) R.string.note_ai_replace_selection else R.string.note_ai_replace_note,
                    ),
                    onClick = { onApply(target, s.output, AiApplyMode.REPLACE); vm.close() },
                    enabled = ok,
                    modifier = Modifier.fillMaxWidth(),
                )
                val fallbackTitle = stringResource(R.string.note_ai_default_title)
                SecondaryButton(
                    text = stringResource(R.string.note_ai_save_new),
                    onClick = { onSaveAsNew(NoteAiRules.newNoteTitle(s.output, fallbackTitle), s.output.trim()); vm.close() },
                    enabled = ok,
                    modifier = Modifier.fillMaxWidth(),
                )
                LinkButton(stringResource(R.string.note_ai_discard), vm::discard)
            } else {
                Text(stringResource(R.string.ai_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinkButton(stringResource(R.string.back), vm::close)
        }
    }
    if (s.showConsent) {
        AiConsentDialog(busy = s.consentBusy, onAgree = vm::acceptConsent, onDecline = vm::declineConsent)
    }
}
