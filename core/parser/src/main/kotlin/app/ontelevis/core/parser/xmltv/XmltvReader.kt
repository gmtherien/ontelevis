package app.ontelevis.core.parser.xmltv

import app.ontelevis.core.model.Programme
import java.io.InputStream

/**
 * Reads an XMLTV guide as a stream and hands programmes over in batches, so the caller
 * can insert each batch into the database. The whole guide is never held in memory.
 *
 * Implementation comes in V1 (XmlPullParser on Android, gzip support, timezone offsets).
 */
fun interface XmltvReader {
    fun read(
        input: InputStream,
        batchSize: Int,
        onBatch: (List<Programme>) -> Unit,
    )
}
