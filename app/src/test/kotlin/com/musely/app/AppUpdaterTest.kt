package com.musely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun `parseSemVer handles standard, two-part, prefixed, prerelease, and build metadata`() {
        assertEquals(SemVer(1, 0, 0), parseSemVer("1.0.0"))
        assertEquals(SemVer(2, 0, 0), parseSemVer("2.0"))
        assertEquals(SemVer(2, 1, 3), parseSemVer("v2.1.3"))
        assertEquals(SemVer(3, 4, 0), parseSemVer("V3.4"))
        assertEquals(SemVer(2, 0, 0, "rc.1"), parseSemVer("v2.0.0-rc.1"))
        assertEquals(SemVer(2, 0, 0, "rc.1"), parseSemVer("2.0.0-rc.1+build.42"))
        assertEquals(SemVer(1, 5, 2), parseSemVer("1.5.2+20261112"))
    }

    @Test
    fun `parseSemVer rejects invalid inputs`() {
        assertNull(parseSemVer(""))
        assertNull(parseSemVer("v"))
        assertNull(parseSemVer("1"))
        assertNull(parseSemVer("1.2.3.4"))
        assertNull(parseSemVer("1.2.x"))
        assertNull(parseSemVer("1.0.0-"))
        assertNull(parseSemVer("-1.0.0"))
    }

    @Test
    fun `isNewerVersion compares two-part, three-part, and prerelease versions`() {
        assertTrue(isNewerVersion("1.0.0", "2.0"))
        assertTrue(isNewerVersion("1.0.0", "2.0.0"))
        assertTrue(isNewerVersion("1.0.0", "v1.0.1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("2.0.0", "1.9.9"))

        assertTrue(isNewerVersion("2.0.0-rc.1", "2.0.0"))
        assertFalse(isNewerVersion("2.0.0", "2.0.0-rc.1"))
        assertTrue(isNewerVersion("2.0.0-rc.1", "2.0.0-rc.2"))
        assertTrue(isNewerVersion("2.0.0-alpha", "2.0.0-beta"))
        assertTrue(isNewerVersion("1.9.9", "2.0.0-rc.1"))
    }

    @Test
    fun `parseLatestReleaseJson parses valid release and prefers Musely apk`() {
        val json = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/zahidhossin39/musely/releases/tag/v1.2.0",
              "body": "Bug fixes and improvements",
              "assets": [
                {
                  "name": "musely-universal.apk",
                  "browser_download_url": "https://github.com/zahidhossin39/musely/releases/download/v1.2.0/musely-universal.apk"
                },
                {
                  "name": "Musely.apk",
                  "browser_download_url": "https://github.com/zahidhossin39/musely/releases/download/v1.2.0/Musely.apk"
                }
              ]
            }
        """.trimIndent()

        val info = parseLatestReleaseJson(json)
        assertNotNull(info)
        assertEquals("1.2.0", info!!.version)
        assertEquals("v1.2.0", info.tagName)
        assertEquals(
            "https://github.com/zahidhossin39/musely/releases/download/v1.2.0/Musely.apk",
            info.apkUrl,
        )
        assertEquals(
            "https://github.com/zahidhossin39/musely/releases/tag/v1.2.0",
            info.releaseUrl,
        )
        assertEquals("Bug fixes and improvements", info.releaseNotes)
    }

    @Test
    fun `parseLatestReleaseJson falls back to first https apk when Musely apk absent`() {
        val json = """
            {
              "tag_name": "v2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/zahidhossin39/musely/releases/tag/v2.0",
              "body": "",
              "assets": [
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/zahidhossin39/musely/releases/download/v2.0/checksums.txt"
                },
                {
                  "name": "musely-2.0.0.apk",
                  "browser_download_url": "https://github.com/zahidhossin39/musely/releases/download/v2.0/musely-2.0.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = parseLatestReleaseJson(json)
        assertNotNull(info)
        assertEquals("2.0.0", info!!.version)
        assertEquals(
            "https://github.com/zahidhossin39/musely/releases/download/v2.0/musely-2.0.0.apk",
            info.apkUrl,
        )
    }

    @Test
    fun `parseLatestReleaseJson ignores drafts, prereleases, and missing or non-https apk assets`() {
        val draftJson = """
            {
              "tag_name": "v1.2.0",
              "draft": true,
              "prerelease": false,
              "assets": [
                {"name": "Musely.apk", "browser_download_url": "https://example.com/Musely.apk"}
              ]
            }
        """.trimIndent()
        assertNull(parseLatestReleaseJson(draftJson))

        val preJson = """
            {
              "tag_name": "v1.2.0-rc.1",
              "draft": false,
              "prerelease": true,
              "assets": [
                {"name": "Musely.apk", "browser_download_url": "https://example.com/Musely.apk"}
              ]
            }
        """.trimIndent()
        assertNull(parseLatestReleaseJson(preJson))

        val noApkJson = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"}
              ]
            }
        """.trimIndent()
        assertNull(parseLatestReleaseJson(noApkJson))

        val httpApkJson = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "Musely.apk", "browser_download_url": "http://example.com/Musely.apk"}
              ]
            }
        """.trimIndent()
        assertNull(parseLatestReleaseJson(httpApkJson))
    }

    @Test
    fun `extractTagFromRedirectUrl extracts tag from GitHub release redirect`() {
        assertEquals(
            "v1.2.0",
            extractTagFromRedirectUrl("https://github.com/zahidhossin39/musely/releases/tag/v1.2.0"),
        )
        assertEquals(
            "v2.0.0",
            extractTagFromRedirectUrl("/zahidhossin39/musely/releases/tag/v2.0.0?source=redirect"),
        )
        assertEquals(
            "1.5.0",
            extractTagFromRedirectUrl("https://github.com/zahidhossin39/musely/releases/tag/1.5.0/"),
        )
        assertNull(extractTagFromRedirectUrl("https://github.com/zahidhossin39/musely/releases"))
        assertNull(extractTagFromRedirectUrl(null))
        assertNull(extractTagFromRedirectUrl(""))
    }

    @Test
    fun `shouldCheckThrottled respects first check and minimum interval`() {
        val sixHours = 6L * 60L * 60L * 1000L
        assertTrue(shouldCheckThrottled(nowMs = 1_000L, lastCheckedMs = 0L))
        assertFalse(shouldCheckThrottled(nowMs = sixHours, lastCheckedMs = 1_000L))
        assertTrue(shouldCheckThrottled(nowMs = 1_000L + sixHours, lastCheckedMs = 1_000L))
        assertTrue(shouldCheckThrottled(nowMs = 2_000L + sixHours, lastCheckedMs = 1_000L))
    }
}
