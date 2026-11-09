package app.ontelevis.core.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Live pause for continuous streams (MPEG-TS over HTTP): a thread keeps reading the provider's
 * stream into a ring file on disk while the player reads from that file at its own pace. Paused,
 * the player stops reading but the download goes on, so playback resumes where it stopped (behind
 * live) on the same single connection (many providers allow only one). When the pause outlasts the
 * file, the oldest part is overwritten and reading fails: the player reconnects to live.
 */
@OptIn(UnstableApi::class)
class TimeshiftDataSource(
    private val upstream: DataSource,
    private val file: File,
    private val capacity: Long,
) : DataSource {
    private val ring = RingFile(file, capacity)
    private var pump: Thread? = null
    private var uri: Uri? = null

    @Volatile private var closed = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        closed = false
        ring.reset()
        // Opened here so connection errors (404, refused) reach the player at once.
        upstream.open(dataSpec)
        pump =
            thread(name = "ontelevis-timeshift", isDaemon = true) {
                val buffer = ByteArray(CHUNK)
                try {
                    while (!closed) {
                        val n = upstream.read(buffer, 0, buffer.size)
                        if (n == C.RESULT_END_OF_INPUT) break
                        ring.write(buffer, n)
                    }
                    ring.finish(null)
                } catch (e: IOException) {
                    ring.finish(if (closed) null else e)
                } catch (e: InterruptedException) {
                    ring.finish(null)
                }
            }
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = ring.read(buffer, offset, length)

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        ring.finish(null)
        runCatching { upstream.close() }
        pump?.interrupt()
        pump = null
        ring.close()
        file.delete()
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val directory: File,
        private val capacity: Long,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            TimeshiftDataSource(upstream.createDataSource(), File(directory, "timeshift.ts"), capacity)
    }

    private companion object {
        const val CHUNK = 64 * 1024
    }
}

/**
 * Bytes written by one thread and read by another through a fixed-size file used as a ring
 * (positional reads and writes: the two threads never share a file pointer). Positions count every
 * byte since [reset]; a writer that laps the reader makes the next read fail.
 */
@OptIn(UnstableApi::class)
internal class RingFile(
    file: File,
    private val capacity: Long,
) {
    private val raf = RandomAccessFile(file, "rw")
    private val channel = raf.channel
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var written = 0L
    private var read = 0L
    private var done = false
    private var error: IOException? = null

    fun reset() =
        lock.withLock {
            written = 0
            read = 0
            done = false
            error = null
        }

    fun write(
        bytes: ByteArray,
        count: Int,
    ) {
        var offset = 0
        while (offset < count) {
            val at = written % capacity
            val n = minOf((count - offset).toLong(), capacity - at).toInt()
            val chunk = ByteBuffer.wrap(bytes, offset, n)
            var position = at
            while (chunk.hasRemaining()) position += channel.write(chunk, position)
            offset += n
            lock.withLock {
                written += n
                changed.signalAll()
            }
        }
    }

    /** End of the download: [failure] null for a normal end (or a close). */
    fun finish(failure: IOException?) =
        lock.withLock {
            if (!done) {
                done = true
                error = failure
            }
            changed.signalAll()
        }

    /** Blocks until bytes are there; [C.RESULT_END_OF_INPUT] at the end of the stream. */
    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        val (from, available) =
            lock.withLock {
                while (written == read && !done) changed.await()
                if (written - read > capacity) throw IOException(LAPPED)
                if (written == read) {
                    error?.let { throw it }
                    return C.RESULT_END_OF_INPUT
                }
                read to written - read
            }
        val at = from % capacity
        val n = minOf(length.toLong(), available, capacity - at).toInt()
        val target = ByteBuffer.wrap(buffer, offset, n)
        var position = at
        while (target.hasRemaining()) {
            val got = channel.read(target, position)
            if (got < 0) throw IOException("Timeshift file ended")
            position += got
        }
        lock.withLock {
            // Overwritten while it was being read: those bytes are not the stream any more.
            if (written - from > capacity) throw IOException(LAPPED)
            read = from + n
        }
        return n
    }

    fun close() = runCatching { raf.close() }

    private companion object {
        const val LAPPED = "Live pause longer than the buffer"
    }
}
