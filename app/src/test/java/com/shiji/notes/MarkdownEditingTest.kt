package com.shiji.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditingTest {
    @Test fun wrapsSelectedText() {
        val result = MarkdownEditing.apply(TextFieldValue("hello world", TextRange(6, 11)), "**", "**")
        assertEquals("hello **world**", result.text)
        assertEquals(TextRange(8, 13), result.selection)
    }

    @Test fun insertsAtCursor() {
        val result = MarkdownEditing.apply(TextFieldValue("hello world", TextRange(5)), "`", "`")
        assertEquals("hello`文本` world", result.text)
        assertEquals(TextRange(6, 8), result.selection)
    }

    @Test fun prefixesSelectedLines() {
        val result = MarkdownEditing.apply(TextFieldValue("one\ntwo\nthree", TextRange(0, 7)), "- ", linePrefix = true)
        assertEquals("- one\n- two\nthree", result.text)
    }
}
