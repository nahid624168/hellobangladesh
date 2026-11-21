package com.exambase.ai.questions

import com.exambase.core.network.CheckAnswerRequest
import com.exambase.core.network.CheckResults
import com.exambase.core.network.QuestionTypes
import java.util.Locale

/** Pure rules for answering a question. The server re-validates everything; these only keep the UI honest. */
object PracticeRules {
    const val MAX_TEXT = 5000

    fun isSingle(type: String) = type == QuestionTypes.SINGLE_CHOICE || type == QuestionTypes.TRUE_FALSE || type == QuestionTypes.IMAGE_BASED
    fun isMultiple(type: String) = type == QuestionTypes.MULTIPLE_SELECT
    fun isChoice(type: String) = isSingle(type) || isMultiple(type)

    /** Single-answer types replace the choice; multiple select toggles it. */
    fun toggle(type: String, selected: Set<String>, optionId: String): Set<String> = when {
        isSingle(type) -> setOf(optionId)
        optionId in selected -> selected - optionId
        else -> selected + optionId
    }

    fun canSubmit(type: String, selected: Set<String>, text: String): Boolean = when {
        isSingle(type) -> selected.size == 1
        isMultiple(type) -> selected.isNotEmpty()
        else -> text.isNotBlank()
    }

    fun request(type: String, selected: Set<String>, text: String): CheckAnswerRequest =
        if (isChoice(type)) CheckAnswerRequest(selectedOptionIds = selected.toList()) else CheckAnswerRequest(answerText = text.trim())

    /** Locale-independent on purpose: a Bangla device must not turn "0.25" into other digits or a comma. */
    fun formatMarks(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString()
        else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

    fun formatAwarded(value: Double): String = if (value > 0) "+" + formatMarks(value) else formatMarks(value)
}

data class Tally(val correct: Int = 0, val incorrect: Int = 0, val ungraded: Int = 0) {
    val answered: Int get() = correct + incorrect + ungraded

    fun add(result: String) = when (result) {
        CheckResults.CORRECT -> copy(correct = correct + 1)
        CheckResults.INCORRECT -> copy(incorrect = incorrect + 1)
        else -> copy(ungraded = ungraded + 1)
    }
}
