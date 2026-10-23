package com.github.phoswald.localbrowser

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders the Mustache templates in the resources folder "templates".
 *
 * Contexts must consist of maps, lists, strings and booleans only. For other objects, jmustache
 * reads fields and getters via reflection, which breaks once R8 renames or removes them.
 */
object Templates {

    // HTML-escapes {{x}} (but not {{{x}}}) and throws on missing keys.
    private val compiler = Mustache.compiler()

    // The WebView calls the path handlers on multiple threads.
    private val cache = ConcurrentHashMap<String, Template>()

    fun text(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/templates/$name")) { "Missing template $name" }
            .reader(Charsets.UTF_8).use { it.readText() }

    fun render(name: String, context: Map<String, Any>): String =
        cache.computeIfAbsent(name) { compiler.compile(text(it)) }.execute(context)
}
