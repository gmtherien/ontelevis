package app.ontelevis.core.data

import app.ontelevis.core.data.db.PlaylistDao
import app.ontelevis.core.data.db.PlaylistEntity
import app.ontelevis.core.data.importer.EpgImporter
import app.ontelevis.core.data.importer.PlaylistImporter
import app.ontelevis.core.data.importer.PlaylistImporter.Companion.KIND_M3U
import app.ontelevis.core.data.importer.PlaylistImporter.Companion.KIND_XTREAM
import app.ontelevis.core.data.importer.RefreshException
import app.ontelevis.core.data.importer.VodImporter
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.PlaylistSource
import app.ontelevis.core.model.RefreshError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    suspend fun add(
        name: String,
        source: PlaylistSource,
    ): Long

    suspend fun remove(id: Long)

    /** Downloads the playlist again. Returns the error, or null when it worked. */
    suspend fun refresh(id: Long): RefreshError?

    /** Downloads the guide again. Returns the error, or null when it worked (or there is no guide). */
    suspend fun refreshEpg(id: Long): RefreshError?

    /** Downloads the movies and series again (Xtream; M3U ones come with [refresh]). */
    suspend fun refreshVod(id: Long): RefreshError?

    /** Refreshes channels, movies and series, then guide of every enabled playlist. Returns how many failed. */
    suspend fun refreshAll(): Int
}

@Singleton
internal class RoomPlaylistRepository
    @Inject
    constructor(
        private val dao: PlaylistDao,
        private val importer: PlaylistImporter,
        private val epgImporter: EpgImporter,
        private val vodImporter: VodImporter,
    ) : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> =
            dao.observeAll().map { rows ->
                rows.map { it.toModel() }
            }

        override suspend fun add(
            name: String,
            source: PlaylistSource,
        ): Long = dao.insert(source.toEntity(name.trim()))

        override suspend fun remove(id: Long) = dao.delete(id)

        override suspend fun refresh(id: Long): RefreshError? {
            val playlist = dao.get(id) ?: return null
            return try {
                val result = importer.import(playlist)
                dao.markRefreshed(id, result.channelCount, System.currentTimeMillis())
                if (playlist.kind == KIND_M3U) {
                    dao.setDetectedEpgUrl(id, result.detectedEpgUrl)
                    dao.setVodCounts(id, result.movieCount ?: 0, result.seriesCount ?: 0)
                }
                null
            } catch (e: RefreshException) {
                dao.markFailed(id, e.reason.name)
                e.reason
            }
        }

        override suspend fun refreshEpg(id: Long): RefreshError? {
            val playlist = dao.get(id) ?: return null
            return try {
                val count = epgImporter.import(playlist) ?: return null
                dao.markEpgRefreshed(id, count, System.currentTimeMillis())
                null
            } catch (e: RefreshException) {
                dao.markEpgFailed(id, e.reason.name)
                e.reason
            }
        }

        override suspend fun refreshVod(id: Long): RefreshError? {
            val playlist = dao.get(id) ?: return null
            return try {
                val (movies, series) = vodImporter.import(playlist) ?: return null
                dao.setVodCounts(id, movies, series)
                null
            } catch (e: RefreshException) {
                e.reason
            }
        }

        override suspend fun refreshAll(): Int =
            dao.enabled().count { playlist ->
                val channelsFailed = refresh(playlist.id) != null
                val vodFailed = refreshVod(playlist.id) != null
                val guideFailed = refreshEpg(playlist.id) != null
                channelsFailed || vodFailed || guideFailed
            }
    }

private fun PlaylistEntity.toModel(): Playlist {
    val source =
        when (kind) {
            KIND_XTREAM -> PlaylistSource.Xtream(url, username.orEmpty(), password.orEmpty())
            else -> PlaylistSource.M3u(url, epgUrl)
        }
    return Playlist(
        id = id,
        name = name,
        source = source,
        enabled = enabled,
        channelCount = channelCount,
        lastRefreshAt = lastRefreshAt,
        lastError = lastError.toRefreshError(),
        programmeCount = programmeCount,
        lastEpgAt = lastEpgAt,
        epgError = epgError.toRefreshError(),
        movieCount = movieCount,
        seriesCount = seriesCount,
        lastVodAt = lastVodAt,
    )
}

private fun PlaylistSource.toEntity(name: String): PlaylistEntity =
    when (this) {
        is PlaylistSource.M3u -> {
            PlaylistEntity(name = name, kind = KIND_M3U, url = url.trim(), epgUrl = epgUrl?.trim()?.ifEmpty { null })
        }

        is PlaylistSource.Xtream -> {
            PlaylistEntity(
                name = name,
                kind = KIND_XTREAM,
                url = server.trim(),
                username = username.trim(),
                password = password,
            )
        }
    }

private fun String?.toRefreshError(): RefreshError? =
    this?.let { name ->
        RefreshError.entries.firstOrNull {
            it.name ==
                name
        }
    }
