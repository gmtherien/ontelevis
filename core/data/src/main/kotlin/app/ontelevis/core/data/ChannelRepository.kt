package app.ontelevis.core.data

import app.ontelevis.core.data.db.ChannelDao
import app.ontelevis.core.data.db.ChannelEntity
import app.ontelevis.core.data.db.ChannelGroupDao
import app.ontelevis.core.data.db.GroupPrefEntity
import app.ontelevis.core.data.db.HistoryEntity
import app.ontelevis.core.data.db.LibraryDao
import app.ontelevis.core.data.db.PlaylistDao
import app.ontelevis.core.data.importer.PlaylistImporter
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ChannelGroup
import app.ontelevis.core.parser.xtream.XtreamUrls
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

interface ChannelRepository {
    /** Categories of every enabled playlist, hidden ones included, in the user's then the provider's order. */
    fun observeGroups(): Flow<List<ChannelGroup>>

    /** Favourite channels in the user's order. */
    fun observeFavorites(): Flow<List<Channel>>

    fun observeFavoriteIds(): Flow<Set<String>>

    /** Adds or removes [channel] from the favourites; returns true when it is now a favourite. */
    suspend fun toggleFavorite(channel: Channel): Boolean

    /** Last channels played, most recent first. */
    fun observeHistory(): Flow<List<Channel>>

    suspend fun recordWatched(channel: Channel)

    suspend fun clearHistory()

    /** Moves a category one place up ([step] -1) or down (+1) inside its playlist. */
    suspend fun moveGroup(
        group: ChannelGroup,
        step: Int,
    )

    suspend fun setGroupHidden(
        group: ChannelGroup,
        hidden: Boolean,
    )

    fun observeChannels(group: ChannelGroup): Flow<List<Channel>>

    suspend fun channel(id: String): Channel?

    /**
     * Link to the recording of [channel] between [startMillis] and [stopMillis] (catch-up), or null
     * when the channel has none (only Xtream panels for now).
     */
    suspend fun catchupUrl(
        channel: Channel,
        startMillis: Long,
        stopMillis: Long,
    ): String?

    /** Channels of the category [channel] belongs to, in provider order (for CH+ / CH-). */
    suspend fun channelsAround(channel: Channel): List<Channel>
}

internal class RoomChannelRepository
    @Inject
    constructor(
        private val groupDao: ChannelGroupDao,
        private val channelDao: ChannelDao,
        private val libraryDao: LibraryDao,
        private val playlistDao: PlaylistDao,
    ) : ChannelRepository {
        override fun observeGroups(): Flow<List<ChannelGroup>> =
            groupDao.observeAll().map { rows ->
                rows.map { ChannelGroup(it.playlistId, it.name, it.channelCount, it.hidden) }
            }

        override fun observeFavorites(): Flow<List<Channel>> =
            libraryDao.observeFavorites().map { rows -> rows.map { it.toModel() } }

        override fun observeFavoriteIds(): Flow<Set<String>> = libraryDao.observeFavoriteIds().map { it.toSet() }

        override suspend fun toggleFavorite(channel: Channel): Boolean =
            if (libraryDao.isFavorite(channel.id)) {
                libraryDao.removeFavorite(channel.id)
                false
            } else {
                libraryDao.addFavorite(channel.id)
                true
            }

        override fun observeHistory(): Flow<List<Channel>> =
            libraryDao.observeHistory().map { rows -> rows.map { it.toModel() } }

        override suspend fun recordWatched(channel: Channel) {
            libraryDao.recordWatched(HistoryEntity(channel.id, System.currentTimeMillis()))
            libraryDao.trimHistory(HISTORY_SIZE)
        }

        override suspend fun clearHistory() = libraryDao.clearHistory()

        override suspend fun moveGroup(
            group: ChannelGroup,
            step: Int,
        ) {
            if (group.shortcut != null) return
            val names = groupDao.orderedNames(group.playlistId).toMutableList()
            val from = names.indexOf(group.name)
            val to = from + step
            if (from < 0 || to !in names.indices) return
            names.add(to, names.removeAt(from))
            // Every category of the playlist gets its place, so later provider changes cannot shuffle them.
            val prefs = libraryDao.groupPrefs(group.playlistId).associateBy { it.name }
            libraryDao.upsertGroupPrefs(
                names.mapIndexed { index, name ->
                    (prefs[name] ?: GroupPrefEntity(group.playlistId, name)).copy(position = index)
                },
            )
        }

        override suspend fun setGroupHidden(
            group: ChannelGroup,
            hidden: Boolean,
        ) {
            if (group.shortcut != null) return
            val current = libraryDao.groupPrefs(group.playlistId).firstOrNull { it.name == group.name }
            libraryDao.upsertGroupPrefs(
                listOf((current ?: GroupPrefEntity(group.playlistId, group.name)).copy(hidden = hidden)),
            )
        }

        override fun observeChannels(group: ChannelGroup): Flow<List<Channel>> =
            channelDao.observeInGroup(group.playlistId, group.name).map { rows -> rows.map { it.toModel() } }

        override suspend fun channel(id: String): Channel? = channelDao.get(id)?.toModel()

        override suspend fun catchupUrl(
            channel: Channel,
            startMillis: Long,
            stopMillis: Long,
        ): String? {
            if (channel.catchupDays <= 0) return null
            val playlist = playlistDao.get(channel.playlistId) ?: return null
            if (playlist.kind != PlaylistImporter.KIND_XTREAM) return null
            val urls = XtreamUrls(playlist.url, playlist.username.orEmpty(), playlist.password.orEmpty())
            val minutes = ((stopMillis - startMillis + 59_999) / 60_000).toInt().coerceAtLeast(1)
            return urls.timeshift(
                streamId = channel.rawId,
                start = Instant.ofEpochMilli(startMillis),
                durationMinutes = minutes,
                zone = XtreamUrls.zoneOf(playlist.serverTimezone),
            )
        }

        override suspend fun channelsAround(channel: Channel): List<Channel> =
            channelDao.inGroup(channel.playlistId, channel.group).map { it.toModel() }
    }

/** How many channels the History shortcut keeps. */
private const val HISTORY_SIZE = 50

private fun ChannelEntity.toModel() =
    Channel(
        id = id,
        playlistId = playlistId,
        rawId = rawId,
        number = number,
        name = name,
        streamUrl = streamUrl,
        group = groupName,
        logoUrl = logoUrl,
        epgId = epgId,
        catchupDays = catchupDays,
    )
