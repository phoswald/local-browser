package com.github.phoswald.localbrowser

object MarkdownPage {

    private const val BASE = "${ASSETS_PATH}markdown/"

    /** Renders an HTML page that displays [markdown] using the bundled <markdown-text> element. */
    fun render(title: String, markdown: String): String = buildString {
        append("""<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">""")
        append("""<meta name="viewport" content="width=device-width, initial-scale=1">""")
        append("<title>").append(DirectoryListing.escape(title)).append("</title>")
        append("""<link rel="stylesheet" href="${BASE}defaults.css">""")
        append("""<link rel="stylesheet" href="${BASE}markdown.css">""")
        append("""<script src="${BASE}markdown.js" type="module"></script>""")
        append("</head><body><markdown-text></markdown-text>")
        // Module scripts run in document order, so <markdown-text> is defined by now.
        append("""<script type="module">document.querySelector('markdown-text').markdown = """)
        append(jsonString(markdown)).append(";</script>")
        append("</body></html>")
    }

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
