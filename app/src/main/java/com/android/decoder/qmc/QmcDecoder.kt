package com.android.decoder.qmc

import java.io.InputStream
import java.io.OutputStream

object QmcDecoder {

    private class MaskGenerator {
        private val seedMap = arrayOf(
            intArrayOf(0x4a, 0xd6, 0xca, 0x90, 0x67, 0xf7, 0x52),
            intArrayOf(0x5e, 0x95, 0x23, 0x9f, 0x13, 0x11, 0x7e),
            intArrayOf(0x47, 0x74, 0x3d, 0x90, 0xaa, 0x3f, 0x51),
            intArrayOf(0xc6, 0x09, 0xd5, 0x9f, 0xfa, 0x66, 0xf9),
            intArrayOf(0xf3, 0xd6, 0xa1, 0x90, 0xa0, 0xf7, 0xf0),
            intArrayOf(0x1d, 0x95, 0xde, 0x9f, 0x84, 0x11, 0xf4),
            intArrayOf(0x0e, 0x74, 0xbb, 0x90, 0xbc, 0x3f, 0x92),
            intArrayOf(0x00, 0x09, 0x5b, 0x9f, 0x62, 0x66, 0xa1)
        )

        private var x = -1
        private var y = 8
        private var dx = 1
        private var index = -1

        fun nextMask(): Int {
            index++

            val result = when {
                x < 0 -> {
                    dx = 1
                    y = (8 - y) % 8
                    0xc3
                }

                x > 6 -> {
                    dx = -1
                    y = 7 - y
                    0xd8
                }

                else -> seedMap[y][x]
            }

            x += dx

            if (
                index == 0x8000 ||
                (index > 0x8000 && (index + 1) % 0x8000 == 0)
            ) {
                return nextMask()
            }

            return result
        }
    }

    fun decode(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = 256 * 1024,
        onBytesProcessed: ((Long) -> Unit)? = null
    ): Long {
        require(bufferSize > 0)

        val mask = MaskGenerator()
        val buffer = ByteArray(bufferSize)
        var totalBytes = 0L

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue

            for (i in 0 until count) {
                buffer[i] = ((buffer[i].toInt() and 0xff) xor mask.nextMask()).toByte()
            }

            output.write(buffer, 0, count)
            totalBytes += count
            onBytesProcessed?.invoke(totalBytes)
        }

        output.flush()
        return totalBytes
    }

    fun outputExtension(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "qmc0", "qmc3" -> "mp3"
            "qmcflac" -> "flac"
            "qmcogg" -> "ogg"
            else -> null
        }
}
