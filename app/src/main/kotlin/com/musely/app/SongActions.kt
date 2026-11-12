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
)

sealed interface DeleteSongOutcome {
    data object Deleted : DeleteSongOutcome
    data class RequiresConfirmation(val intentSender: IntentSender) : DeleteSongOutcome
    data class Failed(val message: String) : DeleteSongOutcome
}

/**
 * Loads file metadata for [song] from [MediaStore], falling back to [MediaMetadataRetriever]
 * for bitrate and sample rate when unavailable from MediaStore.
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

    val projection = buildList {
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.MIME_TYPE)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        add(MediaStore.Audio.Media.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Audio.Media.BITRATE)
        }
    }.toTypedArray()

    runCatching {
        context.contentResolver.query(song.uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                }

                val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                if (sizeIdx >= 0) {
                    sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                }

                val mimeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                if (mimeIdx >= 0) {
                    mimeType = cursor.getString(mimeIdx).orEmpty()
                }

                val modifiedIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                if (modifiedIdx >= 0) {
                    dateModifiedSec = cursor.getLong(modifiedIdx).coerceAtLeast(0L)
                }

                val durationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                if (durationIdx >= 0) {
                    val queriedDuration = cursor.getLong(durationIdx)
                    if (queriedDuration > 0L) durationMs = queriedDuration
                }

                pathOrRelative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relIdx = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    if (relIdx >= 0) cursor.getString(relIdx).orEmpty() else ""
                } else {
                    @Suppress("DEPRECATION")
                    val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataIdx >= 0) cursor.getString(dataIdx).orEmpty() else ""
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bitrateIdx = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                    if (bitrateIdx >= 0) {
                        val queriedBitrate = cursor.getInt(bitrateIdx)
                        if (queriedBitrate > 0) bitrateBps = queriedBitrate
                    }
                }
            }
        }
    }

    if (bitrateBps == null || sampleRateHz == null || mimeType.isBlank() || durationMs <= 0L) {
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
    )
}

/**
 * Deletes [song] from device storage via [MediaStore], handling API 30+ confirmation requests,
 * API 29 [RecoverableSecurityException], and direct deletion on API 26-28.
 */
fun deleteSongFromDevice(context: Context, song: Song): DeleteSongOutcome {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(song.uri),
            )
            DeleteSongOutcome.RequiresConfirmation(pendingIntent.intentSender)
        } catch (t: Throwable) {
            DeleteSongOutcome.Failed(t.message ?: "Unable to request song deletion")
        }
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
        try {
            val deletedRows = context.contentResolver.delete(song.uri, null, null)
            if (deletedRows > 0) {
                DeleteSongOutcome.Deleted
            } else {
                DeleteSongOutcome.Failed("Song could not be deleted")
            }
        } catch (rse: RecoverableSecurityException) {
            DeleteSongOutcome.RequiresConfirmation(rse.userAction.actionIntent.intentSender)
        } catch (t: Throwable) {
            DeleteSongOutcome.Failed(t.message ?: "Unable to delete song")
        }
    } else {
        try {
            val deletedRows = context.contentResolver.delete(song.uri, null, null)
            if (deletedRows > 0) {
                DeleteSongOutcome.Deleted
            } else {
                DeleteSongOutcome.Failed("Song could not be deleted")
            }
        } catch (t: Throwable) {
            DeleteSongOutcome.Failed(t.message ?: "Unable to delete song")
        }
    }
}
