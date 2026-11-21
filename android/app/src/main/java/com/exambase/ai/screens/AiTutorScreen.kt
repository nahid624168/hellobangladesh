package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.ai.AiTutorViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun AiTutorScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { AiTutorViewModel(it.aiRepository) }
    val s by vm.state.collectAsState()
    val ai by vm.aiState.collectAsState()
    var input by remember { mutableStateOf("") }
    var studyQuestion by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(ai.signedOut) { if (ai.signedOut) onSignedOut() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ai_tutor_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text(stringResource(R.string.ai_tutor_back)) }
            }
            Text(stringResource(R.string.ai_tutor_intro))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(ai.language == "en", { vm.onLanguage("en") }, { Text(stringResource(R.string.ai_language_en)) })
                FilterChip(ai.language == "bn", { vm.onLanguage("bn") }, { Text(stringResource(R.string.ai_language_bn)) })
            }
            AiQuotaCard(ai)
            Text(stringResource(R.string.ai_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SecondaryButton(stringResource(R.string.ai_tutor_new_chat), vm::newConversation)
                SecondaryButton(stringResource(R.string.ai_tutor_refresh), vm::loadHistory)
            }
            Text(stringResource(R.string.ai_tutor_history), style = MaterialTheme.typography.titleLarge)
            s.conversations.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.task, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.open(item.id) }) { Text(stringResource(R.string.ai_tutor_open)) }
                        }
                        TextButton(onClick = { vm.delete(item.id) }) { Text(stringResource(R.string.ai_tutor_delete)) }
                    }
                }
            }
            s.selected?.let { conversation ->
                Text("${conversation.title}", style = MaterialTheme.typography.titleLarge)
                conversation.messages.forEach { message ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(if (message.role == "user") stringResource(R.string.ai_tutor_you) else stringResource(R.string.ai_tutor_ai), style = MaterialTheme.typography.labelMedium)
                            Text(message.content)
                        }
                    }
                }
            }
            OutlinedTextField(input, { input = it }, label = { Text(stringResource(R.string.ai_tutor_ask)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            PrimaryButton(stringResource(R.string.ai_tutor_send), { vm.send(input) }, enabled = input.isNotBlank() && ai.canRun && !ai.quotaExhausted, loading = ai.running, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(studyQuestion, { studyQuestion = it }, label = { Text(stringResource(R.string.ai_tutor_guided)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SecondaryButton(stringResource(R.string.ai_tutor_hint), { vm.hint(studyQuestion) }, enabled = studyQuestion.isNotBlank() && ai.canRun && !ai.quotaExhausted, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.ai_tutor_steps), { vm.steps(studyQuestion) }, enabled = studyQuestion.isNotBlank() && ai.canRun && !ai.quotaExhausted, modifier = Modifier.weight(1f))
            }
            if (ai.hasOutput && s.selected == null) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(Spacing.md)) { Text(stringResource(R.string.ai_tutor_response), style = MaterialTheme.typography.titleMedium); Text(ai.output); Text(ai.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            ai.error?.let { Text(aiErrorText(it), color = MaterialTheme.colorScheme.error) }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (ai.showConsent) AiConsentDialog(ai.consentBusy, vm::acceptConsent, vm::declineConsent)
}
