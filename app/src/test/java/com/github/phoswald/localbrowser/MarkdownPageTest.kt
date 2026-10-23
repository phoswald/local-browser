package com.github.phoswald.localbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPageTest {

    @Test
    fun jsonString_escapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\"", MarkdownPage.jsonString("a\"b\\c\nd\te"))
        assertEquals("\"\\u003c/script\\u003e\"", MarkdownPage.jsonString("</script>"))
        assertEquals("\"\\u0026\\u2028\\u0001\"", MarkdownPage.jsonString("&\u2028\u0001"))
        assertEquals("\"äé # x\"", MarkdownPage.jsonString("äé # x"))
    }

    @Test
    fun render_embedsMarkdownAndAssets() {
        val page = MarkdownPage.render("<a>.md", "# Hi\n</script><script>alert(1)</script>")
        assertTrue(page.contains("<title>&lt;a&gt;.md</title>"))
        assertTrue(page.contains("""href="/.localbrowser/markdown/defaults.css""""))
        assertTrue(page.contains("""href="/.localbrowser/markdown/markdown.css""""))
        assertTrue(page.contains("""src="/.localbrowser/markdown/markdown.js""""))
        assertTrue(page.contains(""".markdown = "# Hi\n\u003c/script\u003e"""))
        assertFalse(page.contains("alert(1)</script>"))
    }
}
