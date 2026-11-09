package app.ontelevis.core.parser.xtream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

/** One entry of `get_vod_streams`. */
@Serializable
data class XtreamVodStream(
    @Serializable(FlexibleInt::class) val num: Int = 0,
    @Serializable(FlexibleString::class) val name: String? = null,
    @SerialName("stream_id") @Serializable(FlexibleString::class) val streamId: String? = null,
    @SerialName("stream_icon") @Serializable(FlexibleString::class) val streamIcon: String? = null,
    @SerialName("rating_5based") @Serializable(FlexibleDouble::class) val rating5: Double = 0.0,
    @Serializable(FlexibleString::class) val added: String? = null,
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
    @SerialName("container_extension") @Serializable(FlexibleString::class) val extension: String? = null,
)

/** One entry of `get_series`. */
@Serializable
data class XtreamSeries(
    @Serializable(FlexibleInt::class) val num: Int = 0,
    @Serializable(FlexibleString::class) val name: String? = null,
    @SerialName("series_id") @Serializable(FlexibleString::class) val seriesId: String? = null,
    @Serializable(FlexibleString::class) val cover: String? = null,
    @Serializable(FlexibleString::class) val plot: String? = null,
    @Serializable(FlexibleString::class) val genre: String? = null,
    @SerialName("releaseDate") @Serializable(FlexibleString::class) val releaseDate: String? = null,
    @SerialName("rating_5based") @Serializable(FlexibleDouble::class) val rating5: Double = 0.0,
    @SerialName("backdrop_path") @Serializable(FirstString::class) val backdrop: String? = null,
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
    /** Epoch seconds of the last change (new episode): "recently added" series. */
    @SerialName("last_modified") @Serializable(FlexibleString::class) val lastModified: String? = null,
)

/** `get_series_info`: seasons and episodes of one series. */
@Serializable
data class XtreamSeriesInfo(
    val episodes: JsonElement? = null,
)

@Serializable
data class XtreamEpisode(
    @Serializable(FlexibleString::class) val id: String? = null,
    @SerialName("episode_num") @Serializable(FlexibleInt::class) val episodeNum: Int = 0,
    @Serializable(FlexibleString::class) val title: String? = null,
    @SerialName("container_extension") @Serializable(FlexibleString::class) val extension: String? = null,
    @Serializable(FlexibleInt::class) val season: Int = 0,
    val info: XtreamEpisodeInfo? = null,
)

@Serializable
data class XtreamEpisodeInfo(
    @Serializable(FlexibleString::class) val plot: String? = null,
    @SerialName("movie_image") @Serializable(FlexibleString::class) val image: String? = null,
    @SerialName("duration_secs") @Serializable(FlexibleInt::class) val durationSecs: Int = 0,
)

/** `get_vod_info`: details shown on a movie page. */
@Serializable
data class XtreamVodInfoResponse(
    val info: XtreamVodInfo? = null,
)

@Serializable
data class XtreamVodInfo(
    @Serializable(FlexibleString::class) val plot: String? = null,
    @Serializable(FlexibleString::class) val cast: String? = null,
    @Serializable(FlexibleString::class) val director: String? = null,
    @Serializable(FlexibleString::class) val genre: String? = null,
    @SerialName("releasedate") @Serializable(FlexibleString::class) val releaseDate: String? = null,
    @SerialName("duration_secs") @Serializable(FlexibleInt::class) val durationSecs: Int = 0,
    @Serializable(FirstString::class) val backdrop: String? = null,
    @SerialName("backdrop_path") @Serializable(FirstString::class) val backdropPath: String? = null,
)

/** Decoding of the VOD replies; same leniency as [XtreamJson]. */
object XtreamVodJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeVodStreams(input: InputStream): Sequence<XtreamVodStream> =
        json.decodeToSequence(input, XtreamVodStream.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeSeries(input: InputStream): Sequence<XtreamSeries> =
        json.decodeToSequence(input, XtreamSeries.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeVodInfo(input: InputStream): XtreamVodInfo? =
        json.decodeFromStream(XtreamVodInfoResponse.serializer(), input).info

    /** Episodes of all seasons. Panels send `{"1": [...], "2": [...]}` or a plain array. */
    @OptIn(ExperimentalSerializationApi::class)
    fun decodeEpisodes(input: InputStream): List<XtreamEpisode> {
        val episodes = json.decodeFromStream(XtreamSeriesInfo.serializer(), input).episodes ?: return emptyList()
        val lists =
            when (episodes) {
                is JsonObject -> episodes.entries.map { (season, list) -> season.toIntOrNull() to list }
                is JsonArray -> listOf(null to episodes)
                else -> emptyList()
            }
        return lists.flatMap { (season, list) ->
            (list as? JsonArray).orEmpty().mapNotNull { element ->
                runCatching { json.decodeFromJsonElement(XtreamEpisode.serializer(), element) }
                    .getOrNull()
                    ?.let { if (it.season == 0 && season != null) it.copy(season = season) else it }
            }
        }
    }
}

private fun Decoder.element(): JsonElement? = (this as? JsonDecoder)?.decodeJsonElement()

/** Accepts `4.5`, `"4.5"`, `""`, `null`: anything not a number becomes 0. */
internal object FlexibleDouble : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double =
        (decoder.element() as? JsonPrimitive)
            ?.content
            ?.trim()
            ?.toDoubleOrNull() ?: 0.0

    override fun serialize(
        encoder: Encoder,
        value: Double,
    ) = encoder.encodeDouble(value)
}

/** A string, or the first string of an array (`backdrop_path` comes both ways); blank becomes null. */
internal object FirstString : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("FirstString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = decoder.element()
        val primitive = (element as? JsonArray)?.firstOrNull() ?: element
        return (primitive as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.trim()
            ?.ifEmpty { null }
    }

    override fun serialize(
        encoder: Encoder,
        value: String?,
    ) = encoder.encodeString(value.orEmpty())
}
