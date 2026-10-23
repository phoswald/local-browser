package com.github.phoswald.localbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryListingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun render_ordersDirectoriesFirstAndEscapes() {
        val dir = tmp.newFolder("d")
        dir.resolve("b.txt").writeText("12345")
        dir.resolve("A <x>&y.html").writeText("")
        dir.resolve("zdir").mkdir()
        dir.resolve(".hidden").writeText("")

        val html = DirectoryListing.render(dir, "/d/")

        assertTrue(html.contains("""<a href="zdir/">zdir/</a>"""))
        assertTrue(html.contains("""<a href="A%20%3Cx%3E%26y.html">A &lt;x&gt;&amp;y.html</a>"""))
        assertTrue(html.contains("""<a href=".hidden">.hidden</a>"""))
        assertTrue(html.contains("<td>5 B</td>"))
        val order = listOf("zdir/", ".hidden", "A%20", "b.txt").map { html.indexOf("href=\"$it") }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun formatSize() {
        assertEquals("1023 B", DirectoryListing.formatSize(1023))
        assertEquals("1.0 KiB", DirectoryListing.formatSize(1024))
        assertEquals("1.5 MiB", DirectoryListing.formatSize(1024 * 1024 * 3 / 2))
    }
}
