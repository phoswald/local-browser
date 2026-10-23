package com.github.phoswald.localbrowser

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    @Test
    fun error_escapesMessage() {
        val html = Templates.render("error.html", mapOf("message" to "<b>&"))
        assertTrue(html.contains("<title>&lt;b&gt;&amp;</title>"))
        assertTrue(html.contains("<h1>&lt;b&gt;&amp;</h1>"))
    }

    @Test
    fun redirect_containsLocation() {
        val html = Templates.render("redirect.html", mapOf("location" to "/a%20b/"))
        assertTrue(html.contains("""content="0; url=/a%20b/""""))
        assertTrue(html.contains("""location.replace("/a%20b/")"""))
    }

    @Test
    fun missingTemplate_fails() {
        assertThrows(IllegalStateException::class.java) { Templates.render("nope.html", emptyMap()) }
    }
}
