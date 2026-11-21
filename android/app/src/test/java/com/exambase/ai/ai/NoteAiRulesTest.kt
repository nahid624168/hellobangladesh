package com.exambase.ai.ai

import com.exambase.core.network.AiRunResponse
import com.exambase.core.network.ApiResult
import com.exambase.core.network.FailureKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteAiTargetTest {
    @Test fun `a selection becomes the target with its range`() {
        val t = NoteAiRules.target("Hello brave world", 6, 11)
        assertEquals(AiScope.SELECTION, t.scope)
        assertEquals("brave", t.text)
        assertEquals(6, t.start)
        assertEquals(11, t.end)
    }

    @Test fun `a backwards selection is normalised`() {
        val t = NoteAiRules.target("Hello brave world", 11, 6)
        assertEquals("brave", t.text)
        assertEquals(6, t.start)
    }

    @Test fun `no selection means the whole note`() {
        val t = NoteAiRules.target("Hello world", 3, 3)
        assertEquals(AiScope.WHOLE_NOTE, t.scope)
        assertEquals("Hello world", t.text)
        assertEquals(0, t.start)
        assertEquals(11, t.end)
    }

    @Test fun `a selection of only spaces means the whole note`() {
        assertEquals(AiScope.WHOLE_NOTE, NoteAiRules.target("a   b", 1, 4).scope)
    }

    @Test fun `out of range selection is clamped without crashing`() {
        val t = NoteAiRules.target("abc", -5, 99)
        assertEquals(AiScope.SELECTION, t.scope)
        assertEquals("abc", t.text)
    }

    @Test fun `empty note gives an empty whole-note target`() {
        val t = NoteAiRules.target("", 0, 0)
        assertEquals(AiScope.WHOLE_NOTE, t.scope)
        assertEquals("", t.text)
    }
}

class NoteAiApplyTest {
    private val body = "Hello brave world"
    private val sel = NoteAiRules.target(body, 6, 11)

    @Test fun `replace swaps only the selected text and trims the answer`() {
        val r = NoteAiRules.apply(body, sel, "  Great \n", AiApplyMode.REPLACE)
        assertEquals("Hello Great world", r.body)
        assertEquals(11, r.cursor)
        assertFalse(r.fellBackToEnd)
    }

    @Test fun `insert below a selection in the middle of a line`() {
        val b = "Intro. Key fact. More"
        val t = NoteAiRules.target(b, 7, 16)
        assertEquals("Key fact.", t.text)
        val r = NoteAiRules.apply(b, t, "Answer", AiApplyMode.INSERT_BELOW)
        assertEquals("Intro. Key fact.\n\nAnswer\n\n More", r.body)
        assertEquals("Intro. Key fact.".length + "\n\nAnswer".length, r.cursor)
    }

    @Test fun `insert below a selection that ends a line keeps the line break`() {
        val b = "A\nB"
        val r = NoteAiRules.apply(b, NoteAiRules.target(b, 0, 1), "X", AiApplyMode.INSERT_BELOW)
        assertEquals("A\n\nX\nB", r.body)
        assertEquals(4, r.cursor)
    }

    @Test fun `insert below a selection at the very end`() {
        val r = NoteAiRules.apply(body, NoteAiRules.target(body, 12, 17), "X", AiApplyMode.INSERT_BELOW)
        assertEquals("Hello brave world\n\nX", r.body)
    }

    @Test fun `whole note insert below adds one blank line`() {
        fun go(b: String) = NoteAiRules.apply(b, NoteAiRules.target(b, 0, 0), "X", AiApplyMode.INSERT_BELOW).body
        assertEquals("Note\n\nX", go("Note"))
        assertEquals("Note\n\nX", go("Note\n"))
        assertEquals("Note\n\nX", go("Note\n\n"))
        assertEquals("X", go(""))
    }

    @Test fun `whole note replace swaps everything`() {
        val r = NoteAiRules.apply("Old", NoteAiRules.target("Old", 0, 0), "New", AiApplyMode.REPLACE)
        assertEquals("New", r.body)
        assertEquals(3, r.cursor)
    }

    @Test fun `if the note changed meanwhile the answer goes to the end and nothing is overwritten`() {
        val edited = "Hello wild world"
        val r = NoteAiRules.apply(edited, sel, "X", AiApplyMode.REPLACE)
        assertEquals("Hello wild world\n\nX", r.body)
        assertTrue(r.fellBackToEnd)
        assertEquals(r.body.length, r.cursor)
    }

    @Test fun `a note that became shorter than the target does not crash`() {
        val r = NoteAiRules.apply("Hi", sel, "X", AiApplyMode.INSERT_BELOW)
        assertEquals("Hi\n\nX", r.body)
        assertTrue(r.fellBackToEnd)
    }

    @Test fun `new note title is the first meaningful line without markdown marks`() {
        assertEquals("Summary of cells", NoteAiRules.newNoteTitle("\n## Summary of cells\n- a", "AI note"))
        assertEquals("Bold title", NoteAiRules.newNoteTitle("**Bold title**\nrest", "AI note"))
        assertEquals("item", NoteAiRules.newNoteTitle("- item", "AI note"))
    }

    @Test fun `long title is cut to 60 characters and blank answer uses the fallback`() {
        val t = NoteAiRules.newNoteTitle("x".repeat(100), "AI note")
        assertEquals(60, t.length)
        assertTrue(t.endsWith("…"))
        assertEquals("AI note", NoteAiRules.newNoteTitle("  \n \n", "AI note"))
    }

    @Test fun `apply is allowed only for a non-blank answer`() {
        assertTrue(NoteAiRules.canApply("x"))
        assertFalse(NoteAiRules.canApply("  \n"))
    }
}

/** The note sheet uses the same engine; these check what the sheet depends on. */
class NoteAiSheetEngineTest {
    private fun active() = FakeAiSource(consent = ApiResult.Success(consentDto(active = true)))

    @Test fun `a whole note over the limit is refused locally and nothing is sent`() = runBlocking {
        val src = active()
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("x".repeat(AiRules.MAX_INPUT_CHARS + 1))
        e.run()
        assertEquals(InputProblem.TOO_LONG, e.state.value.inputProblem)
        assertEquals(0, src.runCalls)
    }

    @Test fun `an empty note gives the empty-input problem`() = runBlocking {
        val src = active()
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("")
        e.run()
        assertEquals(InputProblem.EMPTY, e.state.value.inputProblem)
    }

    @Test fun `clearing the output keeps consent and quota but drops the answer`() = runBlocking {
        val src = active()
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("Text")
        e.run()
        assertTrue(e.state.value.hasOutput)
        e.clearOutput()
        val s = e.state.value
        assertFalse(s.hasOutput)
        assertEquals("", s.output)
        assertTrue(AiRules.consentActive(s.consent))
        assertEquals(1, s.quota?.requestsUsed)
    }

    @Test fun `offline run leaves the note text for the editor untouched and shows offline`() = runBlocking {
        val src = active().apply { run = fail(FailureKind.NETWORK, "network") }
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("My note")
        e.run()
        assertEquals(AiError.Offline, e.state.value.error)
        assertEquals("My note", e.state.value.input)
        assertFalse(e.state.value.hasOutput)
    }

    @Test fun `a second run after a first answer replaces the answer`() = runBlocking {
        val src = active()
        val e = AiAssistantEngine(src)
        e.load()
        e.onInput("Text")
        e.run()
        src.run = ApiResult.Success(AiRunResponse("Second", null, quotaDto(used = 2), "n"))
        e.run()
        assertEquals("Second", e.state.value.output)
        assertEquals(2, src.runCalls)
    }
}
