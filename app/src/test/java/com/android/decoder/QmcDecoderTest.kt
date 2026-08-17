package com.android.decoder.qmc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class QmcDecoderTest {

    @Test
    fun decodeTwiceRestoresOriginalData() {
        val original = ByteArray(200_000) { index ->
            ((index * 31 + 17) and 0xff).toByte()
        }

        val encrypted = ByteArrayOutputStream().use { output ->
            QmcDecoder.decode(
                ByteArrayInputStream(original),
                output,
                bufferSize = 4096
            )
            output.toByteArray()
        }

        val restored = ByteArrayOutputStream().use { output ->
            QmcDecoder.decode(
                ByteArrayInputStream(encrypted),
                output,
                bufferSize = 7777
            )
            output.toByteArray()
        }

        assertArrayEquals(original, restored)
    }

    @Test
    fun outputExtensionIsMappedCorrectly() {
        assertEquals("mp3", QmcDecoder.outputExtension("song.qmc0"))
        assertEquals("mp3", QmcDecoder.outputExtension("song.QMC3"))
        assertEquals("flac", QmcDecoder.outputExtension("song.qmcflac"))
        assertEquals("ogg", QmcDecoder.outputExtension("song.qmcogg"))
        assertNull(QmcDecoder.outputExtension("song.txt"))
    }
}
