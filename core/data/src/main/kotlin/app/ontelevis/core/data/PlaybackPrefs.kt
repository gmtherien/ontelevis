package app.ontelevis.core.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small playback memory kept on the device: the channel to resume at startup. */
@Singleton
class PlaybackPrefs
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

        var lastChannelId: String?
            get() = prefs.getString(KEY_LAST_CHANNEL, null)
            set(value) = prefs.edit { putString(KEY_LAST_CHANNEL, value) }

        /**
         * Category of the last channel (playlist id, group name): the TV screen opens on it at once,
         * instead of loading the first category and then the last channel's.
         */
        var lastGroup: Pair<Long, String>?
            get() {
                val playlist = prefs.getLong(KEY_LAST_GROUP_PLAYLIST, -1L).takeIf { it >= 0 } ?: return null
                return playlist to (prefs.getString(KEY_LAST_GROUP_NAME, null) ?: return null)
            }
            set(value) =
                prefs.edit {
                    if (value == null) {
                        remove(KEY_LAST_GROUP_PLAYLIST)
                        remove(KEY_LAST_GROUP_NAME)
                    } else {
                        putLong(KEY_LAST_GROUP_PLAYLIST, value.first)
                        putString(KEY_LAST_GROUP_NAME, value.second)
                    }
                }

        private companion object {
            const val KEY_LAST_CHANNEL = "last_channel_id"
            const val KEY_LAST_GROUP_PLAYLIST = "last_group_playlist"
            const val KEY_LAST_GROUP_NAME = "last_group_name"
        }
    }
