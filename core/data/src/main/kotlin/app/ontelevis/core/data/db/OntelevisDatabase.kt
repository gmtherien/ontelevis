package app.ontelevis.core.data.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "m3u" or "xtream". */
    val kind: String,
    /** M3U URL, or the Xtream server address. */
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val enabled: Boolean = true,
    val channelCount: Int = 0,
    val lastRefreshAt: Long? = null,
    /** Name of a RefreshError, null when the last refresh worked. */
    val lastError: String? = null,
    /** Guide link announced by the M3U header (url-tvg), used when [epgUrl] is empty. */
    val detectedEpgUrl: String? = null,
    val lastEpgAt: Long? = null,
    val programmeCount: Int = 0,
    /** Name of a RefreshError for the guide, null when the last guide refresh worked. */
    val epgError: String? = null,
    // Added in version 3: defaults let the automatic migration fill existing rows.
    @ColumnInfo(defaultValue = "0") val movieCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val seriesCount: Int = 0,
    val lastVodAt: Long? = null,
    /** Xtream panel time zone (`server_info.timezone`), for catch-up start times. */
    val serverTimezone: String? = null,
)

@Entity(
    tableName = "channel_group",
    primaryKeys = ["playlistId", "name"],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ChannelGroupEntity(
    val playlistId: Long,
    val name: String,
    val sortOrder: Int,
    val hidden: Boolean = false,
)

@Entity(
    tableName = "channel",
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId", "groupName", "sortOrder"), Index("epgId")],
)
data class ChannelEntity(
    /** `playlistId::rawId`. */
    @PrimaryKey val id: String,
    val playlistId: Long,
    val rawId: String,
    val number: Int,
    val name: String,
    val groupName: String,
    val streamUrl: String,
    val logoUrl: String?,
    val epgId: String?,
    val catchupDays: Int,
    val sortOrder: Int,
)

/** Row of the categories query: a group and how many channels it holds. */
data class GroupRow(
    val playlistId: Long,
    val name: String,
    val channelCount: Int,
    val hidden: Boolean,
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<PlaylistEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlist SET channelCount = :count, lastRefreshAt = :at, lastError = NULL WHERE id = :id")
    suspend fun markRefreshed(
        id: Long,
        count: Int,
        at: Long,
    )

    @Query("UPDATE playlist SET serverTimezone = :timezone WHERE id = :id")
    fun setServerTimezone(
        id: Long,
        timezone: String?,
    )

    @Query("UPDATE playlist SET detectedEpgUrl = :url WHERE id = :id")
    suspend fun setDetectedEpgUrl(
        id: Long,
        url: String?,
    )

    @Query("UPDATE playlist SET lastEpgAt = :at, programmeCount = :count, epgError = NULL WHERE id = :id")
    suspend fun markEpgRefreshed(
        id: Long,
        count: Int,
        at: Long,
    )

    @Query("UPDATE playlist SET movieCount = :movies, seriesCount = :series, lastVodAt = :at WHERE id = :id")
    suspend fun setVodCounts(
        id: Long,
        movies: Int,
        series: Int,
        at: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE playlist SET epgError = :error WHERE id = :id")
    suspend fun markEpgFailed(
        id: Long,
        error: String,
    )

    @Query("UPDATE playlist SET lastError = :error WHERE id = :id")
    suspend fun markFailed(
        id: Long,
        error: String,
    )

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChannelDao {
    @Query(
        "SELECT DISTINCT lower(epgId) FROM channel WHERE playlistId = :playlistId AND epgId IS NOT NULL AND epgId != ''",
    )
    fun epgKeys(playlistId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channel WHERE playlistId = :playlistId")
    fun deleteForPlaylist(playlistId: Long)

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun get(id: String): ChannelEntity?

    /** Longest catch-up of the playlist's channels, in days (the guide keeps that much past). */
    @Query("SELECT COALESCE(MAX(catchupDays), 0) FROM channel WHERE playlistId = :playlistId")
    fun maxCatchupDays(playlistId: Long): Int

    @Query("SELECT * FROM channel WHERE playlistId = :playlistId AND groupName = :group ORDER BY sortOrder")
    suspend fun inGroup(
        playlistId: Long,
        group: String,
    ): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE playlistId = :playlistId AND groupName = :group ORDER BY sortOrder")
    fun observeInGroup(
        playlistId: Long,
        group: String,
    ): Flow<List<ChannelEntity>>
}

@Dao
interface ChannelGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(groups: List<ChannelGroupEntity>)

    @Query("SELECT * FROM channel_group WHERE playlistId = :playlistId")
    fun groupsOf(playlistId: Long): List<ChannelGroupEntity>

    @Query("DELETE FROM channel_group WHERE playlistId = :playlistId")
    fun deleteForPlaylist(playlistId: Long)

    @Query(
        """
        SELECT g.playlistId AS playlistId, g.name AS name,
            (SELECT COUNT(*) FROM channel c WHERE c.playlistId = g.playlistId AND c.groupName = g.name) AS channelCount,
            COALESCE(gp.hidden, 0) AS hidden
        FROM channel_group g JOIN playlist p ON p.id = g.playlistId
        LEFT JOIN group_pref gp ON gp.playlistId = g.playlistId AND gp.name = g.name
        WHERE p.enabled = 1
        ORDER BY g.playlistId, gp.position IS NULL, gp.position, g.sortOrder
        """,
    )
    fun observeAll(): Flow<List<GroupRow>>

    /** Category names of one playlist in the order shown (the user's, then the provider's). */
    @Query(
        """
        SELECT g.name FROM channel_group g
        LEFT JOIN group_pref gp ON gp.playlistId = g.playlistId AND gp.name = g.name
        WHERE g.playlistId = :playlistId
        ORDER BY gp.position IS NULL, gp.position, g.sortOrder
        """,
    )
    suspend fun orderedNames(playlistId: Long): List<String>
}

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelGroupEntity::class,
        ChannelEntity::class,
        ProgrammeEntity::class,
        VodCategoryEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        WatchProgressEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        GroupPrefEntity::class,
        VodFavoriteEntity::class,
    ],
    version = 7,
    // From version 3 on, schema changes keep the user's data (no more wipe).
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
    exportSchema = true,
)
abstract class OntelevisDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun channelDao(): ChannelDao

    abstract fun channelGroupDao(): ChannelGroupDao

    abstract fun programmeDao(): ProgrammeDao

    abstract fun vodDao(): VodDao

    abstract fun libraryDao(): LibraryDao
}
