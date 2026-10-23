package com.github.phoswald.localbrowser

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFileHandlerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setUp() {
        root = tmp.newFolder("root").canonicalFile
        File(root, "site").mkdir()
        File(root, "site/index.html").writeText("<h1>site</h1>")
        File(root, "site/app.mjs").writeText("export {}")
        File(root, "plain").mkdir()
        File(root, "plain/a b&c.txt").writeText("x")
        tmp.newFile("secret.txt")
    }

    @Test
    fun root_isListing() {
        assertEquals(Resolution.Listing(root, "/"), resolve(root, ""))
    }

    @Test
    fun directoryWithIndex_servesIndex() {
        assertEquals(Resolution.Serve(File(root, "site/index.html"), "text/html"), resolve(root, "site/"))
    }

    @Test
    fun directoryWithoutIndex_isListing() {
        assertEquals(Resolution.Listing(File(root, "plain"), "/plain/"), resolve(root, "plain/"))
    }

    @Test
    fun directoryWithoutSlash_redirects() {
        assertEquals(Resolution.Redirect("/site/"), resolve(root, "site"))
        File(root, "a dir").mkdir()
        assertEquals(Resolution.Redirect("/a%20dir/"), resolve(root, "a dir"))
    }

    @Test
    fun file_servedWithMimeType() {
        assertEquals(Resolution.Serve(File(root, "site/app.mjs"), "text/javascript"), resolve(root, "site/app.mjs"))
        assertEquals(Resolution.Serve(File(root, "plain/a b&c.txt"), "text/plain"), resolve(root, "plain/a b&c.txt"))
    }

    @Test
    fun markdownFile_isMarkdown() {
        File(root, "plain/README.md").writeText("# x")
        File(root, "plain/NOTES.MD").writeText("# y")
        assertEquals(Resolution.Markdown(File(root, "plain/README.md")), resolve(root, "plain/README.md"))
        assertEquals(Resolution.Markdown(File(root, "plain/NOTES.MD")), resolve(root, "plain/NOTES.MD"))
    }

    @Test
    fun missingFile_notFound() {
        assertEquals(Resolution.NotFound, resolve(root, "nope.html"))
    }

    @Test
    fun traversal_forbidden() {
        assertEquals(Resolution.Forbidden, resolve(root, "../secret.txt"))
        assertEquals(Resolution.Forbidden, resolve(root, "site/../../secret.txt"))
        assertEquals(Resolution.Forbidden, resolve(root, ".."))
    }

    @Test
    fun mimeType_fallback() {
        assertEquals("text/css", mimeType("A.CSS"))
        assertEquals("application/zip", mimeType("a.zip") { if (it == "zip") "application/zip" else null })
        assertEquals("application/octet-stream", mimeType("README"))
    }

    @Test
    fun encodePath_encodesReservedAndUnicode() {
        assertEquals("/a%20b%26c/%C3%A4%3A%3F%23/", encodePath("/a b&c/ä:?#/"))
    }

    @Test
    fun parentUrl_navigatesUp() {
        assertEquals(null, parentUrl(BASE_URL))
        assertEquals(BASE_URL, parentUrl(BASE_URL + "a/"))
        assertEquals(BASE_URL, parentUrl(BASE_URL + "a.md"))
        assertEquals(BASE_URL + "a%20b/", parentUrl(BASE_URL + "a%20b/c/"))
        assertEquals(BASE_URL + "a%20b/", parentUrl(BASE_URL + "a%20b/c.html?x=1#y"))
        assertEquals(null, parentUrl("https://example.com/a/"))
    }

    @Test
    fun displayPath_roundTrip() {
        val sdcard = File("/storage/emulated/0")
        assertEquals("/", urlToDisplayPath(BASE_URL))
        assertEquals("/a b/", urlToDisplayPath(BASE_URL + "a%20b/"))
        assertEquals(BASE_URL + "a%20b/", displayPathToUrl(sdcard, "/storage/emulated/0/a b/"))
        assertEquals(BASE_URL + "a%20b/", displayPathToUrl(sdcard, "/a b/"))
        assertEquals(BASE_URL + "Documents/x.html", displayPathToUrl(sdcard, " Documents/x.html "))
        assertEquals(BASE_URL, displayPathToUrl(sdcard, "/storage/emulated/0"))
    }
}
