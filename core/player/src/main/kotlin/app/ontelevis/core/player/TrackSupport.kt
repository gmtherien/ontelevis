package app.ontelevis.core.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import java.util.Locale

/** An audio or subtitle track the user can pick. */
data class TrackOption(
    val type: Kind,
    /** What the menu shows: "Français · AC3 5.1", "English (CC)". */
    val label: String,
    /** ISO 639 language, null when the stream does not say. */
    val language: String?,
    /** The stream's own name for the track, used to tell apart two tracks in the same language. */
    val rawLabel: String?,
    val selected: Boolean,
    internal val groupIndex: Int,
    internal val trackIndex: Int,
) {
    enum class Kind { AUDIO, SUBTITLE }
}

/** Playable audio or subtitle tracks of what plays now, in the stream's order. */
fun Player.trackOptions(
    kind: TrackOption.Kind,
    locale: Locale = Locale.getDefault(),
    /** Name of a track without language or label, with `%d` for its number ("Track %d"). */
    unnamed: String = "#%d",
): List<TrackOption> {
    var number = 0
    val type = kind.c
    val textOff = kind == TrackOption.Kind.SUBTITLE && trackSelectionParameters.disabledTrackTypes.contains(type)
    return currentTracks.groups.withIndex().flatMap { (groupIndex, group) ->
        if (group.type != type) return@flatMap emptyList()
        (0 until group.length).mapNotNull { i ->
            if (!group.isTrackSupported(i)) return@mapNotNull null
            val format = group.getTrackFormat(i)
            val language = format.language?.takeUnless { it == C.LANGUAGE_UNDETERMINED || it.isBlank() }
            number++
            TrackOption(
                type = kind,
                label =
                    trackLabel(
                        label = format.label,
                        language = language,
                        mimeType = format.sampleMimeType,
                        channels = format.channelCount,
                        locale = locale,
                        unnamed = unnamed.format(number),
                    ),
                language = language,
                rawLabel = format.label,
                selected = !textOff && group.isTrackSelected(i),
                groupIndex = groupIndex,
                trackIndex = i,
            )
        }
    }
}

/** Plays [option] instead of the track chosen automatically. */
fun Player.selectTrack(option: TrackOption) {
    val group = currentTracks.groups.getOrNull(option.groupIndex) ?: return
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(option.type.c, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
}

fun Player.hideSubtitles() {
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
}

/**
 * Starting choices for a new stream: the preferred languages (null audio: the stream's default;
 * null subtitles: none), with no track forced yet.
 */
fun Player.applyTrackDefaults(
    audioLanguage: String?,
    subtitleLanguage: String?,
) {
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredAudioLanguage(audioLanguage)
            .setPreferredTextLanguage(subtitleLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleLanguage == null)
            .build()
}

private val TrackOption.Kind.c: Int
    get() = if (this == TrackOption.Kind.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT

/** "Français · AC3 5.1": the language (or the stream's label), then the codec and channels. */
fun trackLabel(
    label: String?,
    language: String?,
    mimeType: String?,
    channels: Int,
    locale: Locale,
    unnamed: String,
): String {
    val languageName =
        language?.let { tag ->
            Locale
                .forLanguageTag(tag)
                .getDisplayLanguage(locale)
                .takeIf { it.isNotBlank() && it != tag }
                ?.replaceFirstChar { it.titlecase(locale) } ?: tag.uppercase(locale)
        }
    val name =
        when {
            label.isNullOrBlank() -> languageName ?: unnamed
            languageName == null || label.contains(languageName, ignoreCase = true) -> label
            else -> "$languageName ($label)"
        }
    val details = listOfNotNull(codecName(mimeType), channelsName(channels)).joinToString(" ")
    return if (details.isEmpty()) name else "$name · $details"
}

private fun codecName(mimeType: String?): String? =
    when (mimeType) {
        "audio/ac3" -> "AC3"
        "audio/eac3", "audio/eac3-joc" -> "E-AC3"
        "audio/ac4" -> "AC4"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/mpeg-L2" -> "MP2"
        "audio/opus" -> "Opus"
        "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
        "audio/true-hd" -> "TrueHD"
        "audio/flac" -> "FLAC"
        "application/cea-608", "application/cea-708" -> "CC"
        else -> null
    }

private fun channelsName(channels: Int): String? =
    when (channels) {
        1 -> "mono"
        2 -> "stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }
