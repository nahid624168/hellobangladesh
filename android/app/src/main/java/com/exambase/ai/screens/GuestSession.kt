package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.exambase.ai.R
import com.exambase.ai.auth.AuthRepository
import com.exambase.ai.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun NavHostController.resetTo(route: String) {
    runCatching { navigate(route) { popUpTo(0) { inclusive = true } } }
}

private fun formatRemaining(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Wraps the whole app. While a guest session is active it shows a countdown banner on top;
 * when the 20 minutes are over the guest is signed out and told so. The server enforces the same
 * deadline, so changing the phone clock cannot extend access to data.
 */
@Composable
fun GuestSessionLayout(
    repo: AuthRepository,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val guestEnd by repo.guestExpiresAt.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var expired by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(guestEnd) {
        val end = guestEnd ?: return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis() / 1000
            if (now >= end) {
                expired = true
                navController.resetTo(Routes.LOGIN)
                repo.endGuestSession() // clears tokens and the guest deadline
                break
            }
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        guestEnd?.let { end ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.guest_banner, formatRemaining(end - now)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch { repo.logout() }
                        navController.resetTo(Routes.REGISTER)
                    }) { Text(stringResource(R.string.guest_sign_up)) }
                }
            }
        }
        // The banner already sits below the status bar, so tell the screens not to pad for it a second time.
        val contentModifier = if (guestEnd != null) Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars) else Modifier.weight(1f)
        Column(contentModifier) { content() }
    }

    if (expired) {
        AlertDialog(
            onDismissRequest = { expired = false },
            title = { Text(stringResource(R.string.guest_expired_title)) },
            text = { Text(stringResource(R.string.guest_expired_message)) },
            confirmButton = {
                TextButton(onClick = { expired = false; navController.resetTo(Routes.REGISTER) }) {
                    Text(stringResource(R.string.guest_expired_register))
                }
            },
            dismissButton = {
                TextButton(onClick = { expired = false }) { Text(stringResource(R.string.guest_expired_login)) }
            },
        )
    }
}
