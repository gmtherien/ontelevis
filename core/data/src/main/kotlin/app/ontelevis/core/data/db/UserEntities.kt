package app.ontelevis.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Track and picture choices remembered for one channel, series or movie ([key]: a channel id, a
 * series id for all of its episodes, or a movie id). Null fields follow the defaults in Settings.
 */
@Entity(tableName = "playback_pref")
data class PlaybackPrefEntity(
    @PrimaryKey val key: String,
    /** BCP 47 language of the chosen audio track, or null for the default. */
    val audioLang: String? = null,
    /** Label of the chosen audio track (two tracks can share a language: "English" / "English AD"). */
    val audioLabel: String? = null,
    val textLang: String? = null,
    val textLabel: String? = null,
    /** True when subtitles were turned off for this item. */
    val textOff: Boolean = false,
    /** Name of a PictureFormat, or null for the default. */
    val format: String? = null,
)

@Dao
interface PlaybackPrefDao {
    @Query("SELECT * FROM playback_pref WHERE `key` = :key")
    suspend fun get(key: String): PlaybackPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(pref: PlaybackPrefEntity)

    @Query("SELECT * FROM playback_pref")
    suspend fun all(): List<PlaybackPrefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(prefs: List<PlaybackPrefEntity>)
}

/** A programme the user asked to be reminded of. Kept across guide refreshes (keyed by channel + start). */
@Entity(tableName = "reminder", primaryKeys = ["channelId", "start"])
data class ReminderEntity(
    val channelId: String,
    /** Programme start, epoch milliseconds. */
    val start: Long,
    val stop: Long,
    val title: String,
    val channelName: String,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY start")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE start > :after ORDER BY start")
    suspend fun upcoming(after: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE channelId = :channelId AND start = :start")
    suspend fun get(
        channelId: String,
        start: Long,
    ): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE channelId = :channelId AND start = :start")
    suspend fun delete(
        channelId: String,
        start: Long,
    )

    @Query("DELETE FROM reminder WHERE start < :before")
    suspend fun deleteOlderThan(before: Long)
}
