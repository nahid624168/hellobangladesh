package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
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
import com.exambase.ai.auth.LoginViewModel
import com.exambase.ai.auth.appViewModel
import com.exambase.ai.auth.message
import com.exambase.core.designsystem.components.ExamBaseTextField
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.components.SecondaryButton
import com.exambase.core.designsystem.theme.Spacing

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onGuest: () -> Unit = {},
) {
    val vm = appViewModel { LoginViewModel(it.authRepository) }
    val state by vm.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<FieldError?>(null) }
    var passwordError by remember { mutableStateOf<FieldError?>(null) }

    var guestRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.success) {
        if (state.success) { if (guestRequested) onGuest() else onLoggedIn() }
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                // Readable on tablets too: the form never gets wider than 480dp.
                Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xl),
                )
                Text(
                    stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.login_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.md),
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
                FormErrorText(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LinkButton(stringResource(R.string.forgot_password), onForgotPassword)
                }
                PrimaryButton(
                    text = stringResource(R.string.login),
                    loading = state.loading && !guestRequested,
                    onClick = {
                        guestRequested = false
                        emailError = AuthValidators.email(email)
                        passwordError = AuthValidators.required(password)
                        if (emailError == null && passwordError == null) vm.login(email, password)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                LinkButton(stringResource(R.string.no_account), onRegister, Modifier.align(Alignment.CenterHorizontally))

                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Text(
                        stringResource(R.string.or_divider),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                SecondaryButton(
                    text = stringResource(R.string.continue_as_guest),
                    onClick = { guestRequested = true; vm.continueAsGuest() },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.guest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
