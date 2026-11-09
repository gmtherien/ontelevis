package app.ontelevis.core.data

import app.ontelevis.core.data.db.ProgrammeDao
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.Programme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface EpgRepository {
    /**
     * Programmes overlapping [from, to) for each channel, keyed by channel id, in time order.
     * Without [withDescription] (the guide grid) descriptions are left out: they are the bulk of the
     * data and only the focused programme shows one ([description]).
     */
    suspend fun programmes(
        channels: List<Channel>,
        from: Long,
        to: Long,
        withDescription: Boolean = true,
    ): Map<String, List<Programme>>

    /** Description of one programme (the one focused in the guide). */
    suspend fun description(
        channel: Channel,
        startMillis: Long,
    ): String?
}

internal class RoomEpgRepository
    @Inject
    constructor(
        private val dao: ProgrammeDao,
    ) : EpgRepository {
        override suspend fun programmes(
            channels: List<Channel>,
            from: Long,
            to: Long,
            withDescription: Boolean,
        ): Map<String, List<Programme>> =
            withContext(Dispatchers.Default) {
                val result = HashMap<String, List<Programme>>()
                // A lower bound lets the index stop early: without it, every programme kept before the
                // window (up to days of catch-up) was read for each channel.
                val earliestStart = from - LONGEST_PROGRAMME_MS
                channels
                    .filter { !it.epgId.isNullOrBlank() }
                    .groupBy { it.playlistId }
                    .forEach { (playlistId, inPlaylist) ->
                        // Several copies of a channel (HD / FHD) can share one guide id.
                        val byKey = inPlaylist.groupBy { it.epgId!!.lowercase() }
                        byKey.keys.chunked(SQLITE_SAFE_ARGS).forEach { keys ->
                            val rows =
                                if (withDescription) {
                                    dao.overlapping(playlistId, keys, from, to, earliestStart).map {
                                        Programme(it.channelKey, it.start, it.stop, it.title, it.description)
                                    }
                                } else {
                                    dao.overlappingSlots(playlistId, keys, from, to, earliestStart).map {
                                        Programme(it.channelKey, it.start, it.stop, it.title)
                                    }
                                }
                            rows.groupBy { it.channelEpgId }.forEach { (key, programmes) ->
                                byKey[key]?.forEach { channel -> result[channel.id] = programmes }
                            }
                        }
                    }
                result
            }

        override suspend fun description(
            channel: Channel,
            startMillis: Long,
        ): String? {
            val key = channel.epgId?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            return dao.description(channel.playlistId, key, startMillis)
        }

        private companion object {
            const val SQLITE_SAFE_ARGS = 500

            /** Programmes longer than this that began before the window are not shown in it. */
            const val LONGEST_PROGRAMME_MS = 24L * 60 * 60 * 1000
        }
    }
