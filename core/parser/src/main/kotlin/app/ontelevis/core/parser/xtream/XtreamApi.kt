package app.ontelevis.core.parser.xtream

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

@Serializable
data class XtreamAccountResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfo? = null,
)

@Serializable
data class XtreamServerInfo(
    /** Time zone of the panel (e.g. "UTC", "Europe/Paris"): catch-up start times are written in it. */
    @Serializable(FlexibleString::class) val timezone: String? = null,
)

@Serializable
data class XtreamUserInfo(
    @Serializable(FlexibleInt::class) val auth: Int = 0,
    @Serializable(FlexibleString::class) val status: String? = null,
    @SerialName("exp_date") @Serializable(FlexibleString::class) val expDate: String? = null,
    @SerialName("max_connections") @Serializable(FlexibleInt::class) val maxConnections: Int = 0,
    @SerialName("active_cons") @Serializable(FlexibleInt::class) val activeConnections: Int = 0,
    @SerialName("is_trial") @Serializable(FlexibleInt::class) val isTrial: Int = 0,
) {
    /** Expiry as epoch milliseconds, or null when the account never expires (`exp_date` empty, 0 or null). */
    val expiresAtMillis: Long?
        get() =
            expDate
                ?.toDoubleOrNull()
                ?.toLong()
                ?.takeIf { it > 0 }
                ?.let { it * 1000 }
}

@Serializable
data class XtreamCategory(
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
    @SerialName("category_name") @Serializable(FlexibleString::class) val categoryName: String? = null,
)

@Serializable
data class XtreamLiveStream(
    @Serializable(FlexibleInt::class) val num: Int = 0,
    @Serializable(FlexibleString::class) val name: String? = null,
    @SerialName("stream_id") @Serializable(FlexibleString::class) val streamId: String? = null,
    @SerialName("stream_icon") @Serializable(FlexibleString::class) val streamIcon: String? = null,
    @SerialName("epg_channel_id") @Serializable(FlexibleString::class) val epgChannelId: String? = null,
    @SerialName("category_id") @Serializable(FlexibleString::class) val categoryId: String? = null,
    @SerialName("tv_archive") @Serializable(FlexibleInt::class) val tvArchive: Int = 0,
    @SerialName("tv_archive_duration") @Serializable(FlexibleInt::class) val tvArchiveDuration: Int = 0,
)

/**
 * Decoding of Xtream Codes `player_api.php` replies. Providers are inconsistent (numbers sent as
 * strings, empty strings, nulls), so every field is decoded leniently and unknown keys are ignored.
 */
object XtreamJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

    fun decodeAccount(input: InputStream): XtreamAccountResponse = decode(input, XtreamAccountResponse.serializer())

    fun decodeCategories(input: InputStream): List<XtreamCategory> =
        decode(input, ListSerializer(XtreamCategory.serializer()))

    /** Streams the array element by element: lists of 50 000+ channels never sit in memory at once. */
    @OptIn(ExperimentalSerializationApi::class)
    fun decodeLiveStreams(input: InputStream): Sequence<XtreamLiveStream> =
        json.decodeToSequence(input, XtreamLiveStream.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> decode(
        input: InputStream,
        deserializer: DeserializationStrategy<T>,
    ): T = json.decodeFromStream(deserializer, input)
}

private fun Decoder.primitiveContent(): String? {
    val element = (this as? JsonDecoder)?.decodeJsonElement() ?: return decodeString()
    return (element as? JsonPrimitive)?.takeUnless { it.content == "null" && !it.isString }?.content
}

/** Accepts `5`, `"5"`, `""`, `null`: anything not a number becomes 0. */
internal object FlexibleInt : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int =
        decoder
            .primitiveContent()
            ?.trim()
            ?.toDoubleOrNull()
            ?.toInt() ?: 0

    override fun serialize(
        encoder: Encoder,
        value: Int,
    ) = encoder.encodeInt(value)
}

/** Accepts strings and numbers; blank values become null. */
internal object FlexibleString : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? = decoder.primitiveContent()?.trim()?.ifEmpty { null }

    override fun serialize(
        encoder: Encoder,
        value: String?,
    ) = encoder.encodeString(value.orEmpty())
}
