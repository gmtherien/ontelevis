package app.ontelevis.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * The user's own organisation of the live channels. Kept apart from the imported tables on purpose:
 * a playlist refresh deletes and rewrites channels and groups, these rows must survive it.
 */

/** A favourite channel, by app channel id (`playlistId::rawId`). */
@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val channelId: String,
    /** Order in the Favourites shortcut (new ones go last). */
    val position: Long,
)

/** A channel that played, for the History shortcut. */
@Entity(tableName = "channel_history")
data class HistoryEntity(
    @PrimaryKey val channelId: String,
    val watchedAt: Long,
)

/** The user's order and visibility of one provider category. */
@Entity(
    tableName = "group_pref",
    primaryKeys = ["playlistId", "name"],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class GroupPrefEntity(
    val playlistId: Long,
    val name: String,
    /** Null: provider order, after the categories the user placed. */
    val position: Int? = null,
    val hidden: Boolean = false,
)

@Dao
interface LibraryDao {
    // Favourites

    @Query(
        """
        SELECT c.* FROM favorite f
        JOIN channel c ON c.id = f.channelId
        JOIN playlist p ON p.id = c.playlistId
        WHERE p.enabled = 1
        ORDER BY f.position
        """,
    )
    fun observeFavorites(): Flow<List<ChannelEntity>>

    @Query("SELECT channelId FROM favorite")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE channelId = :channelId)")
    suspend fun isFavorite(channelId: String): Boolean

    @Query(
        "INSERT OR IGNORE INTO favorite (channelId, position) SELECT :channelId, COALESCE(MAX(position), 0) + 1 FROM favorite",
    )
    suspend fun addFavorite(channelId: String)

    @Query("DELETE FROM favorite WHERE channelId = :channelId")
    suspend fun removeFavorite(channelId: String)

    // History

    @Query(
        """
        SELECT c.* FROM channel_history h
        JOIN channel c ON c.id = h.channelId
        JOIN playlist p ON p.id = c.playlistId
        WHERE p.enabled = 1
        ORDER BY h.watchedAt DESC
        """,
    )
    fun observeHistory(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordWatched(entry: HistoryEntity)

    @Query(
        "DELETE FROM channel_history WHERE channelId NOT IN " +
            "(SELECT channelId FROM channel_history ORDER BY watchedAt DESC LIMIT :keep)",
    )
    suspend fun trimHistory(keep: Int)

    @Query("DELETE FROM channel_history")
    suspend fun clearHistory()

    // Channels of a removed playlist (the channel ids start with its id).

    @Query("DELETE FROM favorite WHERE channelId LIKE :prefix || '%'")
    suspend fun deleteFavoritesWithPrefix(prefix: String)

    @Query("DELETE FROM channel_history WHERE channelId LIKE :prefix || '%'")
    suspend fun deleteHistoryWithPrefix(prefix: String)

    // Categories

    @Query("SELECT * FROM group_pref WHERE playlistId = :playlistId")
    suspend fun groupPrefs(playlistId: Long): List<GroupPrefEntity>

    @Upsert
    suspend fun upsertGroupPrefs(prefs: List<GroupPrefEntity>)
}
