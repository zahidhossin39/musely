package com.musely.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val DEFAULT_OWNER = "zahidhossin39"
private const val DEFAULT_REPO = "musely"
private const val USER_AGENT = "Musely-Android"
private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 10_000
private const val DEFAULT_THROTTLE_MS = 6L * 60L * 60L * 1000L

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        val a = preRelease
        val b = other.preRelease
        if (a == b) return 0
        if (a == null) return 1
        if (b == null) return -1

        val aParts = a.split('.')
        val bParts = b.split('.')
        val minLen = minOf(aParts.size, bParts.size)
        for (i in 0 until minLen) {
            val cmp = comparePreReleaseIdentifier(aParts[i], bParts[i])
            if (cmp != 0) return cmp
        }
        return aParts.size.compareTo(bParts.size)
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        return if (preRelease != null) "$base-$preRelease" else base
    }
}

private fun comparePreReleaseIdentifier(a: String, b: String): Int {
    val aNum = a.toIntOrNull()
    val bNum = b.toIntOrNull()
    return when {
        aNum != null && bNum != null -> aNum.compareTo(bNum)
        aNum != null -> -1 // Numeric identifiers always have lower precedence than non-numeric
        bNum != null -> 1
        else -> a.compareTo(b)
    }
}

/**
 * Parses a semantic version string such as `"1.2.3"`, `"v2.0"`, or `"2.0.0-rc.1+build.5"`.
 * Returns `null` if [raw] is not a valid 2-part or 3-part SemVer string.
 */
fun parseSemVer(raw: String): SemVer? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val withoutPrefix = if (trimmed.startsWith('v', ignoreCase = true)) {
        trimmed.substring(1)
    } else {
        trimmed
    }
    if (withoutPrefix.isEmpty()) return null

    val withoutBuild = withoutPrefix.substringBefore('+')
    val dashIndex = withoutBuild.indexOf('-')
    val corePart: String
    val preRelease: String?
    if (dashIndex >= 0) {
        corePart = withoutBuild.substring(0, dashIndex)
        val pre = withoutBuild.substring(dashIndex + 1)
        if (pre.isEmpty()) return null
        preRelease = pre
    } else {
        corePart = withoutBuild
        preRelease = null
    }

    val segments = corePart.split('.')
    if (segments.size !in 2..3) return null

    val major = segments[0].toIntOrNull() ?: return null
    val minor = segments[1].toIntOrNull() ?: return null
    val patch = if (segments.size == 3) segments[2].toIntOrNull() ?: return null else 0
    if (major < 0 || minor < 0 || patch < 0) return null

    return SemVer(major, minor, patch, preRelease)
}

/** Returns `true` if [candidate] parses as a strictly newer SemVer than [installed]. */
fun isNewerVersion(installed: String, candidate: String): Boolean {
    val installedVer = parseSemVer(installed) ?: return false
    val candidateVer = parseSemVer(candidate) ?: return false
    return candidateVer > installedVer
}

data class UpdateInfo(
    val version: String,
    val tagName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val releaseNotes: String,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/** Reads the installed app `versionName` from [PackageManager]. */
fun getAppVersion(context: Context): String {
    return try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName?.takeIf { it.isNotBlank() } ?: "0.0.0"
    } catch (_: PackageManager.NameNotFoundException) {
        "0.0.0"
    }
}

/**
 * Parses a GitHub Releases `/releases/latest` JSON payload into [UpdateInfo].
 * Ignores drafts, prereleases, and releases without an HTTPS `.apk` asset.
 */
fun parseLatestReleaseJson(jsonText: String): UpdateInfo? {
    return try {
        val root = JSONObject(jsonText)
        if (root.optBoolean("draft", false)) return null
        if (root.optBoolean("prerelease", false)) return null

        val tagName = root.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null

        val assets = root.optJSONArray("assets") ?: return null
        val apkUrl = pickApkAssetUrl(assets) ?: return null

        val version = parseSemVer(tagName)?.toString()
            ?: tagName.removePrefix("v").removePrefix("V")
        val releaseUrl = root.optString("html_url", "").trim()
        val releaseNotes = root.optString("body", "").trim()

        UpdateInfo(
            version = version,
            tagName = tagName,
            apkUrl = apkUrl,
            releaseUrl = releaseUrl,
            releaseNotes = releaseNotes,
        )
    } catch (_: Exception) {
        null
    }
}

private fun pickApkAssetUrl(assets: JSONArray): String? {
    var fallbackApkUrl: String? = null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name", "").trim()
        val url = asset.optString("browser_download_url", "").trim()
        if (!url.startsWith("https://", ignoreCase = true)) continue
        if (!url.endsWith(".apk", ignoreCase = true) && !name.endsWith(".apk", ignoreCase = true)) {
            continue
        }
        if (name.equals("Musely.apk", ignoreCase = true)) {
            return url
        }
        if (fallbackApkUrl == null) {
            fallbackApkUrl = url
        }
    }
    return fallbackApkUrl
}

/**
 * Extracts the release tag from a GitHub `/releases/latest` redirect `Location` header
 * (e.g. `https://github.com/owner/repo/releases/tag/v1.2.0` -> `"v1.2.0"`).
 */
fun extractTagFromRedirectUrl(location: String?): String? {
    if (location.isNullOrBlank()) return null
    val marker = "/releases/tag/"
    val idx = location.indexOf(marker)
    if (idx < 0) return null

    val afterMarker = location.substring(idx + marker.length)
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .trim()
    return afterMarker.takeIf { it.isNotEmpty() && !it.contains('/') }
}

/**
 * Returns `true` when an update check is allowed (either never checked or at least
 * [minIntervalMs] has elapsed since [lastCheckedMs]).
 */
fun shouldCheckThrottled(
    nowMs: Long,
    lastCheckedMs: Long,
    minIntervalMs: Long = DEFAULT_THROTTLE_MS,
): Boolean {
    if (lastCheckedMs <= 0L) return true
    return (nowMs - lastCheckedMs) >= minIntervalMs
}

/** Opens [url] in the system browser so Android handles APK download and installation natively. */
fun openUrl(context: Context, url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true) &&
        !trimmed.startsWith("http://", ignoreCase = true)
    ) {
        return false
    }
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

/** Checks GitHub Releases for a newer version of Musely. */
suspend fun checkForUpdate(
    context: Context,
    owner: String = DEFAULT_OWNER,
    repo: String = DEFAULT_REPO,
): UpdateStatus = withContext(Dispatchers.IO) {
    val installedVersion = getAppVersion(context)
    try {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val conn = openGitHubConnection(apiUrl).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code == 403 || code == 429) {
                return@withContext checkViaRedirectFallback(owner, repo, installedVersion)
            }
            if (code !in 200..299) {
                return@withContext UpdateStatus.Error("Update check failed (HTTP $code)")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val info = parseLatestReleaseJson(body)
                ?: return@withContext UpdateStatus.UpToDate(installedVersion)

            if (isNewerVersion(installedVersion, info.version)) {
                UpdateStatus.Available(info)
            } else {
                UpdateStatus.UpToDate(installedVersion)
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        UpdateStatus.Error(e.message?.takeIf { it.isNotBlank() } ?: "Unable to check for updates")
    }
}

private fun checkViaRedirectFallback(
    owner: String,
    repo: String,
    installedVersion: String,
): UpdateStatus {
    val latestUrl = "https://github.com/$owner/$repo/releases/latest"
    val conn = openGitHubConnection(latestUrl).apply {
        instanceFollowRedirects = false
    }
    val tag = try {
        val code = conn.responseCode
        if (code !in 300..399) {
            return UpdateStatus.Error("Update check rate-limited (HTTP $code)")
        }
        extractTagFromRedirectUrl(conn.getHeaderField("Location"))
    } finally {
        conn.disconnect()
    } ?: return UpdateStatus.UpToDate(installedVersion)

    if (tag.contains('-')) {
        return UpdateStatus.UpToDate(installedVersion)
    }

    val parsed = parseSemVer(tag) ?: return UpdateStatus.UpToDate(installedVersion)
    val version = parsed.toString()
    if (!isNewerVersion(installedVersion, version)) {
        return UpdateStatus.UpToDate(installedVersion)
    }

    val candidates = listOf(
        "https://github.com/$owner/$repo/releases/download/$tag/Musely.apk",
        "https://github.com/$owner/$repo/releases/download/$tag/musely-$version.apk",
    )
    val apkUrl = candidates.firstOrNull { urlExists(it) }
        ?: return UpdateStatus.UpToDate(installedVersion)

    return UpdateStatus.Available(
        UpdateInfo(
            version = version,
            tagName = tag,
            apkUrl = apkUrl,
            releaseUrl = "https://github.com/$owner/$repo/releases/tag/$tag",
            releaseNotes = "",
        )
    )
}

private fun urlExists(url: String): Boolean {
    val conn = openGitHubConnection(url).apply {
        requestMethod = "HEAD"
        instanceFollowRedirects = false
    }
    return try {
        conn.responseCode in 200..399
    } catch (_: Exception) {
        false
    } finally {
        conn.disconnect()
    }
}

private fun openGitHubConnection(url: String): HttpURLConnection {
    return (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("User-Agent", USER_AGENT)
    }
}
