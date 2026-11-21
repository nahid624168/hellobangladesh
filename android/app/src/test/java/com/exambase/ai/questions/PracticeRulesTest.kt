package com.exambase.ai.questions

import com.exambase.core.network.CheckResults
import com.exambase.core.network.QuestionTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRulesTest {
    @Test fun singleAnswerTypesReplaceTheChoice() {
        listOf(QuestionTypes.SINGLE_CHOICE, QuestionTypes.TRUE_FALSE, QuestionTypes.IMAGE_BASED).forEach {
            assertEquals(setOf("b"), PracticeRules.toggle(it, setOf("a"), "b"))
        }
    }

    @Test fun multipleSelectTogglesChoices() {
        val one = PracticeRules.toggle(QuestionTypes.MULTIPLE_SELECT, emptySet(), "a")
        val two = PracticeRules.toggle(QuestionTypes.MULTIPLE_SELECT, one, "b")
        assertEquals(setOf("a", "b"), two)
        assertEquals(setOf("b"), PracticeRules.toggle(QuestionTypes.MULTIPLE_SELECT, two, "a"))
    }

    @Test fun submitNeedsAValidAnswerForTheType() {
        assertFalse(PracticeRules.canSubmit(QuestionTypes.SINGLE_CHOICE, emptySet(), ""))
        assertTrue(PracticeRules.canSubmit(QuestionTypes.SINGLE_CHOICE, setOf("a"), ""))
        assertFalse(PracticeRules.canSubmit(QuestionTypes.MULTIPLE_SELECT, emptySet(), ""))
        assertTrue(PracticeRules.canSubmit(QuestionTypes.MULTIPLE_SELECT, setOf("a", "b"), ""))
        assertFalse(PracticeRules.canSubmit(QuestionTypes.SHORT_ANSWER, emptySet(), "   "))
        assertTrue(PracticeRules.canSubmit(QuestionTypes.WRITTEN, emptySet(), "essay"))
    }

    @Test fun requestCarriesChoicesOrTrimmedTextNeverBoth() {
        val choice = PracticeRules.request(QuestionTypes.SINGLE_CHOICE, setOf("a"), "ignored")
        assertEquals(listOf("a"), choice.selectedOptionIds)
        assertNull(choice.answerText)
        val text = PracticeRules.request(QuestionTypes.SHORT_ANSWER, setOf("a"), "  Dhaka ")
        assertTrue(text.selectedOptionIds.isEmpty())
        assertEquals("Dhaka", text.answerText)
    }

    @Test fun marksAreFormattedWithoutLocaleSurprises() {
        assertEquals("1", PracticeRules.formatMarks(1.0))
        assertEquals("0.25", PracticeRules.formatMarks(0.25))
        assertEquals("1.5", PracticeRules.formatMarks(1.5))
        assertEquals("+2", PracticeRules.formatAwarded(2.0))
        assertEquals("-0.5", PracticeRules.formatAwarded(-0.5))
        assertEquals("0", PracticeRules.formatAwarded(0.0))
    }

    @Test fun tallyCountsEachResult() {
        val t = Tally().add(CheckResults.CORRECT).add(CheckResults.CORRECT).add(CheckResults.INCORRECT).add(CheckResults.UNGRADED)
        assertEquals(Tally(2, 1, 1), t)
        assertEquals(4, t.answered)
    }
}
