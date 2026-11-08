package app.ontelevis.core.data

import app.ontelevis.core.data.db.ProgrammeDao
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.Programme
import javax.inject.Inject

interface EpgRepository {
    /** Programmes overlapping [from, to) for each channel, keyed by channel id, in time order. */
    suspend fun programmes(
        channels: List<Channel>,
        from: Long,
        to: Long,
    ): Map<String, List<Programme>>
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
        ): Map<String, List<Programme>> {
            val result = HashMap<String, List<Programme>>()
            channels
                .filter { !it.epgId.isNullOrBlank() }
                .groupBy { it.playlistId }
                .forEach { (playlistId, inPlaylist) ->
                    // Several copies of a channel (HD / FHD) can share one guide id.
                    val byKey = inPlaylist.groupBy { it.epgId!!.lowercase() }
                    byKey.keys.chunked(SQLITE_SAFE_ARGS).forEach { keys ->
                        dao
                            .overlapping(playlistId, keys, from, to)
                            .groupBy { it.channelKey }
                            .forEach { (key, rows) ->
                                val programmes =
                                    rows.map {
                                        Programme(
                                            key,
                                            it.start,
                                            it.stop,
                                            it.title,
                                            it.description,
                                        )
                                    }
                                byKey[key]?.forEach { channel -> result[channel.id] = programmes }
                            }
                    }
                }
            return result
        }

        private companion object {
            const val SQLITE_SAFE_ARGS = 500
        }
    }
