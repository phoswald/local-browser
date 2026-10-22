package com.github.phoswald.localbrowser

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DirectoryListing {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    /** Renders an HTML listing of [dir]; [urlPath] is the decoded URL path ending with '/'. */
    fun render(dir: File, urlPath: String, isRoot: Boolean): String {
        val entries = (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val title = escape("Index of $urlPath")
        return buildString {
            append("""<!DOCTYPE html><html><head><meta charset="utf-8">""")
            append("""<meta name="viewport" content="width=device-width, initial-scale=1">""")
            append("<title>").append(title).append("</title>")
            append("<style>").append(STYLE).append("</style></head><body>")
            append("<h1>").append(title).append("</h1><table>")
            append("<tr><th>Name</th><th>Size</th><th>Modified</th></tr>")
            if (!isRoot) {
                append("""<tr><td><a href="../">../</a></td><td></td><td></td></tr>""")
            }
            for (entry in entries) {
                val suffix = if (entry.isDirectory) "/" else ""
                val href = encodePath(entry.name) + suffix
                append("<tr><td><a href=\"").append(href).append("\">")
                append(escape(entry.name + suffix)).append("</a></td>")
                append("<td>").append(if (entry.isDirectory) "" else formatSize(entry.length())).append("</td>")
                append("<td>").append(dateFormat.format(Instant.ofEpochMilli(entry.lastModified()))).append("</td></tr>")
            }
            append("</table></body></html>")
        }
    }

    fun formatSize(size: Long): String {
        if (size < 1024) return "$size B"
        var value = size.toDouble()
        for (unit in listOf("KiB", "MiB", "GiB", "TiB")) {
            value /= 1024
            if (value < 1024 || unit == "TiB") return String.format(Locale.ROOT, "%.1f %s", value, unit)
        }
        error("unreachable")
    }

    fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private const val STYLE = """
        :root { color-scheme: light dark; }
        body { font-family: sans-serif; margin: 8px; }
        h1 { font-size: 1.1em; word-break: break-all; }
        table { border-collapse: collapse; width: 100%; }
        th, td { text-align: left; padding: 6px 4px; border-bottom: 1px solid #8884; }
        td:first-child { word-break: break-all; }
        td:nth-child(2), th:nth-child(2) { text-align: right; white-space: nowrap; }
        td:nth-child(3) { white-space: nowrap; font-size: 0.85em; }
        a { text-decoration: none; }
    """
}
