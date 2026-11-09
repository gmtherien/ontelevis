package app.ontelevis.core.playerui

import android.content.res.Resources
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntTheme
import kotlinx.coroutines.delay
import java.util.Locale

/** Technical details of what plays, refreshed every second: for "why does this channel stutter?". */
@OptIn(UnstableApi::class)
@Composable
fun StatsOverlay(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lines by produceState(emptyList<String>(), player) {
        val meter = DefaultBandwidthMeter.getSingletonInstance(context)
        while (true) {
            value = (player as? ExoPlayer)?.let { statsOf(context.resources, it, meter.bitrateEstimate) }.orEmpty()
            delay(1_000)
        }
    }
    if (lines.isEmpty()) return
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach { Text(it, style = OntTheme.type.meta, color = Color.White) }
    }
}

@OptIn(UnstableApi::class)
private fun statsOf(
    res: Resources,
    player: ExoPlayer,
    networkBitrate: Long,
): List<String> {
    val video = player.videoFormat
    val audio = player.audioFormat
    val buffer = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
    val dropped = player.videoDecoderCounters?.droppedBufferCount ?: 0
    return listOfNotNull(
        video?.let { v ->
            val fps =
                v.frameRate
                    .takeIf { it > 0 }
                    ?.let { " %.0f fps".format(Locale.ROOT, it) }
                    .orEmpty()
            val hdr = if (isHdr(v.colorInfo?.colorTransfer)) " HDR" else ""
            res.getString(R.string.stats_video, "${v.width}×${v.height}$fps · ${codec(v)}$hdr")
        },
        audio?.let { a ->
            val rate =
                a.sampleRate
                    .takeIf { it > 0 }
                    ?.let { " ${it / 1000} kHz" }
                    .orEmpty()
            res.getString(R.string.stats_audio, "${codec(a)} · ${a.channelCount} ch$rate")
        },
        (video?.bitrate?.takeIf { it > 0 } ?: video?.averageBitrate?.takeIf { it > 0 })?.let {
            res.getString(R.string.stats_bitrate, mbps(it.toLong()))
        },
        networkBitrate.takeIf { it > 0 }?.let { res.getString(R.string.stats_network, mbps(it)) },
        res.getString(R.string.stats_buffer, "%.1f s".format(Locale.ROOT, buffer / 1000.0), dropped),
    )
}

@OptIn(UnstableApi::class)
private fun isHdr(transfer: Int?): Boolean = transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG

private fun codec(format: Format): String =
    (format.codecs ?: format.sampleMimeType?.substringAfter('/') ?: "?").substringBefore('.').uppercase(Locale.ROOT)

private fun mbps(bitsPerSecond: Long) = "%.1f Mb/s".format(Locale.ROOT, bitsPerSecond / 1_000_000.0)
