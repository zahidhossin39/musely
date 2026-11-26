package com.musely.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMetadataTest {

    @Test
    fun `topic channels keep the title and drop the suffix`() {
        assertEquals(
            TrackTags("Blinding Lights", "The Weeknd", "Blinding Lights"),
            deriveYouTubeTags("Blinding Lights", "The Weeknd - Topic"),
        )
    }

    @Test
    fun `artist dash title uploads are split and cleaned`() {
        assertEquals(
            TrackTags("Never Gonna Give You Up", "Rick Astley", "Never Gonna Give You Up"),
            deriveYouTubeTags("Rick Astley - Never Gonna Give You Up (Official Music Video)", "Rick Astley"),
        )
    }

    @Test
    fun `plain titles fall back to the uploader as artist`() {
        assertEquals(TrackTags("Lofi beats", "Chill Channel", "Lofi beats"), deriveYouTubeTags("Lofi beats [HD]", "Chill Channel"))
    }

    @Test
    fun `file names are safe and keep the extension`() {
        assertEquals("AC_DC - Back In Black_.m4a", safeFileName(TrackTags("Back In Black?", "AC/DC", ""), "m4a"))
        assertEquals("Musely download.mp4", safeFileName(TrackTags("", "", ""), "mp4"))
        assertEquals("Solo.m4a", safeFileName(TrackTags("Solo", " ", ""), "m4a"))
    }
}

class SpotifyEmbedParserTest {

    private fun page(entity: String) =
        """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":$entity}}}}}</script></html>"""

    @Test
    fun `parses a track with artists, duration and the largest cover`() {
        val html = page(
            """{"type":"track","name":"Never Gonna Give You Up","duration":213573,
               "artists":[{"name":"Rick Astley","uri":"spotify:artist:x"}],
               "visualIdentity":{"image":[{"url":"https://img/small","maxWidth":64},{"url":"https://img/big","maxWidth":640}]}}""",
        )
        val entity = SpotifyEmbedParser.parse(html) as SpotifyEntity.Track
        assertEquals(SpotifyTrackInfo("Never Gonna Give You Up", listOf("Rick Astley"), 213_573L), entity.info)
        assertEquals("https://img/big", entity.imageUrl)
    }

    @Test
    fun `parses a playlist track list and splits artists on non-breaking spaces`() {
        val html = page(
            """{"type":"playlist","name":"Today's Top Hits","subtitle":"Spotify",
               "trackList":[{"title":"Song A","subtitle":"Justice, Tame Impala","duration":200000},{"title":"","subtitle":"x"}]}""",
        )
        val entity = SpotifyEmbedParser.parse(html) as SpotifyEntity.Collection
        assertEquals("Today's Top Hits", entity.name)
        assertEquals(false, entity.isAlbum)
        assertEquals(listOf(SpotifyTrackInfo("Song A", listOf("Justice", "Tame Impala"), 200_000L)), entity.tracks)
    }

    @Test
    fun `pages without embed data return null`() {
        assertNull(SpotifyEmbedParser.parse("<html>nothing</html>"))
    }

    @Test
    fun `finds the real link inside a short-link landing page`() {
        val html = """<a href="https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1">Open</a>"""
        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", SpotifyEmbedParser.findSpotifyUrl(html))
    }
}

class TrackMatcherTest {

    private val track = SpotifyTrackInfo("Blinding Lights", listOf("The Weeknd"), 200_000L)

    @Test
    fun `prefers the official song with matching length over covers`() {
        val candidates = listOf(
            MatchCandidate("cover", "Blinding Lights (Piano Cover)", "Some Pianist", 260),
            MatchCandidate("official", "Blinding Lights", "The Weeknd", 201),
            MatchCandidate("other", "Save Your Tears", "The Weeknd", 215),
        )
        assertEquals("official", TrackMatcher.bestMatch(track, candidates)?.url)
    }

    @Test
    fun `rejects results that do not match at all`() {
        assertNull(TrackMatcher.bestMatch(track, listOf(MatchCandidate("x", "Completely Different", "Nobody", 30))))
    }

    @Test
    fun `search query leads with the first artist`() {
        assertEquals("The Weeknd Blinding Lights", TrackMatcher.searchQuery(track))
    }

    @Test
    fun `tokens ignore accents and punctuation`() {
        assertEquals(setOf("beyonce", "halo"), TrackMatcher.tokens("Beyoncé - Halo!"))
    }
}

class StreamSelectorTest {

    @Test
    fun `audio prefers the best aac stream over higher bitrate opus`() {
        val streams = listOf(
            StreamCandidate("opus", "webm", "opus", 160_000),
            StreamCandidate("aac-low", "m4a", "mp4a.40.5", 48_000),
            StreamCandidate("aac-high", "m4a", "mp4a.40.2", 128_000),
        )
        assertEquals("aac-high", StreamSelector.pickAudio(streams)?.url)
    }

    @Test
    fun `audio falls back to opus and skips non-progressive streams`() {
        val streams = listOf(
            StreamCandidate("dash", "m4a", "mp4a.40.2", 128_000, progressive = false),
            StreamCandidate("opus", "webm", "opus", 160_000),
        )
        assertEquals("opus", StreamSelector.pickAudio(streams)?.url)
    }

    @Test
    fun `video picks the tallest h264 within the cap`() {
        val streams = listOf(
            StreamCandidate("1080", "mp4", "avc1.640028", 4_000_000, height = 1080, fps = 30),
            StreamCandidate("720", "mp4", "avc1.4d401f", 2_000_000, height = 720, fps = 30),
            StreamCandidate("720-vp9", "webm", "vp9", 1_500_000, height = 720, fps = 30),
            StreamCandidate("480", "mp4", "avc1.4d401e", 900_000, height = 480, fps = 30),
        )
        assertEquals("720", StreamSelector.pickVideoOnly(streams, 720)?.url)
    }

    @Test
    fun `video uses the smallest stream when all exceed the cap`() {
        val streams = listOf(
            StreamCandidate("1080", "mp4", "avc1", 4_000_000, height = 1080),
            StreamCandidate("720", "mp4", "avc1", 2_000_000, height = 720),
        )
        assertEquals("720", StreamSelector.pickVideoOnly(streams, 360)?.url)
    }

    @Test
    fun `quality names round trip and default to 720p`() {
        assertEquals(VideoQuality.P1080, VideoQuality.fromName("P1080"))
        assertEquals(VideoQuality.P720, VideoQuality.fromName("bogus"))
        assertEquals(DownloadFormat.Audio, DownloadFormat.fromName(null))
    }
}

class DownloadHistoryCodecTest {

    private fun request(id: String, source: DownloadSource = DownloadSource.YouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ")) =
        DownloadRequest(
            id = id,
            title = "Title $id",
            artist = "Artist",
            album = null,
            thumbnailUrl = "https://img/$id",
            durationMs = 1000,
            source = source,
            origin = MediaSource.YouTube,
            format = DownloadFormat.Audio,
            quality = VideoQuality.P720,
            playlistId = "playlist-1",
            batchId = null,
        )

    private val idA = "00000000-0000-4000-8000-00000000000a"
    private val idB = "00000000-0000-4000-8000-00000000000b"
    private val idC = "00000000-0000-4000-8000-00000000000c"

    @Test
    fun `finished downloads survive a round trip`() {
        val spotify = DownloadSource.SpotifyMatch(SpotifyTrackInfo("Song", listOf("A", "B"), 5000))
        val items = listOf(
            DownloadItem(request(idA), DownloadState.Completed(42L, 1234L, 99L), 10L),
            DownloadItem(request(idB, spotify), DownloadState.Failed("No match"), 11L),
        )
        assertEquals(items, DownloadHistoryCodec.decode(DownloadHistoryCodec.encode(items)))
    }

    @Test
    fun `in-progress downloads come back as retryable failures`() {
        val items = listOf(DownloadItem(request(idC), DownloadState.Downloading(10, 100), 12L))
        val decoded = DownloadHistoryCodec.decode(DownloadHistoryCodec.encode(items)).single()
        assertEquals(DownloadState.Failed(INTERRUPTED_MESSAGE), decoded.state)
    }

    @Test
    fun `history entries with ids that are not uuids are dropped`() {
        val items = listOf(
            DownloadItem(request("../../files"), DownloadState.Failed("x"), 1L),
            DownloadItem(request(idA), DownloadState.Failed("x"), 2L),
        )
        assertEquals(listOf(idA), DownloadHistoryCodec.decode(DownloadHistoryCodec.encode(items)).map { it.request.id })
        assertTrue(DownloadHistoryCodec.isValidId(DownloadCenter.newId()))
    }

    @Test
    fun `corrupt history is ignored`() {
        assertTrue(DownloadHistoryCodec.decode("not json").isEmpty())
    }

    @Test
    fun `history trimming keeps active downloads and the newest finished ones`() {
        val items = listOf(
            DownloadItem(request("a"), DownloadState.Queued, 5),
            DownloadItem(request("b"), DownloadState.Completed(1, 1, 1), 4),
            DownloadItem(request("c"), DownloadState.Completed(2, 1, 1), 3),
        )
        assertEquals(listOf("a", "b"), DownloadCenter.trimHistory(items, max = 2).map { it.request.id })
    }

    @Test
    fun `content range totals are parsed`() {
        assertEquals(1234L, ChunkedDownloader.parseContentRangeTotal("bytes 0-99/1234"))
        assertNull(ChunkedDownloader.parseContentRangeTotal("bytes 0-99/*"))
        assertNull(ChunkedDownloader.parseContentRangeTotal(null))
    }
}

class HostPolicyTest {

    private fun url(text: String) = java.net.URL(text)

    @Test
    fun `media policy allows youtube and spotify cdns only over https`() {
        assertTrue(HostPolicy.Media.allows(url("https://rr3---sn-abc.googlevideo.com/videoplayback?x=1")))
        assertTrue(HostPolicy.Media.allows(url("https://i.ytimg.com/vi/x/hqdefault.jpg")))
        assertTrue(HostPolicy.Media.allows(url("https://image-cdn-ak.spotifycdn.com/image/abc")))
        assertTrue(!HostPolicy.Media.allows(url("http://i.ytimg.com/vi/x/hqdefault.jpg")))
        assertTrue(!HostPolicy.Media.allows(url("https://evil.com/ytimg.com.jpg")))
        assertTrue(!HostPolicy.Media.allows(url("https://notytimg.com/x.jpg")))
    }

    @Test
    fun `spotify policies stay on spotify hosts`() {
        assertTrue(HostPolicy.Spotify.allows(url("https://open.spotify.com/embed/track/x")))
        assertTrue(!HostPolicy.Spotify.allows(url("https://spotify.com.evil.net/embed")))
        assertTrue(HostPolicy.SpotifyShortLink.allows(url("https://spotify.link/AbC")))
        assertTrue(!HostPolicy.SpotifyShortLink.allows(url("https://example.org/AbC")))
    }

    @Test
    fun `file names drop bidi controls and trailing dots`() {
        // The override character is neutralised to "_" and the trailing dots are trimmed.
        assertEquals("Artist - Song_.m4a", safeFileName(TrackTags("Song‮...", "Artist", ""), "m4a"))
    }
}
