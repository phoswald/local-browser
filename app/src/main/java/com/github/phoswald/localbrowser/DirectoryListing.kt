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
            .map { entry ->
                val suffix = if (entry.isDirectory) "/" else ""
                mapOf(
                    "href" to encodePath(entry.name) + suffix,
                    "name" to entry.name + suffix,
                    "size" to if (entry.isDirectory) "" else formatSize(entry.length()),
                    "modified" to dateFormat.format(Instant.ofEpochMilli(entry.lastModified())),
                )
            }
        return Templates.render(
            "listing.html",
            mapOf(
                "title" to "Index of $urlPath",
                "css" to Templates.text("listing.css"),
                "isRoot" to isRoot,
                "entries" to entries,
            ),
        )
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
}
