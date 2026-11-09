package app.ontelevis.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

const val VOD_MOVIE = "movie"
const val VOD_SERIES = "series"

@Entity(
    tableName = "vod_category",
    primaryKeys = ["playlistId", "kind", "name"],
    foreignKeys = [ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE)],
)
data class VodCategoryEntity(
    val playlistId: Long,
    /** [VOD_MOVIE] or [VOD_SERIES]. */
    val kind: String,
    val name: String,
    val sortOrder: Int,
    /** Titles in the category, counted at import (a COUNT per category re-ran on every write). */
    @ColumnInfo(defaultValue = "0") val itemCount: Int = 0,
)

@Entity(
    tableName = "movie",
    foreignKeys = [ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId", "groupName", "sortOrder"), Index("added")],
)
data class MovieEntity(
    /** `playlistId::rawId`. */
    @PrimaryKey val id: String,
    val playlistId: Long,
    val rawId: String,
    val name: String,
    val groupName: String,
    val streamUrl: String,
    val posterUrl: String?,
    val rating: Double,
    val year: Int?,
    val added: Long,
    val sortOrder: Int,
    /** [searchKeyOf] the name; empty for rows imported before search existed (until the next refresh). */
    @ColumnInfo(defaultValue = "") val searchKey: String = "",
)

@Entity(
    tableName = "series",
    foreignKeys = [ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId", "groupName", "sortOrder"), Index("lastModified")],
)
data class SeriesEntity(
    /** `playlistId::rawId`. */
    @PrimaryKey val id: String,
    val playlistId: Long,
    val rawId: String,
    val name: String,
    val groupName: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val year: Int?,
    val rating: Double,
    val sortOrder: Int,
    /** [searchKeyOf] the name; empty for rows imported before search existed (until the next refresh). */
    @ColumnInfo(defaultValue = "") val searchKey: String = "",
    /** Xtream `last_modified` (epoch seconds), 0 when unknown: "recently added" on the Home screen. */
    @ColumnInfo(defaultValue = "0") val lastModified: Long = 0,
)

@Entity(
    tableName = "episode",
    foreignKeys = [ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("seriesId", "season", "episode"), Index("playlistId")],
)
data class EpisodeEntity(
    /** `playlistId::rawId`. */
    @PrimaryKey val id: String,
    val seriesId: String,
    val playlistId: Long,
    val season: Int,
    val episode: Int,
    val title: String,
    val streamUrl: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSecs: Int,
)

/** Resume point of a movie or episode. Kept across refreshes (ids are stable). */
@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** Titles per category, counted once at the end of an import. */
data class GroupCount(
    val name: String,
    val count: Int,
)

/** A movie with its resume point (null when never played), for the posters grid. */
data class MovieWithProgressRow(
    @Embedded val movie: MovieEntity,
    val positionMs: Long?,
    val durationMs: Long?,
)

data class VodCategoryRow(
    val playlistId: Long,
    val kind: String,
    val name: String,
    val count: Int,
)

@Dao
interface VodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategories(categories: List<VodCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMovies(movies: List<MovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSeries(series: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM vod_category WHERE playlistId = :playlistId AND kind = :kind")
    fun deleteCategories(
        playlistId: Long,
        kind: String,
    )

    @Query("SELECT groupName AS name, COUNT(*) AS count FROM movie WHERE playlistId = :playlistId GROUP BY groupName")
    fun movieCounts(playlistId: Long): List<GroupCount>

    @Query("SELECT groupName AS name, COUNT(*) AS count FROM series WHERE playlistId = :playlistId GROUP BY groupName")
    fun seriesCounts(playlistId: Long): List<GroupCount>

    @Query("DELETE FROM movie WHERE playlistId = :playlistId")
    fun deleteMovies(playlistId: Long)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    fun deleteSeries(playlistId: Long)

    @Query("DELETE FROM episode WHERE playlistId = :playlistId")
    fun deleteEpisodes(playlistId: Long)

    @Query("DELETE FROM episode WHERE seriesId = :seriesId")
    fun deleteEpisodesOf(seriesId: String)

    @Transaction
    fun replaceEpisodes(
        seriesId: String,
        episodes: List<EpisodeEntity>,
    ) {
        deleteEpisodesOf(seriesId)
        insertEpisodes(episodes)
    }

    @Query(
        """
        SELECT c.playlistId AS playlistId, c.kind AS kind, c.name AS name,
            c.itemCount AS count
        FROM vod_category c JOIN playlist p ON p.id = c.playlistId
        WHERE p.enabled = 1 AND c.kind = :kind
        ORDER BY c.playlistId, c.sortOrder
        """,
    )
    fun observeCategories(kind: String): Flow<List<VodCategoryRow>>

    @Query("SELECT * FROM movie WHERE playlistId = :playlistId AND groupName = :group ORDER BY sortOrder")
    fun observeMovies(
        playlistId: Long,
        group: String,
    ): Flow<List<MovieEntity>>

    /** One query for posters and progress bars (a separate IN (...) query was capped at 900 ids). */
    @Query(
        """
        SELECT m.*, wp.positionMs AS positionMs, wp.durationMs AS durationMs
        FROM movie m LEFT JOIN watch_progress wp ON wp.contentId = m.id
        WHERE m.playlistId = :playlistId AND m.groupName = :group
        ORDER BY m.sortOrder
        """,
    )
    fun observeMoviesWithProgress(
        playlistId: Long,
        group: String,
    ): Flow<List<MovieWithProgressRow>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND groupName = :group ORDER BY sortOrder")
    fun observeSeries(
        playlistId: Long,
        group: String,
    ): Flow<List<SeriesEntity>>

    /**
     * Movies of enabled playlists whose name contains the search: [key] is matched against the
     * stored search key, [raw] against the name for rows that do not have one yet.
     */
    @Query(
        """
        SELECT m.* FROM movie m JOIN playlist p ON p.id = m.playlistId
        WHERE p.enabled = 1 AND (m.searchKey LIKE :key ESCAPE '\' OR (m.searchKey = '' AND m.name LIKE :raw ESCAPE '\'))
        ORDER BY m.playlistId, m.sortOrder
        LIMIT :limit
        """,
    )
    suspend fun searchMovies(
        key: String,
        raw: String,
        limit: Int,
    ): List<MovieEntity>

    @Query(
        """
        SELECT s.* FROM series s JOIN playlist p ON p.id = s.playlistId
        WHERE p.enabled = 1 AND (s.searchKey LIKE :key ESCAPE '\' OR (s.searchKey = '' AND s.name LIKE :raw ESCAPE '\'))
        ORDER BY s.playlistId, s.sortOrder
        LIMIT :limit
        """,
    )
    suspend fun searchSeries(
        key: String,
        raw: String,
        limit: Int,
    ): List<SeriesEntity>

    @Query("SELECT * FROM movie WHERE id = :id")
    suspend fun movie(id: String): MovieEntity?

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun series(id: String): SeriesEntity?

    @Query("SELECT * FROM episode WHERE id = :id")
    suspend fun episode(id: String): EpisodeEntity?

    @Query("SELECT * FROM episode WHERE seriesId = :seriesId ORDER BY season, episode")
    fun observeEpisodes(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM watch_progress WHERE contentId IN (:ids)")
    fun observeProgress(ids: List<String>): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE contentId = :id")
    suspend fun progress(id: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgressEntity)
}
