package com.exambase.backend.questions

import com.exambase.backend.ApiException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GraderTest {
    private val a = UUID.randomUUID(); private val b = UUID.randomUUID(); private val c = UUID.randomUUID()

    private fun key(type: String, correct: Set<UUID> = setOf(b), answers: List<String> = emptyList(), neg: Double = 0.25) = AnswerKey(
        UUID.randomUUID(), UUID.randomUUID(), type, 1, 2.0, neg,
        if (type == QuestionType.SHORT_ANSWER || type == QuestionType.WRITTEN) emptySet() else setOf(a, b, c),
        correct, answers, "why", null, emptyList(),
    )

    private fun req(vararg ids: UUID, text: String? = null) = CheckAnswerRequest(ids.map { it.toString() }, text)
    private fun field(block: () -> Unit) = assertFailsWith<ApiException> { block() }.fields!!.keys.single()

    @Test fun normalizeFoldsCaseAndSpacing() = assertEquals("dhaka city", Grader.normalize("  DHAKA \t City "))

    @Test fun singleChoiceCorrectAndIncorrect() {
        val k = key(QuestionType.SINGLE_CHOICE)
        assertEquals(CheckResult.CORRECT, Grader.grade(k, Grader.validate(k, req(b))).result)
        val wrong = Grader.grade(k, Grader.validate(k, req(a)))
        assertEquals(CheckResult.INCORRECT, wrong.result)
        assertEquals(-0.25, wrong.awardedMarks)
    }

    @Test fun correctAwardsMarksAndZeroPenaltyIsPositiveZero() {
        val k = key(QuestionType.TRUE_FALSE, neg = 0.0)
        assertEquals(2.0, Grader.grade(k, Grader.validate(k, req(b))).awardedMarks)
        val zero = Grader.grade(k, Grader.validate(k, req(a))).awardedMarks
        assertEquals("0.0", zero.toString())
    }

    @Test fun multipleSelectIsAllOrNothing() {
        val k = key(QuestionType.MULTIPLE_SELECT, correct = setOf(a, b))
        assertEquals(CheckResult.CORRECT, Grader.grade(k, Grader.validate(k, req(b, a))).result)
        assertEquals(CheckResult.INCORRECT, Grader.grade(k, Grader.validate(k, req(a))).result)
        assertEquals(CheckResult.INCORRECT, Grader.grade(k, Grader.validate(k, req(a, b, c))).result)
    }

    @Test fun shortAnswerIgnoresCaseAndSpacing() {
        val k = key(QuestionType.SHORT_ANSWER, answers = listOf("Dhaka", "Dacca"))
        assertEquals(CheckResult.CORRECT, Grader.grade(k, Grader.validate(k, req(text = "  dhaka "))).result)
        assertEquals(CheckResult.CORRECT, Grader.grade(k, Grader.validate(k, req(text = "DACCA"))).result)
        assertEquals(CheckResult.INCORRECT, Grader.grade(k, Grader.validate(k, req(text = "Chattogram"))).result)
    }

    @Test fun writtenIsUngradedWithNoMarks() {
        val k = key(QuestionType.WRITTEN)
        val g = Grader.grade(k, Grader.validate(k, req(text = "my essay")))
        assertEquals(CheckResult.UNGRADED, g.result)
        assertEquals(0.0, g.awardedMarks)
    }

    @Test fun inputRulesNameTheField() {
        val single = key(QuestionType.SINGLE_CHOICE)
        assertEquals("selectedOptionIds", field { Grader.validate(single, req()) })
        assertEquals("selectedOptionIds", field { Grader.validate(single, req(a, b)) })
        assertEquals("selectedOptionIds", field { Grader.validate(single, req(UUID.randomUUID())) })
        assertEquals("selectedOptionIds", field { Grader.validate(single, CheckAnswerRequest(listOf("nope"))) })
        assertEquals("answerText", field { Grader.validate(single, req(a, text = "x")) })
        assertEquals("selectedOptionIds", field { Grader.validate(key(QuestionType.MULTIPLE_SELECT), req(a, a)) })
        val short = key(QuestionType.SHORT_ANSWER)
        assertEquals("answerText", field { Grader.validate(short, req(text = "   ")) })
        assertEquals("answerText", field { Grader.validate(short, req(text = "x".repeat(Grader.MAX_SHORT + 1))) })
        assertEquals("selectedOptionIds", field { Grader.validate(short, req(a)) })
        assertTrue(Grader.validate(key(QuestionType.WRITTEN), req(text = "x".repeat(Grader.MAX_WRITTEN))).text!!.length == Grader.MAX_WRITTEN)
    }
}
