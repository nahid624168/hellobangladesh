package com.exambase.ai.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.core.network.ApiFailure
import com.exambase.core.network.FailureKind

data class FormState(
    val loading: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val networkError: Boolean = false,
    val success: Boolean = false,
)

fun ApiFailure.toFormState() = FormState(
    fieldErrors = fields,
    errorMessage = message.takeIf { kind != FailureKind.NETWORK && fields.isEmpty() },
    networkError = kind == FailureKind.NETWORK,
)

@Composable
fun FormErrorText(state: FormState, modifier: Modifier = Modifier) {
    val text = when {
        state.networkError -> stringResource(R.string.error_network)
        state.errorMessage != null -> state.errorMessage
        else -> null
    }
    if (text != null) {
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
    }
}
