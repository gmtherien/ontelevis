package app.ontelevis.core.data

import app.ontelevis.core.data.db.PlaylistDao
import app.ontelevis.core.data.db.PlaylistEntity
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.PlaylistSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    suspend fun add(
        name: String,
        source: PlaylistSource,
    ): Long

    suspend fun remove(id: Long)
}

internal class RoomPlaylistRepository
    @Inject
    constructor(
        private val dao: PlaylistDao,
    ) : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> =
            dao.observeAll().map { rows ->
                rows.map { it.toModel() }
            }

        override suspend fun add(
            name: String,
            source: PlaylistSource,
        ): Long = dao.insert(source.toEntity(name))

        override suspend fun remove(id: Long) = dao.delete(id)
    }

private fun PlaylistEntity.toModel(): Playlist {
    val source =
        when (kind) {
            KIND_XTREAM -> PlaylistSource.Xtream(url, username.orEmpty(), password.orEmpty())
            else -> PlaylistSource.M3u(url, epgUrl)
        }
    return Playlist(id = id, name = name, source = source, enabled = enabled)
}

private fun PlaylistSource.toEntity(name: String): PlaylistEntity =
    when (this) {
        is PlaylistSource.M3u -> {
            PlaylistEntity(name = name, kind = KIND_M3U, url = url, epgUrl = epgUrl)
        }

        is PlaylistSource.Xtream -> {
            PlaylistEntity(name = name, kind = KIND_XTREAM, url = server, username = username, password = password)
        }
    }

private const val KIND_M3U = "m3u"
private const val KIND_XTREAM = "xtream"
