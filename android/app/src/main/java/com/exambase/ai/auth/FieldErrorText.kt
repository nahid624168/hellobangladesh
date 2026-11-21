package com.exambase.ai.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R

@Composable
fun FieldError?.message(): String? = when (this) {
    null -> null
    FieldError.EMPTY -> stringResource(R.string.err_empty)
    FieldError.INVALID_EMAIL -> stringResource(R.string.err_email)
    FieldError.WEAK_PASSWORD -> stringResource(R.string.err_password)
    FieldError.MISMATCH -> stringResource(R.string.err_mismatch)
}
