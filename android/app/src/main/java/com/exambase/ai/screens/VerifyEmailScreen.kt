package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.exambase.ai.R
import com.exambase.ai.auth.FormErrorText
import com.exambase.ai.auth.VerifyEmailViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun VerifyEmailScreen(onDone: () -> Unit) {
    val vm = appViewModel { VerifyEmailViewModel(it.authRepository) }
    val state by vm.state.collectAsState()
    var code by remember { mutableStateOf("") }
    var resent by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        // Both verify and resend report success; only leave the screen after a real verification.
        if (state.success && !resent) onDone() else if (state.success) vm.consumeSuccess()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.verify_body, vm.email ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            ExamBaseTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = stringResource(R.string.code_label),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            if (resent) Text(stringResource(R.string.code_sent), color = MaterialTheme.colorScheme.secondary)
            FormErrorText(state)
            PrimaryButton(
                text = stringResource(R.string.verify),
                loading = state.loading,
                enabled = code.length == 6,
                onClick = { resent = false; vm.verify(code) },
                modifier = Modifier.fillMaxWidth(),
            )
            LinkButton(stringResource(R.string.resend_code), onClick = { resent = true; vm.resend() })
            LinkButton(stringResource(R.string.later), onDone)
        }
    }
}
