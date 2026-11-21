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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.ai.AiContentViewModel
import com.exambase.ai.ai.AiTasks
import com.exambase.ai.ai.GeneratedFlashcardDraft
import com.exambase.ai.ai.GeneratedQuestionDraft
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun AiContentScreen(onBack: () -> Unit, onSignedOut: () -> Unit, initialSource: String? = null) {
    val vm = appViewModel { AiContentViewModel(it.aiRepository, it.flashcardRepository, initialSource) }
    val s by vm.state.collectAsState()
    val ai by vm.aiState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(ai.signedOut) { if (ai.signedOut) onSignedOut() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ai_content_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text(stringResource(R.string.ai_tutor_back)) }
            }
            Text(stringResource(R.string.ai_content_intro), style = MaterialTheme.typography.bodyMedium)
            AiQuotaCard(ai)
            OutlinedTextField(value = s.title, onValueChange = vm::title, label = { Text(stringResource(R.string.ai_content_title_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = s.input, onValueChange = vm::input, label = { Text(stringResource(R.string.ai_content_source_label)) }, minLines = 6, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton(stringResource(R.string.ai_content_generate_mcq), { vm.generate(AiTasks.MAKE_QUESTIONS) }, enabled = ai.canRun && !ai.quotaExhausted && !s.busy && s.input.isNotBlank(), loading = s.busy, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.ai_content_generate_cards), { vm.generate(AiTasks.MAKE_FLASHCARDS) }, enabled = ai.canRun && !ai.quotaExhausted && !s.busy && s.input.isNotBlank(), modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.ai_content_notice), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            ai.error?.let { Text(aiErrorText(it), color = MaterialTheme.colorScheme.error) }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (s.questions.isNotEmpty()) {
                Text(stringResource(R.string.ai_content_review_mcq, s.questions.size), style = MaterialTheme.typography.titleLarge)
                s.questions.forEachIndexed { index, q ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedTextField(q.question, { vm.editQuestion(index, q.copy(question = it)) }, label = { Text(stringResource(R.string.ai_content_question_label, index + 1)) }, modifier = Modifier.fillMaxWidth())
                            q.options.forEachIndexed { oi, option ->
                                OutlinedTextField(option, { value -> vm.editQuestion(index, q.copy(options = q.options.toMutableList().also { list -> list[oi] = value })) }, label = { Text(stringResource(R.string.ai_content_option_label, ('A' + oi).toString())) }, modifier = Modifier.fillMaxWidth())
                                FilterChip(selected = q.correctOptionIndex == oi, onClick = { vm.editQuestion(index, q.copy(correctOptionIndex = oi)) }, label = { Text(if (q.correctOptionIndex == oi) stringResource(R.string.ai_content_correct) else stringResource(R.string.ai_content_mark_correct)) })
                            }
                            OutlinedTextField(q.explanation, { vm.editQuestion(index, q.copy(explanation = it)) }, label = { Text(stringResource(R.string.ai_content_explanation)) }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { vm.removeQuestion(index) }) { Text(stringResource(R.string.ai_content_remove_question)) }
                        }
                    }
                }
                PrimaryButton(stringResource(R.string.ai_content_save_quiz), vm::saveQuiz, enabled = !s.busy && s.questions.isNotEmpty(), loading = s.busy, modifier = Modifier.fillMaxWidth())
                s.savedQuiz?.let { Text(stringResource(R.string.ai_content_saved_quiz, it.title, it.questionCount)) }
            }
            if (s.savedSets.isNotEmpty()) {
                Text(stringResource(R.string.ai_content_my_quizzes), style = MaterialTheme.typography.titleLarge)
                s.savedSets.forEach { saved ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(saved.title, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.ai_content_private_count, saved.questionCount), style = MaterialTheme.typography.bodySmall); TextButton(onClick = { vm.openSavedQuiz(saved.id) }) { Text(stringResource(R.string.ai_tutor_open)) } }
                            TextButton(onClick = { vm.deleteSavedQuiz(saved.id) }) { Text(stringResource(R.string.ai_content_delete)) }
                        }
                    }
                }
            }
            s.selectedSaved?.let { saved ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(saved.quizSet.title, style = MaterialTheme.typography.titleLarge)
                        saved.questions.forEachIndexed { index, q ->
                            Text("${index + 1}. ${q.question}", style = MaterialTheme.typography.titleSmall)
                            q.options.forEachIndexed { oi, option ->
                                Text("${'A' + oi}. $option${if (oi == q.correctOptionIndex) " ✓" else ""}")
                            }
                            if (q.explanation.isNotBlank()) Text(q.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = vm::closeSavedQuiz) { Text(stringResource(R.string.ai_content_done)) }
                    }
                }
            }
            if (s.cards.isNotEmpty()) {
                Text(stringResource(R.string.ai_content_review_cards, s.cards.size), style = MaterialTheme.typography.titleLarge)
                s.cards.forEachIndexed { index, card ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedTextField(card.front, { vm.editCard(index, card.copy(front = it)) }, label = { Text(stringResource(R.string.ai_content_front)) }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(card.back, { vm.editCard(index, card.copy(back = it)) }, label = { Text(stringResource(R.string.ai_content_back)) }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { vm.removeCard(index) }) { Text(stringResource(R.string.ai_content_remove_card)) }
                        }
                    }
                }
                PrimaryButton(stringResource(R.string.ai_content_save_cards), vm::saveFlashcards, enabled = !s.busy && s.cards.isNotEmpty(), loading = s.busy, modifier = Modifier.fillMaxWidth())
                s.savedFlashcardTitle?.let { Text(stringResource(R.string.ai_content_saved_cards, it)) }
            }
            SecondaryButton(stringResource(R.string.ai_content_done), onBack, modifier = Modifier.fillMaxWidth())
        }
    }
    if (ai.showConsent) AiConsentDialog(ai.consentBusy, vm::acceptConsent, vm::declineConsent)
}
