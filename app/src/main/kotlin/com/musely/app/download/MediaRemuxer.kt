package com.musely.app.download

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Copies the tracks of one or more MP4/M4A inputs into a single standard MP4 without re-encoding.
 * Used to merge a video-only and an audio-only stream, and to turn YouTube's fragmented M4A into
 * a regular file that can carry metadata tags.
 */
internal object MediaRemuxer {

    private const val DEFAULT_SAMPLE_BUFFER_BYTES = 4 * 1024 * 1024

    private class Source(val extractor: MediaExtractor, val muxerTrack: Int, var done: Boolean = false)

    fun remux(inputs: List<File>, output: File) {
        require(inputs.isNotEmpty()) { "Nothing to remux" }
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = ArrayList<MediaExtractor>()
        var started = false
        var failed = true
        try {
            val sources = ArrayList<Source>()
            var bufferSize = DEFAULT_SAMPLE_BUFFER_BYTES
            for (input in inputs) {
                // Track it before it can throw, so it is always released.
                val extractor = MediaExtractor().also(extractors::add)
                extractor.setDataSource(input.absolutePath)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                    mime.startsWith("audio/") || mime.startsWith("video/")
                } ?: throw IOException("No audio or video track in ${input.name}")
                val format = extractor.getTrackFormat(trackIndex)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                extractor.selectTrack(trackIndex)
                sources += Source(extractor, muxer.addTrack(format))
            }
            muxer.start()
            started = true
            writeInterleaved(muxer, sources, ByteBuffer.allocate(bufferSize))
            failed = false
        } finally {
            extractors.forEach { it.release() }
            try {
                // After a failure stop() can throw as well; it must not hide the real error.
                if (started && failed) runCatching { muxer.stop() } else if (started) muxer.stop()
            } finally {
                muxer.release()
            }
        }
    }

    /** Always writes the earliest pending sample so the muxer can interleave tracks cheaply. */
    @SuppressLint("WrongConstant") // Only MediaCodec.BUFFER_FLAG_KEY_FRAME or 0 is ever passed.
    private fun writeInterleaved(muxer: MediaMuxer, sources: List<Source>, buffer: ByteBuffer) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val next = sources.filter { !it.done }.minByOrNull { it.extractor.sampleTime } ?: break
            buffer.clear()
            val size = next.extractor.readSampleData(buffer, 0)
            if (size < 0) {
                next.done = true
                continue
            }
            info.offset = 0
            info.size = size
            info.presentationTimeUs = next.extractor.sampleTime
            info.flags = if (next.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            muxer.writeSampleData(next.muxerTrack, buffer, info)
            if (!next.extractor.advance()) next.done = true
        }
    }
}
