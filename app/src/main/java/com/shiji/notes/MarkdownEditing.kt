package com.shiji.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object MarkdownEditing {
    fun apply(value: TextFieldValue, prefix: String, suffix: String = "", linePrefix: Boolean = false): TextFieldValue {
        val text = value.text
        val start = value.selection.min.coerceIn(0, text.length)
        val end = value.selection.max.coerceIn(start, text.length)
        if (linePrefix) {
            val lineStart = if (start == 0) 0 else text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
            val selectedEnd = if (start == end) text.indexOf('\n', start).let { if (it < 0) text.length else it } else end
            val block = text.substring(lineStart, selectedEnd)
            val replacement = block.split('\n').joinToString("\n") { prefix + it }
            val result = text.replaceRange(lineStart, selectedEnd, replacement)
            val delta = replacement.length - block.length
            val selectionStart = start + prefix.length
            val selectionEnd = if (start == end) selectionStart else end + delta
            return TextFieldValue(result, TextRange(selectionStart.coerceIn(0, result.length), selectionEnd.coerceIn(0, result.length)))
        }
        val selected = text.substring(start, end)
        val content = selected.ifEmpty { "文本" }
        val replacement = prefix + content + suffix
        val result = text.replaceRange(start, end, replacement)
        val selectionStart = start + prefix.length
        return TextFieldValue(result, TextRange(selectionStart, selectionStart + content.length))
    }
}
