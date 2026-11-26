package com.musely.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkParserTest {

    private fun supported(text: String): MediaLink {
        val parsed = MediaLinkParser.parse(text)
        assertTrue("Expected a supported link for '$text' but got $parsed", parsed is ParsedLink.Supported)
        return (parsed as ParsedLink.Supported).link
    }

    private fun unsupportedReason(text: String): String {
        val parsed = MediaLinkParser.parse(text)
        assertTrue("Expected unsupported for '$text' but got $parsed", parsed is ParsedLink.Unsupported)
        return (parsed as ParsedLink.Unsupported).reason
    }

    @Test
    fun `youtube watch, short and mobile links resolve to the video`() {
        val expected = MediaLink.YouTubeVideo("dQw4w9WgXcQ", music = false)
        assertEquals(expected, supported("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, supported("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
        assertEquals(expected, supported("https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
        assertEquals(expected, supported("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(expected, supported("https://www.youtube.com/live/dQw4w9WgXcQ"))
    }

    @Test
    fun `a song shared from inside a playlist downloads the song`() {
        assertEquals(
            MediaLink.YouTubeVideo("dQw4w9WgXcQ", music = false),
            supported("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
        )
    }

    @Test
    fun `youtube playlists are recognised`() {
        assertEquals(
            MediaLink.YouTubePlaylist("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG", music = false),
            supported("https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
        )
    }

    @Test
    fun `youtube music songs and albums are recognised as music`() {
        assertEquals(MediaLink.YouTubeVideo("dQw4w9WgXcQ", music = true), supported("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=x"))
        val album = supported("https://music.youtube.com/playlist?list=OLAK5uy_kpCZcvA0KnEq6xV6rJ0eLzLtBQmQ6sD0Y")
        assertEquals(MediaLink.YouTubePlaylist("OLAK5uy_kpCZcvA0KnEq6xV6rJ0eLzLtBQmQ6sD0Y", music = true), album)
        assertEquals(MediaSource.YouTubeMusic, album.source)
    }

    @Test
    fun `endless mixes and youtube music browse pages are explained, not downloaded`() {
        assertEquals(MediaLinkParser.UNSUPPORTED_MIX, unsupportedReason("https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ"))
        assertEquals(MediaLinkParser.UNSUPPORTED_YTM_PAGE, unsupportedReason("https://music.youtube.com/browse/MPREb_abc"))
    }

    @Test
    fun `spotify tracks albums and playlists including localised links`() {
        assertEquals(MediaLink.SpotifyTrack("4cOdK2wGLETKBW3PvgPWqT"), supported("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1a2b"))
        assertEquals(MediaLink.SpotifyAlbum("6N9PS4QXF1D0OWPk0Sxtb4"), supported("https://open.spotify.com/intl-de/album/6N9PS4QXF1D0OWPk0Sxtb4"))
        assertEquals(MediaLink.SpotifyPlaylist("37i9dQZF1DXcBWIGoYBM5M"), supported("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals(MediaLink.SpotifyTrack("4cOdK2wGLETKBW3PvgPWqT"), supported("spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
    }

    @Test
    fun `spotify share text with words around the link still works`() {
        val shared = "Never Gonna Give You Up by Rick Astley https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=xyz."
        assertEquals(MediaLink.SpotifyTrack("4cOdK2wGLETKBW3PvgPWqT"), supported(shared))
    }

    @Test
    fun `spotify short links are kept for resolving`() {
        assertEquals(MediaLink.SpotifyShortLink("https://spotify.link/AbCdEf"), supported("https://spotify.link/AbCdEf"))
    }

    @Test
    fun `spotify artist pages and other sites are rejected with a reason`() {
        assertEquals(MediaLinkParser.UNSUPPORTED_SPOTIFY_PAGE, unsupportedReason("https://open.spotify.com/artist/0gxyHStUsqpMadRV0Di1Qt"))
        assertEquals(MediaLinkParser.UNSUPPORTED_SITE, unsupportedReason("https://example.com/song.mp3"))
    }

    @Test
    fun `text without a link reports no link`() {
        assertEquals(ParsedLink.NoLink, MediaLinkParser.parse("just some words"))
    }

    @Test
    fun `extractUrl trims trailing punctuation`() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", MediaLinkParser.extractUrl("watch (https://youtu.be/dQw4w9WgXcQ)!"))
        assertNull(MediaLinkParser.extractUrl("nothing here"))
    }
}
