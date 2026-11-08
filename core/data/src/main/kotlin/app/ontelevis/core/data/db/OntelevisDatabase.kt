package app.ontelevis.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
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
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [PlaylistEntity::class], version = 1, exportSchema = true)
abstract class OntelevisDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}
