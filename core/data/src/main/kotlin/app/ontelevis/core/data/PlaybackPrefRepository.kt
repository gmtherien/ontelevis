package app.ontelevis.core.data

import app.ontelevis.core.data.db.PlaybackPrefDao
import app.ontelevis.core.data.db.PlaybackPrefEntity
import app.ontelevis.core.model.PictureFormat
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks and picture format chosen for one channel, series or movie. */
data class PlaybackPref(
    val audioLanguage: String? = null,
    val audioLabel: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleLabel: String? = null,
    val subtitlesOff: Boolean = false,
    val format: PictureFormat? = null,
)

/** Remembers [PlaybackPref]s by key: a channel id, a series id (all its episodes) or a movie id. */
@Singleton
class PlaybackPrefRepository
    @Inject
    constructor(
        private val dao: PlaybackPrefDao,
        private val vod: VodRepository,
    ) {
        suspend fun get(key: String): PlaybackPref? = dao.get(key)?.toModel()

        suspend fun update(
            key: String,
            change: (PlaybackPref) -> PlaybackPref,
        ) {
            val next = change(get(key) ?: PlaybackPref())
            dao.put(
                PlaybackPrefEntity(
                    key = key,
                    audioLang = next.audioLanguage,
                    audioLabel = next.audioLabel,
                    textLang = next.subtitleLanguage,
                    textLabel = next.subtitleLabel,
                    textOff = next.subtitlesOff,
                    format = next.format?.name,
                ),
            )
        }

        /** The key choices are kept under for a movie or episode id: episodes share their series'. */
        suspend fun keyForContent(contentId: String): String =
            if (EPISODE_MARK in contentId) vod.episode(contentId)?.seriesId ?: contentId else contentId

        private fun PlaybackPrefEntity.toModel() =
            PlaybackPref(
                audioLanguage = audioLang,
                audioLabel = audioLabel,
                subtitleLanguage = textLang,
                subtitleLabel = textLabel,
                subtitlesOff = textOff,
                format = PictureFormat.entries.firstOrNull { it.name == format },
            )

        private companion object {
            const val EPISODE_MARK = "::e:"
        }
    }
