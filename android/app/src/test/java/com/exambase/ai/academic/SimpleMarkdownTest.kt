package com.exambase.ai.academic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleMarkdownTest {
    @Test fun parsesHeadingsParagraphsAndLists() {
        val blocks = parseMarkdown("# Title\n\nFirst line\nsecond line\n\n## Types\n- One\n* Two\n1. Step\n2) Next")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Paragraph("First line second line"),
                MdBlock.Heading(2, "Types"),
                MdBlock.Bullet("One"),
                MdBlock.Bullet("Two"),
                MdBlock.Numbered(1, "Step"),
                MdBlock.Numbered(2, "Next"),
            ),
            blocks,
        )
    }

    @Test fun emptyAndBlankInputGiveNoBlocks() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("  \n\n  ").isEmpty())
    }

    @Test fun windowsLineEndingsAreHandled() {
        assertEquals(listOf(MdBlock.Heading(2, "A"), MdBlock.Bullet("b")), parseMarkdown("## A\r\n- b\r\n"))
    }

    @Test fun hashWithoutSpaceAndFourHashesStayPlainText() {
        assertEquals(listOf(MdBlock.Paragraph("#hashtag")), parseMarkdown("#hashtag"))
        assertEquals(listOf(MdBlock.Paragraph("#### too deep")), parseMarkdown("#### too deep"))
    }

    @Test fun banglaTextIsPreserved() {
        assertEquals(listOf(MdBlock.Heading(1, "সন্ধি কী?"), MdBlock.Bullet("স্বরসন্ধি")), parseMarkdown("# সন্ধি কী?\n- স্বরসন্ধি"))
    }

    @Test fun boldSpans() {
        assertEquals(
            listOf(MdSpan("a ", false), MdSpan("b", true), MdSpan(" c", false)),
            parseInline("a **b** c"),
        )
    }

    @Test fun unmatchedBoldMarkerIsLiteral() {
        assertEquals(listOf(MdSpan("a **b", false)), parseInline("a **b"))
    }

    @Test fun emptyBoldIsDropped() {
        assertEquals("ab", parseInline("a****b").joinToString("") { it.text })
    }
}
