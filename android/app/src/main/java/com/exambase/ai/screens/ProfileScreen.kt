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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.auth.FormErrorText
import com.exambase.ai.auth.ProfileViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onVerifyEmail: () -> Unit,
    onOpenSessions: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val vm = appViewModel { ProfileViewModel(it.authRepository) }
    val user by vm.user.collectAsState()
    val state by vm.state.collectAsState()
    val signedOut by vm.signedOut.collectAsState()
    val exportSummary by vm.exportSummary.collectAsState()
    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }

    val current = user
    if (current == null) {
        EmptyView(stringResource(R.string.error_generic), "", actionLabel = stringResource(R.string.back), onAction = onBack)
        return
    }

    var name by remember(current.displayName) { mutableStateOf(current.displayName) }
    var theme by remember(current.theme) { mutableStateOf(current.theme) }
    var showDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.profile), style = MaterialTheme.typography.headlineMedium)
            Text(current.email, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (current.emailVerified) R.string.email_verified else R.string.email_not_verified),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!current.emailVerified) LinkButton(stringResource(R.string.verify_title), onVerifyEmail)

            ExamBaseTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.display_name),
                errorMessage = state.fieldErrors["displayName"],
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
                    .forEach { (value, label) ->
                        FilterChip(selected = theme == value, onClick = { theme = value }, label = { Text(stringResource(label)) })
                    }
            }
            FormErrorText(state)
            PrimaryButton(
                text = stringResource(R.string.save),
                loading = state.loading,
                onClick = { vm.save(name, theme) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.success) Text(stringResource(R.string.saved), color = MaterialTheme.colorScheme.secondary)

            SecondaryButton(stringResource(R.string.sessions_open), onClick = onOpenSessions, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.export_data), onClick = vm::requestExport, modifier = Modifier.fillMaxWidth())
            if (exportSummary != null) {
                Text(exportSummary!!, style = MaterialTheme.typography.bodySmall)
            }
            if (current.avatarUrl != null) {
                LinkButton(stringResource(R.string.clear_avatar), onClick = vm::clearAvatar)
            }
            SecondaryButton(stringResource(R.string.sign_out), onClick = vm::logout, modifier = Modifier.fillMaxWidth())
            LinkButton(stringResource(R.string.delete_account), onClick = { showDelete = true })
            LinkButton(stringResource(R.string.back), onBack)
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.delete_confirm_body))
                    ExamBaseTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = stringResource(R.string.password),
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FormErrorText(state)
                }
            },
            confirmButton = {
                TextButton(enabled = deletePassword.isNotEmpty() && !state.loading, onClick = { vm.deleteAccount(deletePassword) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
