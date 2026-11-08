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
    indices = [Index("playlistId", "channelKey", "start")],
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

@Dao
interface ProgrammeDao {
    @Insert
    fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programme WHERE playlistId = :playlistId")
    fun deleteForPlaylist(playlistId: Long)

    /** Programmes of [keys] that overlap [from, to). Keep [keys] under 500 per call (SQLite variables). */
    @Query(
        """
        SELECT * FROM programme
        WHERE playlistId = :playlistId AND channelKey IN (:keys) AND stop > :from AND start < :to
        ORDER BY channelKey, start
        """,
    )
    suspend fun overlapping(
        playlistId: Long,
        keys: List<String>,
        from: Long,
        to: Long,
    ): List<ProgrammeEntity>
}
