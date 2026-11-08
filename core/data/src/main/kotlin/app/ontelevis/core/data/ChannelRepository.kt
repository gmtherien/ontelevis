package app.ontelevis.core.data

import app.ontelevis.core.data.db.ChannelDao
import app.ontelevis.core.data.db.ChannelEntity
import app.ontelevis.core.data.db.ChannelGroupDao
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ChannelGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ChannelRepository {
    /** Visible groups of every enabled playlist, in provider order. */
    fun observeGroups(): Flow<List<ChannelGroup>>

    fun observeChannels(group: ChannelGroup): Flow<List<Channel>>

    suspend fun channel(id: String): Channel?

    /** Channels of the category [channel] belongs to, in provider order (for CH+ / CH-). */
    suspend fun channelsAround(channel: Channel): List<Channel>
}

internal class RoomChannelRepository
    @Inject
    constructor(
        private val groupDao: ChannelGroupDao,
        private val channelDao: ChannelDao,
    ) : ChannelRepository {
        override fun observeGroups(): Flow<List<ChannelGroup>> =
            groupDao.observeVisible().map { rows ->
                rows.map { ChannelGroup(it.playlistId, it.name, it.channelCount) }
            }

        override fun observeChannels(group: ChannelGroup): Flow<List<Channel>> =
            channelDao.observeInGroup(group.playlistId, group.name).map { rows -> rows.map { it.toModel() } }

        override suspend fun channel(id: String): Channel? = channelDao.get(id)?.toModel()

        override suspend fun channelsAround(channel: Channel): List<Channel> =
            channelDao.inGroup(channel.playlistId, channel.group).map { it.toModel() }
    }

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
