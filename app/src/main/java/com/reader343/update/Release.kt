package com.reader343.update

import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

data class ApkAsset(
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val url: String,
    val size: Long,
)

enum class ChangeKind { New, Improved, Fixed }

data class ChangeEntry(
    val kind: ChangeKind?,
    val title: String?,
    val body: String,
)

data class Release(
    val tag: String,
    val apk: ApkAsset,
    val publishedAt: Instant?,
    val htmlUrl: String?,
    val changelog: List<ChangeEntry>?,
) {
    val versionName: String get() = apk.versionName
    val versionCode: Int get() = apk.versionCode

    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode
}

object ReleaseParser {

    private val AssetPattern = Regex("""^reader343-(\d+\.\d+(?:\.\d+)?)-(\d+)\.apk$""")

    fun parse(json: String): Release? = try {
        parse(JSONObject(json))
    } catch (_: JSONException) {
        null
    }

    fun parse(root: JSONObject): Release? {
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val apk = findApk(root) ?: return null
        return Release(
            tag = tag,
            apk = apk,
            publishedAt = parseInstant(root.optNullableString("published_at")),
            htmlUrl = root.optNullableString("html_url"),
            changelog = root.optNullableString("body")?.let(ChangelogParser::parse),
        )
    }

    fun parseAssetName(name: String): Pair<String, Int>? {
        val match = AssetPattern.matchEntire(name.trim()) ?: return null
        val code = match.groupValues[2].toIntOrNull() ?: return null
        return match.groupValues[1] to code
    }

    private fun findApk(root: JSONObject): ApkAsset? {
        val assets = root.optJSONArray("assets") ?: return null
        val apks = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val asset = apks.singleOrNull() ?: return null
        val name = asset.optString("name")
        val (versionName, versionCode) = parseAssetName(name) ?: return null
        val url = asset.optNullableString("browser_download_url") ?: return null
        return ApkAsset(
            name = name,
            versionName = versionName,
            versionCode = versionCode,
            url = url,
            size = asset.optLong("size", 0L),
        )
    }

    private fun parseInstant(value: String?): Instant? = try {
        value?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

object ChangelogParser {

    private val Heading = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val Bullet = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
    private val Typed = Regex("""^(New|Improved|Fixed)\s*:\s*(.+?)\s+(?:—|–|--?)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val Apostrophes = Regex("[’‘`]")

    fun parse(body: String): List<ChangeEntry>? {
        val lines = body.lines().map { it.trimEnd('\r') }
        val start = lines.indexOfFirst { isWhatsNew(it) }
        if (start < 0) return null
        val bullets = mutableListOf<StringBuilder>()
        for (line in lines.drop(start + 1)) {
            if (Heading.matches(line)) break
            val bullet = Bullet.matchEntire(line)
            when {
                bullet != null -> bullets += StringBuilder(bullet.groupValues[1].trim())
                line.isNotBlank() && bullets.isNotEmpty() && line.first().isWhitespace() ->
                    bullets.last().append(' ').append(line.trim())
            }
        }
        return bullets.map { it.toString() }.filter { it.isNotEmpty() }.map(::entry)
    }

    private fun isWhatsNew(line: String): Boolean {
        val match = Heading.matchEntire(line) ?: return false
        if (match.groupValues[1].length != 2) return false
        val title = match.groupValues[2].replace(Apostrophes, "'").replace(Regex("\\s+"), " ")
        return title.equals("What's new", ignoreCase = true)
    }

    private fun entry(text: String): ChangeEntry {
        val match = Typed.matchEntire(text) ?: return ChangeEntry(kind = null, title = null, body = text)
        val kind = ChangeKind.entries.first { it.name.equals(match.groupValues[1], ignoreCase = true) }
        return ChangeEntry(kind = kind, title = match.groupValues[2].trim(), body = match.groupValues[3].trim())
    }
}
