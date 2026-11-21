package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.exambase.ai.auth.AuthValidators
import com.exambase.ai.auth.FieldError
import com.exambase.ai.auth.FormErrorText
import com.exambase.ai.auth.ResetPasswordViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.auth.message
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.theme.Spacing

/** Two steps: request a code by email, then enter the code and a new password. */
@Composable
fun ResetPasswordScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { ResetPasswordViewModel(it.authRepository) }
    val state by vm.state.collectAsState()
    val step by vm.step.collectAsState()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<FieldError?>(null) }
    var passwordError by remember { mutableStateOf<FieldError?>(null) }
    var codeError by remember { mutableStateOf<FieldError?>(null) }

    // Step 1 success only moves to step 2; step 2 success finishes the flow.
    LaunchedEffect(state.success, step) {
        if (state.success && step == 2 && code.isNotEmpty()) onDone()
        else if (state.success) vm.consumeSuccess()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.reset_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            ExamBaseTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = stringResource(R.string.email),
                errorMessage = emailError.message(),
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth(),
            )
            if (step == 1) {
                FormErrorText(state)
                PrimaryButton(
                    text = stringResource(R.string.send_reset),
                    loading = state.loading,
                    onClick = {
                        emailError = AuthValidators.email(email)
                        if (emailError == null) vm.requestCode(email)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(R.string.reset_enter_code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                ExamBaseTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6); codeError = null },
                    label = stringResource(R.string.code_label),
                    errorMessage = codeError.message(),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExamBaseTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; passwordError = null },
                    label = stringResource(R.string.new_password),
                    errorMessage = passwordError.message() ?: state.fieldErrors["newPassword"],
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormErrorText(state)
                PrimaryButton(
                    text = stringResource(R.string.reset_confirm),
                    loading = state.loading,
                    onClick = {
                        emailError = AuthValidators.email(email)
                        codeError = if (code.length == 6) null else FieldError.EMPTY
                        passwordError = AuthValidators.password(newPassword)
                        if (emailError == null && codeError == null && passwordError == null) {
                            vm.confirm(email, code, newPassword)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LinkButton(stringResource(R.string.back), onBack)
        }
    }
}
