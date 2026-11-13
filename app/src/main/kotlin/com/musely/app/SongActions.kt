package com.musely.app

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore

data class SongFileInfo(
    val fileName: String,
    val pathOrRelative: String,
    val sizeBytes: Long,
    val mimeType: String,
    val durationMs: Long,
    val bitrateBps: Int?,
    val sampleRateHz: Int?,
    val dateModifiedSec: Long,
    val hasAudioTrack: Boolean? = null,
)

sealed interface DeleteSongOutcome {
    data object Deleted : DeleteSongOutcome
    data class RequiresConfirmation(val intentSender: IntentSender) : DeleteSongOutcome
    data class Failed(val message: String) : DeleteSongOutcome
}

/**
 * Loads file metadata for [song] (audio or video) from [MediaStore], falling back to
 * [MediaMetadataRetriever] for bitrate, sample rate, and audio-track availability.
 */
fun loadSongFileInfo(context: Context, song: Song): SongFileInfo {
    var fileName = song.title
    var pathOrRelative = ""
    var sizeBytes = 0L
    var mimeType = ""
    var durationMs = song.durationMs
    var bitrateBps: Int? = null
    var sampleRateHz: Int? = null
    var dateModifiedSec = 0L
    var hasAudioTrack: Boolean? = null

    val projection = buildList {
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            add(MediaStore.MediaColumns.DATA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.MediaColumns.BITRATE)
        }
    }.toTypedArray()

    runCatching {
        context.contentResolver.query(song.uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                }

                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeIdx >= 0) {
                    sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                }

                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                if (mimeIdx >= 0) {
                    mimeType = cursor.getString(mimeIdx).orEmpty()
                }

                val modifiedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (modifiedIdx >= 0) {
                    dateModifiedSec = cursor.getLong(modifiedIdx).coerceAtLeast(0L)
                }

                val durationIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                if (durationIdx >= 0) {
                    val queriedDuration = cursor.getLong(durationIdx)
                    if (queriedDuration > 0L) durationMs = queriedDuration
                }

                pathOrRelative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (relIdx >= 0) cursor.getString(relIdx).orEmpty() else ""
                } else {
                    @Suppress("DEPRECATION")
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx >= 0) cursor.getString(dataIdx).orEmpty() else ""
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bitrateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BITRATE)
                    if (bitrateIdx >= 0) {
                        val queriedBitrate = cursor.getInt(bitrateIdx)
                        if (queriedBitrate > 0) bitrateBps = queriedBitrate
                    }
                }
            }
        }
    }

    if (song.isVideo || bitrateBps == null || sampleRateHz == null || mimeType.isBlank() || durationMs <= 0L) {
        val retriever = MediaMetadataRetriever()
        try {
            val ready = try {
                retriever.setDataSource(context, song.uri)
                true
            } catch (_: Throwable) {
                try {
                    context.contentResolver.openFileDescriptor(song.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        true
                    } ?: false
                } catch (_: Throwable) {
                    false
                }
            }
            if (ready) {
                if (song.isVideo) {
                    hasAudioTrack = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                        ?.equals("yes", ignoreCase = true)
                        ?: false
                }
                if (bitrateBps == null) {
                    bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                }
                if (sampleRateHz == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                }
                if (mimeType.isBlank()) {
                    mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
                }
                if (durationMs <= 0L) {
                    val parsedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    if (parsedDuration != null && parsedDuration > 0L) {
                        durationMs = parsedDuration
                    }
                }
            }
        } catch (_: Throwable) {
            // Keep MediaStore metadata if MediaMetadataRetriever fails.
        } finally {
            runCatching { retriever.release() }
        }
    }

    return SongFileInfo(
        fileName = fileName,
        pathOrRelative = pathOrRelative,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        durationMs = durationMs,
        bitrateBps = bitrateBps,
        sampleRateHz = sampleRateHz,
        dateModifiedSec = dateModifiedSec,
        hasAudioTrack = hasAudioTrack,
    )
}

/**
 * Deletes [song] from device storage via [MediaStore], handling API 30+ confirmation requests,
 * API 29 [RecoverableSecurityException], and direct deletion on API 26-28.
 */
fun deleteSongFromDevice(context: Context, song: Song): DeleteSongOutcome {
    val noun = if (song.isVideo) "video" else "song"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(song.uri),
            )
            DeleteSongOutcome.RequiresConfirmation(pendingIntent.intentSender)
        } catch (t: Throwable) {
            DeleteSongOutcome.Failed(t.message ?: "Unable to request $noun deletion")
        }
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
        try {
            val deletedRows = context.contentResolver.delete(song.uri, null, null)
            if (deletedRows > 0) {
                DeleteSongOutcome.Deleted
            } else {
                DeleteSongOutcome.Failed("${noun.replaceFirstChar { it.uppercase() }} could not be deleted")
            }
        } catch (rse: RecoverableSecurityException) {
            DeleteSongOutcome.RequiresConfirmation(rse.userAction.actionIntent.intentSender)
        } catch (t: Throwable) {
            DeleteSongOutcome.Failed(t.message ?: "Unable to delete $noun")
        }
    } else {
        try {
            val deletedRows = context.contentResolver.delete(song.uri, null, null)
            if (deletedRows > 0) {
                DeleteSongOutcome.Deleted
            } else {
                DeleteSongOutcome.Failed("${noun.replaceFirstChar { it.uppercase() }} could not be deleted")
            }
        } catch (t: Throwable) {
            DeleteSongOutcome.Failed(t.message ?: "Unable to delete $noun")
        }
    }
}
