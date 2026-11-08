package app.ontelevis.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.ontelevis.core.data.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Refreshes every enabled playlist. Per-playlist errors are stored on the playlist itself. */
@HiltWorker
class PlaylistRefreshWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val playlists: PlaylistRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            playlists.refreshAll()
            return Result.success()
        }
    }

/**
 * Downloads one playlist's movies, series and guide in the background, after its channels
 * (big panels list hundreds of thousands of titles; XMLTV files are large too).
 */
@HiltWorker
class EpgRefreshWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val playlists: PlaylistRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = inputData.getLong(KEY_PLAYLIST, -1)
            if (id >= 0) {
                playlists.refreshVod(id)
                playlists.refreshEpg(id)
            }
            return Result.success()
        }

        companion object {
            const val KEY_PLAYLIST = "playlist_id"
        }
    }

object PlaylistSync {
    private const val PERIODIC = "playlist-refresh-periodic"
    private const val NOW = "playlist-refresh-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Daily refresh. Safe to call at every start: an existing schedule is kept. */
    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(network)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Movies, series and guide of one playlist, right after it was added or refreshed. */
    fun refreshEpg(
        context: Context,
        playlistId: Long,
    ) {
        val request =
            OneTimeWorkRequestBuilder<EpgRefreshWorker>()
                .setConstraints(network)
                .setInputData(workDataOf(EpgRefreshWorker.KEY_PLAYLIST to playlistId))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("epg-$playlistId", ExistingWorkPolicy.REPLACE, request)
    }

    /** "Update content" from the menu. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PlaylistRefreshWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }
}
