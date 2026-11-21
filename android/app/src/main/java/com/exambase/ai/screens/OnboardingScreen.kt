package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.exambase.ai.R
import com.exambase.core.designsystem.components.LinkButton
import com.exambase.core.designsystem.components.PrimaryButton
import com.exambase.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

private data class OnboardingPage(val titleRes: Int, val bodyRes: Int)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_title_1, R.string.onboarding_body_1),
    OnboardingPage(R.string.onboarding_title_2, R.string.onboarding_body_2),
    OnboardingPage(R.string.onboarding_title_3, R.string.onboarding_body_3),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(Spacing.lg)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LinkButton(stringResource(R.string.skip), onClick = onDone)
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(page.bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
            PrimaryButton(
                text = stringResource(if (isLast) R.string.get_started else R.string.continue_label),
                onClick = {
                    if (isLast) onDone()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
