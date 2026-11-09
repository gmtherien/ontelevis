package app.ontelevis.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** One guide entry. [channelKey] is the lower-cased XMLTV channel id (= channel.epgId). */
@Entity(
    tableName = "programme",
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
    // stop is in the index: the overlap test (stop > from) no longer reads each row from the table.
    indices = [Index("playlistId", "channelKey", "start", "stop")],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/** A programme as the guide grid draws it (no description). */
data class ProgrammeSlot(
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
)

@Dao
interface ProgrammeDao {
    @Insert
    fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programme WHERE playlistId = :playlistId")
    fun deleteForPlaylist(playlistId: Long)

    /**
     * Programmes of [keys] that overlap [from, to) and began after [earliestStart]. Keep [keys] under
     * 500 per call (SQLite variables).
     */
    @Query(
        """
        SELECT * FROM programme
        WHERE playlistId = :playlistId AND channelKey IN (:keys)
            AND start >= :earliestStart AND start < :to AND stop > :from
        ORDER BY channelKey, start
        """,
    )
    suspend fun overlapping(
        playlistId: Long,
        keys: List<String>,
        from: Long,
        to: Long,
        earliestStart: Long,
    ): List<ProgrammeEntity>

    /** Same as [overlapping] without descriptions (the guide grid). */
    @Query(
        """
        SELECT channelKey, start, stop, title FROM programme
        WHERE playlistId = :playlistId AND channelKey IN (:keys)
            AND start >= :earliestStart AND start < :to AND stop > :from
        ORDER BY channelKey, start
        """,
    )
    suspend fun overlappingSlots(
        playlistId: Long,
        keys: List<String>,
        from: Long,
        to: Long,
        earliestStart: Long,
    ): List<ProgrammeSlot>

    @Query(
        "SELECT description FROM programme WHERE playlistId = :playlistId AND channelKey = :key AND start = :start LIMIT 1",
    )
    suspend fun description(
        playlistId: Long,
        key: String,
        start: Long,
    ): String?
}
