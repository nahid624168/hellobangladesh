package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.exambase.ai.academic.MdBlock
import com.exambase.ai.academic.parseInline
import com.exambase.ai.academic.parseMarkdown
import com.exambase.core.designsystem.theme.Spacing

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    parseInline(text).forEach { span ->
        if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
    }
}

/** Renders the lesson Markdown subset documented in SimpleMarkdown.kt. */
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parseMarkdown(source) }
    val typography = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text),
                    style = when (block.level) {
                        1 -> typography.headlineSmall
                        2 -> typography.titleLarge
                        else -> typography.titleMedium
                    },
                )
                is MdBlock.Paragraph -> Text(inline(block.text), style = typography.bodyLarge)
                is MdBlock.Bullet -> Text(AnnotatedString("•  ") + inline(block.text), style = typography.bodyLarge)
                is MdBlock.Numbered -> Text(AnnotatedString("${block.number}.  ") + inline(block.text), style = typography.bodyLarge)
            }
        }
    }
}
