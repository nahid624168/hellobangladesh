package com.exambase.ai.academic

/**
 * Lesson content is a small Markdown subset so the same text can be edited in the future admin panel and
 * rendered natively: "#"/"##"/"###" headings, "-" or "*" bullets, "1." numbered items, blank-line separated
 * paragraphs, and **bold** inside text. Anything else is shown as plain text, never executed.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: Int, val text: String) : MdBlock
}

/** A run of text, either normal or bold. */
data class MdSpan(val text: String, val bold: Boolean)

private val headingRegex = Regex("^(#{1,3})\\s+(.*)$")
private val bulletRegex = Regex("^[-*]\\s+(.*)$")
private val numberedRegex = Regex("^(\\d{1,3})[.)]\\s+(.*)$")

fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.toString().trim()).also { paragraph.clear() }
    }

    for (raw in source.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        val heading = headingRegex.matchEntire(line)
        val bullet = bulletRegex.matchEntire(line)
        val numbered = numberedRegex.matchEntire(line)
        when {
            line.isEmpty() -> flush()
            heading != null -> { flush(); blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()) }
            bullet != null -> { flush(); blocks += MdBlock.Bullet(bullet.groupValues[1].trim()) }
            numbered != null -> { flush(); blocks += MdBlock.Numbered(numbered.groupValues[1].toInt(), numbered.groupValues[2].trim()) }
            else -> { if (paragraph.isNotEmpty()) paragraph.append(' '); paragraph.append(line) }
        }
    }
    flush()
    return blocks
}

/** Splits "a **b** c" into spans. An unmatched "**" is kept as literal text. */
fun parseInline(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    var rest = text
    while (rest.isNotEmpty()) {
        val open = rest.indexOf("**")
        if (open < 0) { spans += MdSpan(rest, false); break }
        val close = rest.indexOf("**", open + 2)
        if (close < 0) { spans += MdSpan(rest, false); break }
        if (open > 0) spans += MdSpan(rest.substring(0, open), false)
        val bold = rest.substring(open + 2, close)
        if (bold.isNotEmpty()) spans += MdSpan(bold, true)
        rest = rest.substring(close + 2)
    }
    return spans
}
