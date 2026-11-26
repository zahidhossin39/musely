package com.musely.app.download

import java.text.Normalizer
import kotlin.math.abs

/** A YouTube (Music) search result considered as the audio source for a Spotify track. */
data class MatchCandidate(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
)

/**
 * Picks the YouTube Music result that best matches a Spotify track by title, artist and length.
 * Pure and deterministic so the scoring is covered by unit tests.
 */
object TrackMatcher {

    private const val TITLE_WEIGHT = 0.5
    private const val ARTIST_WEIGHT = 0.3
    private const val DURATION_WEIGHT = 0.2
    private const val MIN_ACCEPTED_SCORE = 0.45

    private val diacritics = Regex("""\p{Mn}+""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")
    private val featuring = Regex("""\b(feat|ft|featuring|with)\b.*""")
    private val bracketed = Regex("""[(\[].*?[)\]]""")

    /** Query sent to YouTube Music for a Spotify track. */
    fun searchQuery(track: SpotifyTrackInfo): String =
        listOfNotNull(track.artists.firstOrNull(), track.title).joinToString(" ")

    fun bestMatch(track: SpotifyTrackInfo, candidates: List<MatchCandidate>): MatchCandidate? =
        candidates
            .map { it to score(track, it) }
            .filter { it.second >= MIN_ACCEPTED_SCORE }
            .maxByOrNull { it.second }
            ?.first

    internal fun score(track: SpotifyTrackInfo, candidate: MatchCandidate): Double {
        val wanted = tokens(stripExtras(track.title))
        val found = tokens(candidate.title)
        val titleScore = if (wanted.isEmpty()) 0.0 else wanted.count { it in found }.toDouble() / wanted.size

        val haystack = tokens(candidate.uploader) + found
        val artistScore = track.artists
            .map(::tokens)
            .filter { it.isNotEmpty() }
            .maxOfOrNull { artist -> artist.count { it in haystack }.toDouble() / artist.size }
            ?: 0.0

        return TITLE_WEIGHT * titleScore +
            ARTIST_WEIGHT * artistScore +
            DURATION_WEIGHT * durationScore(track.durationMs / 1000, candidate.durationSec)
    }

    private fun durationScore(wantedSec: Long, foundSec: Long): Double {
        if (wantedSec <= 0 || foundSec <= 0) return 0.5
        val diff = abs(wantedSec - foundSec)
        return when {
            diff <= 3 -> 1.0
            diff <= 10 -> 0.7
            diff <= 30 -> 0.3
            else -> 0.0
        }
    }

    private fun stripExtras(title: String): String =
        title.lowercase().replace(bracketed, " ").replace(featuring, " ").substringBefore(" - ")

    internal fun tokens(text: String): Set<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .split(nonWord)
            .filter { it.isNotEmpty() }
            .toSet()
}
