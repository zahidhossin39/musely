package com.musely.app.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException

/** A failure with a message that is safe and useful to show the user. */
class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One track or video a link resolved to. */
data class MediaEntry(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val source: DownloadSource,
)

/** What the Downloads tab previews before the user confirms. */
data class ResolvedMedia(
    val link: MediaLink,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val isCollection: Boolean,
    val entries: List<MediaEntry>,
)

/** Streams available for one YouTube video. */
internal data class StreamSet(
    val audio: List<StreamCandidate>,
    val videoOnly: List<StreamCandidate>,
    val muxed: List<StreamCandidate>,
    /** User agent the stream URLs were issued for; sent again when downloading. */
    val userAgent: String,
    /** The video's own thumbnail, used as cover art when the link didn't provide one. */
    val thumbnailUrl: String?,
)

internal object NewPipeSetup {
    @Volatile private var initialized = false

    fun ensure() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(NewPipeHttpDownloader(), Localization.DEFAULT, ContentCountry.DEFAULT)
                initialized = true
            }
        }
    }
}

internal object MediaResolver {

    /** Upper bound so a huge playlist can't queue thousands of downloads by accident. */
    const val MAX_COLLECTION_ITEMS = 300
    private const val SEARCH_RESULTS_CONSIDERED = 10

    suspend fun resolve(link: MediaLink): ResolvedMedia = withContext(Dispatchers.IO) {
        guarded {
            NewPipeSetup.ensure()
            when (link) {
                is MediaLink.YouTubeVideo -> resolveVideo(link)
                is MediaLink.YouTubePlaylist -> resolvePlaylist(link)
                is MediaLink.SpotifyTrack, is MediaLink.SpotifyAlbum, is MediaLink.SpotifyPlaylist -> resolveSpotify(link)
                is MediaLink.SpotifyShortLink -> resolveSpotify(followShortLink(link))
            }
        }
    }

    private fun resolveVideo(link: MediaLink.YouTubeVideo): ResolvedMedia {
        val info = StreamInfo.getInfo(ServiceList.YouTube, link.url)
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            throw ResolveException("Live streams can't be downloaded.")
        }
        val tags = deriveYouTubeTags(info.name, info.uploaderName.orEmpty())
        val thumbnail = bestImage(info.thumbnails)
        val entry = MediaEntry(tags.title, tags.artist, null, info.duration * 1000, thumbnail, DownloadSource.YouTube(link.url))
        return ResolvedMedia(link, tags.title, tags.artist, thumbnail, isCollection = false, entries = listOf(entry))
    }

    private fun resolvePlaylist(link: MediaLink.YouTubePlaylist): ResolvedMedia {
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, link.url)
        val items = ArrayList<StreamInfoItem>(info.relatedItems)
        var page = if (info.hasNextPage()) info.nextPage else null
        while (page != null && items.size < MAX_COLLECTION_ITEMS) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, link.url, page)
            items += more.items
            page = if (more.hasNextPage()) more.nextPage else null
        }
        val entries = items
            .filter { it.streamType != StreamType.LIVE_STREAM && it.streamType != StreamType.AUDIO_LIVE_STREAM }
            .take(MAX_COLLECTION_ITEMS)
            .map { item ->
                val tags = deriveYouTubeTags(item.name, item.uploaderName.orEmpty())
                MediaEntry(tags.title, tags.artist, null, item.duration * 1000, bestImage(item.thumbnails), DownloadSource.YouTube(item.url))
            }
        if (entries.isEmpty()) throw ResolveException("This playlist is empty or private.")
        val owner = cleanUploader(info.uploaderName.orEmpty())
        return ResolvedMedia(
            link = link,
            title = info.name,
            subtitle = owner.ifBlank { link.source.label },
            thumbnailUrl = bestImage(info.thumbnails) ?: entries.first().thumbnailUrl,
            isCollection = true,
            entries = entries,
        )
    }

    private fun resolveSpotify(link: MediaLink): ResolvedMedia {
        val embed = SpotifyEmbedParser.embedUrl(link) ?: throw ResolveException(MediaLinkParser.UNSUPPORTED_SPOTIFY_PAGE)
        val entity = SpotifyEmbedParser.parse(Http.getText(embed, HostPolicy.Spotify).body)
            ?: throw ResolveException("Couldn't read this Spotify link. It may be private or region-locked.")
        return when (entity) {
            is SpotifyEntity.Track -> {
                val artist = entity.info.artists.joinToString(", ").ifBlank { "Unknown artist" }
                val entry = MediaEntry(entity.info.title, artist, null, entity.info.durationMs, entity.imageUrl, DownloadSource.SpotifyMatch(entity.info))
                ResolvedMedia(link, entity.info.title, artist, entity.imageUrl, isCollection = false, entries = listOf(entry))
            }
            is SpotifyEntity.Collection -> {
                val entries = entity.tracks.take(MAX_COLLECTION_ITEMS).map { track ->
                    MediaEntry(
                        title = track.title,
                        artist = track.artists.joinToString(", ").ifBlank { "Unknown artist" },
                        album = if (entity.isAlbum) entity.name else null,
                        durationMs = track.durationMs,
                        thumbnailUrl = if (entity.isAlbum) entity.imageUrl else null,
                        source = DownloadSource.SpotifyMatch(track),
                    )
                }
                if (entries.isEmpty()) {
                    throw ResolveException("This Spotify ${if (entity.isAlbum) "album" else "playlist"} has no tracks Musely can read.")
                }
                ResolvedMedia(link, entity.name, entity.owner.ifBlank { "Spotify" }, entity.imageUrl, isCollection = true, entries = entries)
            }
        }
    }

    private fun followShortLink(link: MediaLink.SpotifyShortLink): MediaLink {
        val response = Http.getText(link.url, HostPolicy.SpotifyShortLink)
        val target = listOf(response.finalUrl, SpotifyEmbedParser.findSpotifyUrl(response.body).orEmpty())
            .firstNotNullOfOrNull { candidate ->
                (MediaLinkParser.parse(candidate) as? ParsedLink.Supported)?.link?.takeIf { it !is MediaLink.SpotifyShortLink }
            }
        return target ?: throw ResolveException("Couldn't open this Spotify share link.")
    }

    /** Finds the YouTube Music song that matches a Spotify track and returns its watch URL. */
    suspend fun matchOnYouTubeMusic(track: SpotifyTrackInfo): String = withContext(Dispatchers.IO) {
        guarded {
            NewPipeSetup.ensure()
            val query = TrackMatcher.searchQuery(track)
            val match = listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS)
                .firstNotNullOfOrNull { filter -> TrackMatcher.bestMatch(track, search(query, filter)) }
            match?.url ?: throw ResolveException("No match for \"${track.title}\" on YouTube Music.")
        }
    }

    private fun search(query: String, filter: String): List<MatchCandidate> {
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf(filter), "")
        return SearchInfo.getInfo(ServiceList.YouTube, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(SEARCH_RESULTS_CONSIDERED)
            .map { MatchCandidate(it.url, it.name, it.uploaderName.orEmpty(), it.duration) }
    }

    /** Fetches fresh stream URLs; YouTube's are signed and expire after a few hours. */
    suspend fun fetchStreams(url: String): StreamSet = withContext(Dispatchers.IO) {
        guarded {
            NewPipeSetup.ensure()
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val visionOs = (info.audioStreams + info.videoOnlyStreams).any { stream ->
                stream.isUrl && YoutubeParsingHelper.isVisionOsStreamingUrl(stream.content)
            }
            StreamSet(
                audio = info.audioStreams.map { it.toCandidate() },
                videoOnly = info.videoOnlyStreams.map { it.toCandidate() },
                muxed = info.videoStreams.map { it.toCandidate() },
                userAgent = if (visionOs) YoutubeParsingHelper.getVisionOsUserAgent(Localization.DEFAULT) else Http.BROWSER_USER_AGENT,
                thumbnailUrl = bestImage(info.thumbnails),
            )
        }
    }

    private fun AudioStream.toCandidate() = StreamCandidate(
        url = if (isUrl) content else "",
        suffix = format?.suffix.orEmpty(),
        codec = codec,
        bitrate = maxOf(averageBitrate, bitrate),
        progressive = isProgressive(),
    )

    private fun VideoStream.toCandidate() = StreamCandidate(
        url = if (isUrl) content else "",
        suffix = format?.suffix.orEmpty(),
        codec = codec,
        bitrate = bitrate,
        height = height,
        fps = fps,
        progressive = isProgressive(),
    )

    private fun Stream.isProgressive() = deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private fun bestImage(images: List<Image>): String? =
        images.filter { it.url.startsWith("https://") }.maxByOrNull { it.height.toLong() * maxOf(it.width, 1) }?.url

    /** Runs [block], translating extractor and network failures into [ResolveException]s. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResolveException) {
        throw e
    } catch (e: Exception) {
        throw ResolveException(userMessage(e), e)
    }

    internal fun userMessage(error: Throwable): String = when (error) {
        is AgeRestrictedContentException -> "Age-restricted videos can't be downloaded without signing in."
        is GeographicRestrictionException -> "This isn't available in your country."
        is PrivateContentException -> "This is private."
        is PaidContentException, is YoutubeMusicPremiumContentException -> "This needs a paid subscription."
        is SignInConfirmNotBotException -> "YouTube asked to confirm you're not a bot. Try again later."
        is ReCaptchaException -> "YouTube is rate limiting requests. Try again in a few minutes."
        is ContentNotAvailableException -> "This video isn't available."
        is IOException -> "Couldn't connect. Check your internet connection and try again."
        else -> "Something went wrong reading this link."
    }
}
