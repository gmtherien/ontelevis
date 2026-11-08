package app.ontelevis.core.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

class HttpStatusException(
    val code: Int,
) : IOException("HTTP $code")

/** Blocking HTTP GET that hands the body over as a stream. Call it off the main thread. */
@Singleton
class HttpSource
    @Inject
    constructor(
        private val client: OkHttpClient,
    ) {
        fun <T> open(
            url: String,
            read: (InputStream) -> T,
        ): T {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                // Never log the URL: Xtream credentials are in it.
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                return response.body.byteStream().use(read)
            }
        }
    }
