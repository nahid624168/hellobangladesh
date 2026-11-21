package com.exambase.ai.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.exambase.core.designsystem.theme.Spacing
import com.exambase.core.network.QuestionImageDto

/** Renders question / explanation images with Coil. Falls back to alt text if the URL fails. */
@Composable
fun QuestionImages(images: List<QuestionImageDto>, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    val context = LocalContext.current
    images.forEach { img ->
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(img.url)
                .crossfade(true)
                .build(),
            contentDescription = img.altText ?: img.altTextBn,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .padding(vertical = Spacing.xs),
        )
        val alt = img.altText ?: img.altTextBn
        if (!alt.isNullOrBlank()) {
            Text(
                alt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
