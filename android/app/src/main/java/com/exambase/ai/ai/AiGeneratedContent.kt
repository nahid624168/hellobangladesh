package com.exambase.ai.ai

/**
 * Safe, editable drafts parsed from the existing Step 32 AI task output.
 * These are deliberately client-side drafts: they are not published question-bank records.
 */
data class GeneratedFlashcardDraft(
    val front: String,
    val back: String,
)

data class GeneratedQuestionDraft(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
)

object AiGeneratedContent {
    /**
     * Parses the server's `Question || Answer` format. Empty/malformed lines are ignored.
     * A maximum of 10 cards mirrors the server prompt.
     */
    fun flashcards(raw: String): List<GeneratedFlashcardDraft> =
        raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("||", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val front = parts[0].trim().removePrefix("-").trim()
                val back = parts[1].trim()
                if (front.isBlank() || back.isBlank()) null
                else GeneratedFlashcardDraft(front, back)
            }
            .distinctBy { it.front.lowercase() }
            .take(10)
            .toList()

    /**
     * Parses a predictable plain-text MCQ layout:
     * Q: question
     * A) option
     * B) option
     * C) option
     * D) option
     * Correct: B
     * Explanation: ...
     *
     * The parser also accepts `(correct)` after an option, as requested by the Step 32 prompt.
     * A question is retained only when it has 2-6 options and one unambiguous correct answer.
     */
    fun questions(raw: String): List<GeneratedQuestionDraft> {
        val result = mutableListOf<GeneratedQuestionDraft>()
        var question: String? = null
        val options = mutableListOf<String>()
        var markedCorrect: Int? = null
        var declaredCorrect: Int? = null
        var explanation = ""

        fun flush() {
            val q = question?.trim().orEmpty()
            val correct = when {
                markedCorrect == -1 || declaredCorrect == -1 -> null
                declaredCorrect != null && markedCorrect != null && declaredCorrect != markedCorrect -> null
                else -> declaredCorrect ?: markedCorrect
            }
            if (q.isNotBlank() && options.size in 2..6 && correct != null && correct in options.indices) {
                result += GeneratedQuestionDraft(q, options.toList(), correct, explanation.trim())
            }
            question = null
            options.clear()
            markedCorrect = null
            declaredCorrect = null
            explanation = ""
        }

        raw.lineSequence().forEach { original ->
            val line = original.trim()
            if (line.isBlank()) return@forEach
            val qMatch = Regex("^(?:Q\\s*[:.)-]\\s*|\\d+[.)]\\s*)(.+)$", RegexOption.IGNORE_CASE).matchEntire(line)
            if (qMatch != null) {
                if (question != null) flush()
                question = qMatch.groupValues[1].trim()
                return@forEach
            }
            val optionMatch = Regex("^([A-F])[).:]\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(line)
            if (optionMatch != null && question != null) {
                val value = optionMatch.groupValues[2].trim()
                val marked = value.contains("(correct)", ignoreCase = true)
                options += value.replace(Regex("\\s*\\(correct\\)", RegexOption.IGNORE_CASE), "").trim()
                if (marked) {
                    val index = options.lastIndex
                    if (markedCorrect != null) markedCorrect = -1 else markedCorrect = index
                }
                return@forEach
            }
            val correctMatch = Regex("^(?:correct|answer)\\s*[:=-]\\s*([A-F])\\b", RegexOption.IGNORE_CASE).find(line)
            if (correctMatch != null) {
                val index = correctMatch.groupValues[1].uppercase()[0] - 'A'
                if (declaredCorrect != null) declaredCorrect = -1 else declaredCorrect = index
                return@forEach
            }
            val explanationMatch = Regex("^(?:explanation|why)\\s*[:=-]\\s*(.*)$", RegexOption.IGNORE_CASE).find(line)
            if (explanationMatch != null) {
                explanation = explanationMatch.groupValues[1].trim()
            }
        }
        if (question != null) flush()
        return result.take(5)
    }
}
