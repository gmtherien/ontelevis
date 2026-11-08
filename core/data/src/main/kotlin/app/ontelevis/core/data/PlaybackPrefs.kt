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

        private companion object {
            const val KEY_LAST_CHANNEL = "last_channel_id"
        }
    }
