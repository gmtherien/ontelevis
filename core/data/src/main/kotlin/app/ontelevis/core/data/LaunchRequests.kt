package app.ontelevis.core.data

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something to open when the app is started from outside: a reminder, Google TV, a voice search. */
sealed interface LaunchRequest {
    /** Play a live channel in full screen. */
    data class PlayChannel(
        val channelId: String,
    ) : LaunchRequest

    /** Play a movie or an episode from its resume point. */
    data class PlayVod(
        val contentId: String,
    ) : LaunchRequest

    /** Open a movie, a series or an episode's series page. */
    data class OpenVod(
        val contentId: String,
    ) : LaunchRequest

    /** "Play RDS" (voice search, search intent): resolved to a channel or a title by the app. */
    data class Search(
        val query: String,
    ) : LaunchRequest
}

/**
 * The pending [LaunchRequest], set by the activity from its intent and taken by the screen that
 * handles it ([take]).
 */
@Singleton
class LaunchRequests
    @Inject
    constructor() {
        private val _pending = MutableStateFlow<LaunchRequest?>(null)
        val pending: StateFlow<LaunchRequest?> = _pending.asStateFlow()

        fun post(request: LaunchRequest) {
            _pending.value = request
        }

        private val _groupToOpen = MutableStateFlow<Pair<Long, String>?>(null)

        /** Category (playlist id, name) the TV screen should show: the one of a channel opened from outside. */
        val groupToOpen: StateFlow<Pair<Long, String>?> = _groupToOpen.asStateFlow()

        fun openGroup(key: Pair<Long, String>) {
            _groupToOpen.value = key
        }

        fun groupOpened(key: Pair<Long, String>) {
            _groupToOpen.compareAndSet(key, null)
        }

        /** Returns [request] and clears it if it is still the pending one (it was not handled elsewhere). */
        fun take(request: LaunchRequest): Boolean = _pending.compareAndSet(request, null)

        companion object {
            const val SCHEME = "ontelevis"

            fun channelUri(channelId: String): Uri = Uri.parse("$SCHEME://channel/${Uri.encode(channelId)}")

            fun vodUri(contentId: String): Uri = Uri.parse("$SCHEME://vod/${Uri.encode(contentId)}")

            fun playUri(contentId: String): Uri = Uri.parse("$SCHEME://play/${Uri.encode(contentId)}")

            /** `ontelevis://channel|vod|play/<id>`, or a search query; null for a plain launch. */
            fun parse(intent: Intent?): LaunchRequest? {
                intent ?: return null
                val data = intent.data
                if (data?.scheme == SCHEME) {
                    val id = data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
                    return when (data.host) {
                        "channel" -> LaunchRequest.PlayChannel(id)
                        "vod" -> LaunchRequest.OpenVod(id)
                        "play" -> LaunchRequest.PlayVod(id)
                        else -> null
                    }
                }
                val query =
                    intent.getStringExtra(SEARCH_QUERY)?.trim()?.takeIf {
                        it.isNotEmpty() && intent.action in SEARCH_ACTIONS
                    }
                return query?.let { LaunchRequest.Search(it) }
            }

            /** `SearchManager.QUERY`. */
            private const val SEARCH_QUERY = "query"
            private val SEARCH_ACTIONS =
                setOf(
                    "android.intent.action.SEARCH",
                    "android.media.action.MEDIA_PLAY_FROM_SEARCH",
                    "com.google.android.gms.actions.SEARCH_ACTION",
                )
        }
    }
