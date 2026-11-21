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
import com.exambase.ai.auth.RegisterViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.auth.message
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBackToLogin: () -> Unit) {
    val vm = appViewModel { RegisterViewModel(it.authRepository) }
    val state by vm.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<FieldError?>(null) }
    var emailError by remember { mutableStateOf<FieldError?>(null) }
    var passwordError by remember { mutableStateOf<FieldError?>(null) }
    var confirmError by remember { mutableStateOf<FieldError?>(null) }

    LaunchedEffect(state.success) { if (state.success) onRegistered() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.register),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            ExamBaseTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = stringResource(R.string.display_name),
                errorMessage = nameError.message() ?: state.fieldErrors["displayName"],
                modifier = Modifier.fillMaxWidth(),
            )
            ExamBaseTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = stringResource(R.string.email),
                errorMessage = emailError.message() ?: state.fieldErrors["email"],
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth(),
            )
            ExamBaseTextField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = stringResource(R.string.password),
                errorMessage = passwordError.message() ?: state.fieldErrors["password"],
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExamBaseTextField(
                value = confirm,
                onValueChange = { confirm = it; confirmError = null },
                label = stringResource(R.string.confirm_password),
                errorMessage = confirmError.message(),
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FormErrorText(state)
            PrimaryButton(
                text = stringResource(R.string.register),
                loading = state.loading,
                onClick = {
                    nameError = AuthValidators.required(name)
                    emailError = AuthValidators.email(email)
                    passwordError = AuthValidators.password(password)
                    confirmError = AuthValidators.confirmPassword(password, confirm)
                    if (listOf(nameError, emailError, passwordError, confirmError).all { it == null }) {
                        vm.register(name, email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LinkButton(stringResource(R.string.have_account), onBackToLogin)
        }
    }
}
