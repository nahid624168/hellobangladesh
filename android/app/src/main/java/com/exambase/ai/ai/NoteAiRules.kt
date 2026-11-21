package com.exambase.ai.ai

/** Whether the AI works on the selected part of a note or on the whole note. */
enum class AiScope { SELECTION, WHOLE_NOTE }

/**
 * What the AI was asked about, captured when the sheet opens.
 * [start]/[end] are the range in the note body at that moment (the whole body for [AiScope.WHOLE_NOTE]).
 */
data class AiTarget(val scope: AiScope, val text: String, val start: Int, val end: Int)

enum class AiApplyMode { INSERT_BELOW, REPLACE }

data class AiApplyResult(
    val body: String,
    /** Where the cursor should go: just after the inserted text. */
    val cursor: Int,
    /** True when the note changed while the AI worked, so the answer was added at the end instead of replacing. */
    val fellBackToEnd: Boolean,
)

/** Pure text rules for using an AI answer in a note. No Android types, so plain JUnit tests them. */
object NoteAiRules {
    /** The selection if there is one (ignoring whitespace-only), otherwise the whole note. */
    fun target(body: String, selStart: Int, selEnd: Int): AiTarget {
        val a = minOf(selStart, selEnd).coerceIn(0, body.length)
        val b = maxOf(selStart, selEnd).coerceIn(0, body.length)
        return if (b > a && body.substring(a, b).isNotBlank()) {
            AiTarget(AiScope.SELECTION, body.substring(a, b), a, b)
        } else {
            AiTarget(AiScope.WHOLE_NOTE, body, 0, body.length)
        }
    }

    /**
     * Puts [output] into [body].
     * - INSERT_BELOW: after the target text (after the selection, or at the end of the note), separated by a blank line.
     * - REPLACE: replaces the target text.
     * If the note no longer has the original text at the original place (the user kept typing while the AI worked),
     * nothing is overwritten: the answer is added at the end and [AiApplyResult.fellBackToEnd] is true.
     */
    fun apply(body: String, target: AiTarget, output: String, mode: AiApplyMode): AiApplyResult {
        val text = output.trim()
        val stillThere = target.end <= body.length && target.start <= target.end && body.substring(target.start, target.end) == target.text
        if (!stillThere) return appendAtEnd(body, text, fellBack = true)
        return when (mode) {
            AiApplyMode.REPLACE -> {
                val newBody = body.substring(0, target.start) + text + body.substring(target.end)
                AiApplyResult(newBody, target.start + text.length, false)
            }
            AiApplyMode.INSERT_BELOW ->
                if (target.scope == AiScope.WHOLE_NOTE) appendAtEnd(body, text, fellBack = false)
                else {
                    val head = body.substring(0, target.end)
                    val tail = body.substring(target.end)
                    val inserted = "\n\n$text"
                    // Keep the original line break after the selection if there was one.
                    val joined = head + inserted + (if (tail.isEmpty() || tail.startsWith("\n")) "" else "\n\n") + tail
                    AiApplyResult(joined, head.length + inserted.length, false)
                }
        }
    }

    private fun appendAtEnd(body: String, text: String, fellBack: Boolean): AiApplyResult {
        val sep = when {
            body.isBlank() -> ""
            body.endsWith("\n\n") -> ""
            body.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val newBody = (if (body.isBlank()) "" else body) + sep + text
        return AiApplyResult(newBody, newBody.length, fellBack)
    }

    /** Title for "save as a new note": first meaningful line of the answer without markdown marks, at most 60 characters. */
    fun newNoteTitle(output: String, fallback: String): String {
        val line = output.lineSequence()
            .map { it.trim().trimStart('#', '*', '-', '>', '•', ' ', '_').trim().trimEnd('*', '_', ' ') }
            .firstOrNull { it.isNotEmpty() } ?: return fallback
        return if (line.length <= 60) line else line.take(59).trimEnd() + "…"
    }

    /** The answer can be used only once it has text; an emptied answer disables the buttons. */
    fun canApply(output: String): Boolean = output.isNotBlank()
}
