package com.reader343.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReleaseParserTest {

    private fun asset(name: String, size: Long = 24_600_000L) = JSONObject()
        .put("name", name)
        .put("size", size)
        .put("browser_download_url", "https://github.com/LAITH343/Reader343/releases/download/v1.1/$name")

    private fun release(
        tag: String = "v1.1",
        assets: List<JSONObject> = listOf(asset("reader343-1.1-118.apk")),
        body: String? = "## What's new\n- New: Weekly goals — Set a weekly target.",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String = JSONObject()
        .put("tag_name", tag)
        .put("draft", draft)
        .put("prerelease", prerelease)
        .put("html_url", "https://github.com/LAITH343/Reader343/releases/tag/$tag")
        .put("published_at", "2026-09-16T09:00:00Z")
        .put("body", body ?: JSONObject.NULL)
        .put("assets", JSONArray(assets))
        .toString()

    @Test
    fun `parses stable release`() {
        val parsed = ReleaseParser.parse(release())
        assertNotNull(parsed)
        parsed!!
        assertEquals("v1.1", parsed.tag)
        assertEquals("1.1", parsed.versionName)
        assertEquals(118, parsed.versionCode)
        assertEquals(24_600_000L, parsed.apk.size)
        assertEquals(Instant.parse("2026-09-16T09:00:00Z"), parsed.publishedAt)
        assertEquals(1, parsed.changelog?.size)
    }

    @Test
    fun `skips drafts and prereleases`() {
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse(release(prerelease = true)))
    }

    @Test
    fun `ignores non apk assets`() {
        val parsed = ReleaseParser.parse(
            release(assets = listOf(asset("checksums.txt"), asset("reader343-1.1-118.apk"), asset("notes.pdf"))),
        )
        assertEquals(118, parsed?.versionCode)
    }

    @Test
    fun `rejects missing or ambiguous apk`() {
        assertNull(ReleaseParser.parse(release(assets = emptyList())))
        assertNull(ReleaseParser.parse(release(assets = listOf(asset("reader343-1.1-118.apk"), asset("reader343-1.1-119.apk")))))
        assertNull(ReleaseParser.parse(release(assets = listOf(asset("app-release.apk")))))
    }

    @Test
    fun `null body gives no changelog`() {
        val parsed = ReleaseParser.parse(release(body = null))
        assertNotNull(parsed)
        assertNull(parsed!!.changelog)
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("{}"))
    }

    @Test
    fun `parses asset names`() {
        assertEquals("1.1" to 118, ReleaseParser.parseAssetName("reader343-1.1-118.apk"))
        assertEquals("2.0.3" to 2003, ReleaseParser.parseAssetName("reader343-2.0.3-2003.apk"))
        assertNull(ReleaseParser.parseAssetName("reader343-1-118.apk"))
        assertNull(ReleaseParser.parseAssetName("reader343-1.1.apk"))
        assertNull(ReleaseParser.parseAssetName("Reader343-1.1-118.apk"))
        assertNull(ReleaseParser.parseAssetName("reader343-1.1-118.apk.zip"))
        assertNull(ReleaseParser.parseAssetName("reader343-1.1-99999999999.apk"))
    }

    @Test
    fun `compares version codes`() {
        val parsed = ReleaseParser.parse(release())!!
        assertTrue(parsed.isNewerThan(104))
        assertFalse(parsed.isNewerThan(118))
        assertFalse(parsed.isNewerThan(200))
    }
}
