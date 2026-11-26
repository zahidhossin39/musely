package com.musely.app.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/** iTunes-style metadata written into M4A/MP4 files. Android's media scanner reads these atoms. */
class Mp4Tags(
    val title: String,
    val artist: String,
    val album: String,
    val comment: String? = null,
    /** JPEG bytes for the `covr` atom, shown as album art. */
    val coverJpeg: ByteArray? = null,
)

/**
 * Writes [Mp4Tags] into an MP4 container by replacing `moov/udta` with a fresh
 * `udta/meta/ilst`. When `moov` precedes `mdat`, every `stco`/`co64` chunk offset is shifted by
 * the size difference so the media data still lines up.
 */
object Mp4Tagger {

    private const val MAX_MOOV_BYTES = 16L * 1024 * 1024
    private const val COPY_BUFFER_BYTES = 256 * 1024
    private const val DATA_TYPE_UTF8 = 1
    private const val DATA_TYPE_JPEG = 13
    private val containerTypes = setOf("trak", "mdia", "minf", "stbl")

    internal data class Box(val type: String, val start: Int, val headerSize: Int, val end: Int)

    /** Tags [input] into [output]. Returns false (leaving [output] untouched) if the file isn't a usable MP4. */
    fun tag(input: File, output: File, tags: Mp4Tags): Boolean {
        RandomAccessFile(input, "r").use { raf ->
            val top = topLevelBoxes(raf) ?: return false
            val moovIndex = top.indexOfFirst { it.type == "moov" }
            val mdatIndex = top.indexOfFirst { it.type == "mdat" }
            if (moovIndex < 0 || mdatIndex < 0) return false
            val moov = top[moovIndex]
            if (moov.size > MAX_MOOV_BYTES) return false

            val moovBytes = ByteArray(moov.size.toInt())
            raf.seek(moov.offset)
            raf.readFully(moovBytes)

            val unshifted = rewriteMoov(moovBytes, tags, offsetDelta = 0L) ?: return false
            val delta = if (moovIndex < mdatIndex) (unshifted.size - moovBytes.size).toLong() else 0L
            val newMoov = if (delta == 0L) unshifted else rewriteMoov(moovBytes, tags, delta) ?: return false

            output.outputStream().buffered().use { out ->
                copyRange(raf, 0L, moov.offset, out)
                out.write(newMoov)
                val tailStart = moov.offset + moov.size
                copyRange(raf, tailStart, raf.length() - tailStart, out)
            }
        }
        return true
    }

    internal data class TopBox(val type: String, val offset: Long, val size: Long)

    private fun topLevelBoxes(raf: RandomAccessFile): List<TopBox>? {
        val boxes = ArrayList<TopBox>()
        val length = raf.length()
        var offset = 0L
        val header = ByteArray(16)
        while (offset + 8 <= length) {
            raf.seek(offset)
            raf.readFully(header, 0, 8)
            val size32 = readU32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            val size = when (size32) {
                0L -> length - offset
                1L -> {
                    if (offset + 16 > length) return null
                    raf.readFully(header, 8, 8)
                    readU64(header, 8)
                }
                else -> size32
            }
            if (size < 8 || offset + size > length) return null
            boxes += TopBox(type, offset, size)
            offset += size
        }
        return boxes
    }

    /** New `moov` bytes: chunk offsets shifted by [offsetDelta], old `udta` dropped, new tags appended. */
    internal fun rewriteMoov(moov: ByteArray, tags: Mp4Tags, offsetDelta: Long): ByteArray? {
        val root = parseBox(moov, 0, moov.size) ?: return null
        if (root.type != "moov") return null
        val patched = moov.copyOf()
        if (offsetDelta != 0L && !shiftChunkOffsets(patched, root.start + root.headerSize, root.end, offsetDelta)) {
            return null
        }
        val children = ByteArrayOutputStream()
        for (child in children(patched, root.start + root.headerSize, root.end) ?: return null) {
            if (child.type != "udta") children.write(patched, child.start, child.end - child.start)
        }
        children.write(buildUdta(tags))
        return box("moov", children.toByteArray())
    }

    private fun shiftChunkOffsets(buf: ByteArray, from: Int, to: Int, delta: Long): Boolean {
        for (child in children(buf, from, to) ?: return false) {
            val body = child.start + child.headerSize
            when (child.type) {
                in containerTypes -> if (!shiftChunkOffsets(buf, body, child.end, delta)) return false
                "stco" -> {
                    val count = readU32(buf, body + 4)
                    if (body + 8 + count * 4 > child.end) return false
                    for (i in 0 until count.toInt()) {
                        val pos = body + 8 + i * 4
                        val shifted = readU32(buf, pos) + delta
                        if (shifted !in 0..0xFFFFFFFFL) return false
                        writeU32(buf, pos, shifted)
                    }
                }
                "co64" -> {
                    val count = readU32(buf, body + 4)
                    if (body + 8 + count * 8 > child.end) return false
                    for (i in 0 until count.toInt()) {
                        val pos = body + 8 + i * 8
                        writeU64(buf, pos, readU64(buf, pos) + delta)
                    }
                }
            }
        }
        return true
    }

    internal fun children(buf: ByteArray, from: Int, to: Int): List<Box>? {
        val result = ArrayList<Box>()
        var pos = from
        while (pos + 8 <= to) {
            val child = parseBox(buf, pos, to) ?: return null
            result += child
            pos = child.end
        }
        return if (pos == to) result else null
    }

    internal fun parseBox(buf: ByteArray, start: Int, limit: Int): Box? {
        if (start + 8 > limit) return null
        val size32 = readU32(buf, start)
        val type = String(buf, start + 4, 4, Charsets.ISO_8859_1)
        val (size, header) = when (size32) {
            0L -> (limit - start).toLong() to 8
            1L -> if (start + 16 <= limit) readU64(buf, start + 8) to 16 else return null
            else -> size32 to 8
        }
        if (size < header || start + size > limit) return null
        return Box(type, start, header, (start + size).toInt())
    }

    internal fun buildUdta(tags: Mp4Tags): ByteArray {
        val items = ByteArrayOutputStream()
        items.write(textItem("©nam", tags.title))
        items.write(textItem("©ART", tags.artist))
        items.write(textItem("aART", tags.artist))
        items.write(textItem("©alb", tags.album))
        tags.comment?.takeIf { it.isNotBlank() }?.let { items.write(textItem("©cmt", it)) }
        tags.coverJpeg?.takeIf { it.isNotEmpty() }?.let { items.write(item("covr", DATA_TYPE_JPEG, it)) }

        val handler = ByteArrayOutputStream().apply {
            write(u32(0)) // version + flags
            write(u32(0)) // pre_defined
            write("mdir".toByteArray(Charsets.ISO_8859_1))
            write("appl".toByteArray(Charsets.ISO_8859_1))
            write(u32(0))
            write(u32(0))
            write(0) // empty, null-terminated name
        }.toByteArray()
        val meta = ByteArrayOutputStream().apply {
            write(u32(0)) // version + flags
            write(box("hdlr", handler))
            write(box("ilst", items.toByteArray()))
        }.toByteArray()
        return box("udta", box("meta", meta))
    }

    private fun textItem(name: String, value: String): ByteArray =
        item(name, DATA_TYPE_UTF8, value.toByteArray(Charsets.UTF_8))

    private fun item(name: String, dataType: Int, payload: ByteArray): ByteArray {
        val data = ByteArrayOutputStream().apply {
            write(u32(dataType.toLong()))
            write(u32(0)) // locale
            write(payload)
        }.toByteArray()
        return box(name, box("data", data))
    }

    internal fun box(type: String, body: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        require(typeBytes.size == 4) { "MP4 box type must be 4 bytes: $type" }
        return ByteArrayOutputStream(8 + body.size).apply {
            write(u32((8 + body.size).toLong()))
            write(typeBytes)
            write(body)
        }.toByteArray()
    }

    private fun copyRange(raf: RandomAccessFile, start: Long, length: Long, out: OutputStream) {
        if (length <= 0) return
        raf.seek(start)
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var remaining = length
        while (remaining > 0) {
            val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Unexpected end of file while tagging")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun u32(value: Long) = ByteArray(4).also { writeU32(it, 0, value) }

    internal fun readU32(buf: ByteArray, pos: Int): Long =
        ((buf[pos].toLong() and 0xFF) shl 24) or
            ((buf[pos + 1].toLong() and 0xFF) shl 16) or
            ((buf[pos + 2].toLong() and 0xFF) shl 8) or
            (buf[pos + 3].toLong() and 0xFF)

    private fun readU64(buf: ByteArray, pos: Int): Long = (readU32(buf, pos) shl 32) or readU32(buf, pos + 4)

    private fun writeU32(buf: ByteArray, pos: Int, value: Long) {
        buf[pos] = (value ushr 24).toByte()
        buf[pos + 1] = (value ushr 16).toByte()
        buf[pos + 2] = (value ushr 8).toByte()
        buf[pos + 3] = value.toByte()
    }

    private fun writeU64(buf: ByteArray, pos: Int, value: Long) {
        writeU32(buf, pos, value ushr 32)
        writeU32(buf, pos + 4, value and 0xFFFFFFFFL)
    }
}
