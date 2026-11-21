package com.exambase.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGeneratedContentTest {
    @Test fun parsesFlashcardsAndSkipsMalformedLines() {
        val cards = AiGeneratedContent.flashcards(
            "Photosynthesis || Process plants use to make food\ninvalid\nChlorophyll || Green pigment"
        )
        assertEquals(2, cards.size)
        assertEquals("Photosynthesis", cards[0].front)
        assertEquals("Green pigment", cards[1].back)
    }

    @Test fun flashcardsAreDeduplicatedAndLimitedToTen() {
        val input = (1..12).joinToString("\n") { "Term$it || Meaning$it" } +
            "\nTerm1 || Duplicate"
        val cards = AiGeneratedContent.flashcards(input)
        assertEquals(10, cards.size)
    }

    @Test fun parsesMarkedCorrectOptionAndExplanation() {
        val questions = AiGeneratedContent.questions(
            "Q: What is 2 + 2?\nA) 3\nB) 4 (correct)\nC) 5\nD) 6\nExplanation: Basic addition."
        )
        assertEquals(1, questions.size)
        assertEquals("What is 2 + 2?", questions[0].question)
        assertEquals(1, questions[0].correctOptionIndex)
        assertEquals("Basic addition.", questions[0].explanation)
    }

    @Test fun parsesExplicitCorrectLetter() {
        val questions = AiGeneratedContent.questions(
            "1. Capital of Bangladesh?\nA) Dhaka\nB) Chattogram\nC) Khulna\nD) Rajshahi\nCorrect: A"
        )
        assertEquals(0, questions.single().correctOptionIndex)
    }

    @Test fun rejectsQuestionWithoutOneCorrectAnswer() {
        val questions = AiGeneratedContent.questions(
            "Q: Pick one\nA) First (correct)\nB) Second (correct)"
        )
        assertTrue(questions.isEmpty())
    }

    @Test fun rejectsConflictingDeclaredAndMarkedCorrectAnswers() {
        val questions = AiGeneratedContent.questions(
            "Q: Pick one\nA) First (correct)\nB) Second\nC) Third\nCorrect: B"
        )
        assertTrue(questions.isEmpty())
    }

    @Test fun ignoresMalformedQuestionAndCapsAtFive() {
        val question = (1..7).joinToString("\n") {
            "Q: Question $it?\nA) One\nB) Two\nCorrect: A"
        }
        assertEquals(5, AiGeneratedContent.questions(question).size)
    }
}
