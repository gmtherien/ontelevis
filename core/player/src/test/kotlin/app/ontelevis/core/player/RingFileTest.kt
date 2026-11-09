package app.ontelevis.core.player

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

class RingFileTest {
    private fun ring(capacity: Long) = RingFile(File.createTempFile("ring", ".bin").apply { deleteOnExit() }, capacity)

    @Test
    fun `bytes come out in order across the wrap`() {
        val ring = ring(capacity = 10)
        val out = ByteArray(25)
        var got = 0
        val reader =
            thread {
                while (got < out.size) {
                    val n = ring.read(out, got, out.size - got)
                    if (n < 0) break
                    got += n
                }
            }
        val data = ByteArray(25) { it.toByte() }
        // Small writes, so the writer never laps the reader.
        data.toList().chunked(3).forEach { chunk ->
            ring.write(chunk.toByteArray(), chunk.size)
            Thread.sleep(2)
        }
        ring.finish(null)
        reader.join(2_000)
        assertArrayEquals(data, out)
    }

    @Test
    fun `end of stream after the last byte`() {
        val ring = ring(capacity = 16)
        ring.write(byteArrayOf(1, 2, 3), 3)
        ring.finish(null)
        val out = ByteArray(8)
        assertEquals(3, ring.read(out, 0, 8))
        assertEquals(-1, ring.read(out, 0, 8))
    }

    @Test
    fun `a pause longer than the file fails the read`() {
        val ring = ring(capacity = 8)
        ring.write(ByteArray(20), 20)
        assertThrows<IOException> { ring.read(ByteArray(4), 0, 4) }
    }
}
