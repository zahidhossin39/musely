package com.musely.app.download

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Something another app (or a Musely notification) asked the UI to show. */
sealed interface IncomingRequest {
    /** Text shared from YouTube, YouTube Music, Spotify or any app; parsed on the Downloads tab. */
    data class SharedText(val text: String, val receivedAtNanos: Long) : IncomingRequest
    data object OpenDownloads : IncomingRequest
}

/**
 * Hands share-sheet and notification intents from [com.musely.app.MainActivity] to Compose. The
 * request waits here until the UI consumes it, so it survives the permission screen.
 */
object IncomingShare {

    private const val MAX_SHARED_TEXT = 4_000

    private val _pending = MutableStateFlow<IncomingRequest?>(null)
    val pending: StateFlow<IncomingRequest?> = _pending

    fun offer(intent: Intent?) {
        if (intent == null) return
        when {
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> {
                // Some apps put the link in the subject, others in the text.
                val text = listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                ).joinToString("\n").take(MAX_SHARED_TEXT)
                if (text.isNotBlank()) _pending.value = IncomingRequest.SharedText(text, System.nanoTime())
            }
            intent.getBooleanExtra(DownloadService.EXTRA_OPEN_DOWNLOADS, false) ->
                _pending.value = IncomingRequest.OpenDownloads
        }
    }

    fun consume(request: IncomingRequest) {
        _pending.compareAndSet(request, null)
    }
}
