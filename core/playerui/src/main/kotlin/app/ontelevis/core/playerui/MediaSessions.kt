package app.ontelevis.core.playerui

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import app.ontelevis.core.data.LaunchRequest
import app.ontelevis.core.data.LaunchRequests
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One media session for the app, on whichever player is in use (live or movie): play / pause from the
 * remote's media keys and the Assistant, and the "Now playing" card of Android TV. A "play X" request
 * from the Assistant becomes a [LaunchRequest.Search], resolved by the app like a voice search.
 */
@Singleton
class MediaSessions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val requests: LaunchRequests,
    ) {
        private var session: MediaSession? = null

        private val callback =
            object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    mediaItems
                        .firstNotNullOfOrNull {
                            it.requestMetadata.searchQuery?.takeIf(
                                String::isNotBlank,
                            )
                        }?.let {
                            requests.post(LaunchRequest.Search(it))
                        }
                    // The app picks the stream itself; a controller never replaces what plays.
                    return Futures.immediateFailedFuture(UnsupportedOperationException())
                }
            }

        /** Puts the session on [player] (created with the first player). */
        fun use(player: Player?) {
            player ?: return
            val current = session
            if (current == null) {
                session =
                    MediaSession
                        .Builder(context, player)
                        .setId(SESSION_ID)
                        .setCallback(callback)
                        .build()
            } else if (current.player !== player) {
                current.player = player
            }
        }

        private companion object {
            const val SESSION_ID = "ontelevis"
        }
    }
