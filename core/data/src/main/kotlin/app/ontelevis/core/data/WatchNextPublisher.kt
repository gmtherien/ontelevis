package app.ontelevis.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import app.ontelevis.core.model.ContinueItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google TV / Android TV home: the "Continue watching" row shows the movies and episodes started in
 * Ontelevis (the same list as the app's Home). Kept in step with the resume points; a title the user
 * removed from that row comes back only when watched again. TV devices with Android 8+ only.
 */
@Singleton
class WatchNextPublisher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val vod: VodRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var started = false

        @OptIn(FlowPreview::class)
        fun start() {
            if (started || !supported()) return
            started = true
            scope.launch {
                vod
                    .observeContinueWatching(LIMIT)
                    // Resume points move every 10 s: the row follows by the minute.
                    .map { list -> list.map { it to it.positionMs / 60_000 } }
                    .distinctUntilChanged()
                    .debounce(DEBOUNCE_MS)
                    .collect { list -> runCatching { sync(list.map { it.first }) }.onFailure { Log.w(TAG, it) } }
            }
        }

        private fun supported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        @SuppressLint("RestrictedApi")
        private fun sync(items: List<ContinueItem>) {
            val helper = PreviewChannelHelper(context)
            // Our rows already there: content id -> (row id, engagement time, still shown).
            val existing = HashMap<String, Triple<Long, Long, Boolean>>()
            context.contentResolver
                .query(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val program = WatchNextProgram.fromCursor(cursor)
                        val id = program.internalProviderId ?: continue
                        existing[id] = Triple(program.id, program.lastEngagementTimeUtcMillis, program.isBrowsable)
                    }
                }
            val wanted = items.associateBy { it.contentId }
            existing.forEach { (contentId, row) ->
                if (contentId !in wanted) delete(row.first)
            }
            items.forEach { item ->
                val row = existing[item.contentId]
                val program = item.toProgram()
                when {
                    row == null -> {
                        helper.publishWatchNextProgram(program)
                    }

                    // Removed by the user from the row: back only after watching it again.
                    !row.third -> {
                        if (item.updatedAtMinute() > row.second / 60_000) {
                            delete(row.first)
                            helper.publishWatchNextProgram(program)
                        }
                    }

                    else -> {
                        helper.updateWatchNextProgram(program, row.first)
                    }
                }
            }
        }

        private fun delete(rowId: Long) {
            context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(rowId), null, null)
        }

        @SuppressLint("RestrictedApi")
        private fun ContinueItem.toProgram(): WatchNextProgram {
            val builder =
                WatchNextProgram
                    .Builder()
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setType(
                        if (seriesId != null) {
                            TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                        } else {
                            TvContractCompat.PreviewPrograms.TYPE_MOVIE
                        },
                    ).setTitle(title)
                    .setLastEngagementTimeUtcMillis(updatedAt)
                    .setLastPlaybackPositionMillis(positionMs.toInt())
                    .setDurationMillis(durationMs.toInt())
                    .setInternalProviderId(contentId)
                    .setIntentUri(LaunchRequests.playUri(contentId))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            posterUrl?.let { builder.setPosterArtUri(Uri.parse(it)) }
            if (seriesId != null) {
                builder.setSeasonNumber(season).setEpisodeNumber(episode)
                episodeTitle?.let(builder::setEpisodeTitle)
            }
            return builder.build()
        }

        private fun ContinueItem.updatedAtMinute() = updatedAt / 60_000

        private companion object {
            const val TAG = "OntelevisWatchNext"
            const val LIMIT = 15
            const val DEBOUNCE_MS = 3_000L
        }
    }
