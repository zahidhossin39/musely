package com.musely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumArtTest {

    @Test
    fun computeInSampleSize_scalesPowerOfTwoAboveTargetAndClampsBounds() {
        assertEquals(1, computeInSampleSize(srcWidth = 256, srcHeight = 256, targetPx = 256))
        assertEquals(1, computeInSampleSize(srcWidth = 511, srcHeight = 511, targetPx = 256))
        assertEquals(2, computeInSampleSize(srcWidth = 512, srcHeight = 512, targetPx = 256))
        assertEquals(4, computeInSampleSize(srcWidth = 1024, srcHeight = 1024, targetPx = 256))
        assertEquals(2, computeInSampleSize(srcWidth = 2048, srcHeight = 2048, targetPx = 900))
        // Rectangular extreme should keep the smaller side crisp.
        assertEquals(1, computeInSampleSize(srcWidth = 4000, srcHeight = 300, targetPx = 256))
    }

    @Test
    fun computeInSampleSize_handlesNonPositiveInputsSafely() {
        assertEquals(1, computeInSampleSize(0, 0, 256))
        assertEquals(1, computeInSampleSize(-100, 500, 256))
        assertEquals(1, computeInSampleSize(500, -100, 256))
        assertEquals(1, computeInSampleSize(1024, 1024, 0))
        assertEquals(1, computeInSampleSize(1024, 1024, -1))
    }

    @Test
    fun artKeysAndSubjects_isolateSongsFromAlbumsEvenOnSharedOrExtremeIds() {
        val songKeySmall = ArtCache.key(albumId = 42L, songId = 42L, large = false)
        val songKeyLarge = ArtCache.key(albumId = 42L, songId = 42L, large = true)
        val albumKeySmall = ArtCache.key(albumId = 42L, songId = null, large = false)
        val maxIdSongKey = ArtCache.key(albumId = 1L, songId = Long.MAX_VALUE, large = true)
        val negAlbumKey = ArtCache.key(albumId = -1L, songId = null, large = false)

        assertNotEquals(songKeySmall, albumKeySmall)
        assertNotEquals(songKeySmall, songKeyLarge)
        assertNotEquals(maxIdSongKey, negAlbumKey)
        assertEquals(ArtKey(isSong = true, id = 99L, large = false), ArtCache.key(albumId = 1L, songId = 99L, large = false))

        val songSubject = ArtCache.subject(albumId = 42L, songId = 42L)
        val albumSubject = ArtCache.subject(albumId = 42L, songId = null)
        assertNotEquals(songSubject, albumSubject)
    }

    @Test
    fun decodeSampledBitmap_returnsNullForEmptyOrUnparseableBytes() {
        assertNull(decodeSampledBitmap(ByteArray(0), 256))
        assertNull(decodeSampledBitmap(byteArrayOf(0x01, 0x02, 0x03), 256))
    }

    @Test
    fun clearMissing_resetsNegativeLookupTracking() {
        ArtCache.clearMissing()
        assertFalse(ArtCache.isMissing(ArtCache.subject(albumId = 10L, songId = 20L)))
    }
}
