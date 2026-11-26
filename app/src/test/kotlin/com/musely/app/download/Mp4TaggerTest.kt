package com.musely.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class Mp4TaggerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val payload = "AUDIO-SAMPLES".toByteArray()
    private val tags = Mp4Tags("Song Title", "The Artist", "The Album", "Downloaded with Musely", byteArrayOf(1, 2, 3))

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
        return Mp4Tagger.box(type, body)
    }

    private fun u32(v: Long) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun stco(offset: Long) = box("stco", u32(0), u32(1), u32(offset))

    private fun moov(chunkOffset: Long) = box(
        "moov",
        box("mvhd", ByteArray(12)),
        box("trak", box("mdia", box("minf", box("stbl", stco(chunkOffset))))),
        box("udta", box("old!", ByteArray(4))),
    )

    private val ftyp = box("ftyp", "M4A ".toByteArray(), u32(0))

    private fun childOf(bytes: ByteArray, parent: Mp4Tagger.Box, type: String): Mp4Tagger.Box =
        checkNotNull(Mp4Tagger.children(bytes, parent.start + parent.headerSize, parent.end)) { "unparseable ${parent.type}" }
            .single { it.type == type }

    /** Reads (stco entry, absolute offset of the mdat payload) from a file. */
    private fun inspect(bytes: ByteArray): Pair<Long, Long> {
        val top = checkNotNull(Mp4Tagger.children(bytes, 0, bytes.size)) { "unparseable output" }
        val mdat = top.single { it.type == "mdat" }
        var box = top.single { it.type == "moov" }
        for (type in listOf("trak", "mdia", "minf", "stbl", "stco")) box = childOf(bytes, box, type)
        val entry = Mp4Tagger.readU32(bytes, box.start + box.headerSize + 8)
        return entry to (mdat.start + mdat.headerSize).toLong()
    }

    private fun tagged(input: ByteArray): ByteArray {
        val source = temp.newFile("in.m4a").apply { writeBytes(input) }
        val output = File(temp.root, "out.m4a")
        assertTrue(Mp4Tagger.tag(source, output, tags))
        return output.readBytes()
    }

    @Test
    fun `moov before mdat shifts chunk offsets to the moved media data`() {
        val provisional = ftyp.size + moov(0).size + 8L
        val input = ftyp + moov(provisional) + box("mdat", payload)
        val (entry, payloadOffset) = inspect(input)
        assertEquals("fixture sanity", payloadOffset, entry)

        val output = tagged(input)
        val (newEntry, newPayloadOffset) = inspect(output)
        assertEquals(newPayloadOffset, newEntry)
        assertEquals(String(payload), String(output, newPayloadOffset.toInt(), payload.size))
    }

    @Test
    fun `mdat before moov leaves chunk offsets untouched`() {
        val payloadOffset = ftyp.size + 8L
        val input = ftyp + box("mdat", payload) + moov(payloadOffset)
        val (newEntry, newPayloadOffset) = inspect(tagged(input))
        assertEquals(payloadOffset, newEntry)
        assertEquals(payloadOffset, newPayloadOffset)
    }

    @Test
    fun `old udta is replaced by the new tags`() {
        val input = ftyp + box("mdat", payload) + moov(ftyp.size + 8L)
        val text = String(tagged(input), Charsets.ISO_8859_1)
        assertEquals(1, Regex("udta").findAll(text).count())
        assertFalse(text.contains("old!"))
        assertTrue(text.contains("©nam") && text.contains("©ART") && text.contains("©alb"))
        assertTrue(text.contains("Song Title") && text.contains("The Artist") && text.contains("The Album"))
        assertTrue(text.contains("covr") && text.contains("mdir"))
    }

    @Test
    fun `files that are not mp4 are rejected without writing output`() {
        val source = temp.newFile("not.mp4").apply { writeText("definitely not an mp4 file") }
        val output = File(temp.root, "never.mp4")
        assertFalse(Mp4Tagger.tag(source, output, tags))
        assertFalse(output.exists())
    }
}
