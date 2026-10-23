package com.github.phoswald.localbrowser

import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URI

const val HOST = "appassets.androidplatform.net"
const val BASE_URL = "https://$HOST/"
const val ASSETS_PATH = "/.localbrowser/"

/**
 * Serves files below [root] for [WebViewAssetLoader], registered on path "/".
 * Directories are served as their index.html or as a generated listing.
 */
class LocalFileHandler(private val root: File) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse =
        when (val res = resolve(root, path, MimeTypeMap.getSingleton()::getMimeTypeFromExtension)) {
            is Resolution.Serve -> WebResourceResponse(res.mimeType, null, FileInputStream(res.file))
            is Resolution.Markdown -> html(200, "OK", MarkdownPage.render(res.file.name, res.file.readText()))
            is Resolution.Listing -> html(200, "OK", DirectoryListing.render(res.dir, res.urlPath, res.dir == root.canonicalFile))
            is Resolution.Redirect -> html(200, "OK", redirectPage(res.location))
            Resolution.Forbidden -> html(403, "Forbidden", errorPage("403 Forbidden"))
            Resolution.NotFound -> html(404, "Not Found", errorPage("404 Not Found"))
        }

    private fun html(status: Int, reason: String, body: String) = WebResourceResponse(
        "text/html", "utf-8", status, reason, emptyMap(),
        ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
    )
}

sealed interface Resolution {
    data class Serve(val file: File, val mimeType: String) : Resolution
    data class Markdown(val file: File) : Resolution
    data class Listing(val dir: File, val urlPath: String) : Resolution
    data class Redirect(val location: String) : Resolution
    data object Forbidden : Resolution
    data object NotFound : Resolution
}

/**
 * Maps a decoded request path relative to [root] (no leading slash, as passed by
 * [WebViewAssetLoader]) to what should be served.
 */
fun resolve(root: File, path: String, fallbackMime: (String) -> String? = { null }): Resolution {
    val canonicalRoot = root.canonicalFile
    val file = File(canonicalRoot, path).canonicalFile
    if (file != canonicalRoot && !file.path.startsWith(canonicalRoot.path + File.separator)) {
        return Resolution.Forbidden
    }
    if (file.isDirectory) {
        if (path.isNotEmpty() && !path.endsWith("/")) {
            // Relative links only resolve correctly against a URL ending in a slash.
            return Resolution.Redirect(encodePath("/$path/"))
        }
        val index = File(file, "index.html")
        if (index.isFile) {
            return Resolution.Serve(index, "text/html")
        }
        return Resolution.Listing(file, "/$path")
    }
    if (file.isFile) {
        if (file.extension.equals("md", ignoreCase = true)) {
            return Resolution.Markdown(file)
        }
        return Resolution.Serve(file, mimeType(file.name, fallbackMime))
    }
    return Resolution.NotFound
}

private val MIME_TYPES = mapOf(
    "html" to "text/html",
    "htm" to "text/html",
    "xhtml" to "application/xhtml+xml",
    "js" to "text/javascript",
    "mjs" to "text/javascript",
    "css" to "text/css",
    "json" to "application/json",
    "map" to "application/json",
    "xml" to "application/xml",
    "txt" to "text/plain",
    "md" to "text/markdown",
    "csv" to "text/csv",
    "svg" to "image/svg+xml",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "ico" to "image/x-icon",
    "wasm" to "application/wasm",
    "woff" to "font/woff",
    "woff2" to "font/woff2",
    "ttf" to "font/ttf",
    "otf" to "font/otf",
    "pdf" to "application/pdf",
    "mp3" to "audio/mpeg",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
)

fun mimeType(name: String, fallbackMime: (String) -> String? = { null }): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MIME_TYPES[ext] ?: fallbackMime(ext) ?: "application/octet-stream"
}

/** Percent-encodes everything except unreserved characters and '/'. */
fun encodePath(path: String): String = buildString {
    for (b in path.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xff
        if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-._~/") {
            append(c.toChar())
        } else {
            append('%').append("%02X".format(c))
        }
    }
}

/** Converts a WebView URL to the path relative to the storage root shown in the address bar. */
fun urlToDisplayPath(url: String): String {
    val uri = URI(url)
    if (uri.host != HOST) return url
    return uri.path ?: "/"
}

/** Converts address bar input (relative to [root], or absolute including it) to a WebView URL. */
fun displayPathToUrl(root: File, input: String): String {
    val rel = input.trim().removePrefix(root.path).trimStart('/')
    return BASE_URL + encodePath(rel)
}

private fun redirectPage(location: String) =
    """<!DOCTYPE html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="0; url=$location"><script>location.replace("$location")</script></head><body></body></html>"""

private fun errorPage(message: String) =
    """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>$message</title></head><body><h1>$message</h1></body></html>"""
