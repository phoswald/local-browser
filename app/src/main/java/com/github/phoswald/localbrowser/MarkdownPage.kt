package com.github.phoswald.localbrowser

object MarkdownPage {

    /** Renders an HTML page that displays [markdown] using the bundled <markdown-text> element. */
    fun render(title: String, markdown: String): String = Templates.render(
        "markdown.html",
        mapOf("title" to title, "assets" to ASSETS_PATH, "markdown" to jsonString(markdown)),
    )

    /** Encodes [s] as a JSON string literal that is also safe inside an inline <script>. */
    fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<', '>', '&', ' ', ' ' -> append("\\u%04x".format(c.code))
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
