package app.ontelevis.feature.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.ChannelLogoTile
import app.ontelevis.core.designsystem.component.LiveBadge
import app.ontelevis.core.designsystem.component.OntTag
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.parseChannelName
import app.ontelevis.core.player.LiveState
import java.time.Instant
import java.time.ZoneId

/**
 * §4 "Lecteur (aperçu)" + "Panneau d'info": 338 × 190 dp video, then the channel details.
 * OK on the preview opens full screen (no separate button: the second OK does it).
 */
@Composable
internal fun NowPlaying(
    live: LiveState,
    player: Player?,
    programmes: List<Programme>,
    now: Instant,
    previewFocus: FocusRequester,
    onFullscreen: () -> Unit,
    /** Left from the preview: back to the selected category (not the one beside it on screen). */
    onLeft: () -> Unit,
    /** Programme focused in the guide: the info panel describes it instead of the channel in the player. */
    guideFocus: GuideFocus? = null,
) {
    val current = onNow(programmes, now)
    val next = programmes.firstOrNull { it.startMillis >= (current?.stopMillis ?: now.toEpochMilli()) }
    val times = rememberTimeFormatter()
    val zone = ZoneId.systemDefault()
    Row(Modifier.fillMaxWidth().height(OntDimens.NowPlayingHeight)) {
        Preview(
            live,
            player,
            current,
            previewFocus,
            onFullscreen,
            Modifier
                .width(OntDimens.PlayerWidth)
                .fillMaxHeight()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onLeft()
                        true
                    } else {
                        false
                    }
                },
        )
        Spacer(Modifier.width(24.dp))
        val infoModifier = Modifier.weight(1f).fillMaxHeight()
        if (guideFocus != null) {
            val programme = guideFocus.programme
            InfoPanel(
                channel = guideFocus.channel,
                videoHeight = if (guideFocus.channel.id == live.channel?.id) live.videoHeight else 0,
                programme = programme,
                bottomLine =
                    programme?.let {
                        val start = Instant.ofEpochMilli(it.startMillis).atZone(zone)
                        val end = Instant.ofEpochMilli(it.stopMillis).atZone(zone)
                        "${start.format(times)} – ${end.format(times)}"
                    },
                modifier = infoModifier,
            )
        } else {
            live.channel?.let { channel ->
                InfoPanel(
                    channel = channel,
                    videoHeight = live.videoHeight,
                    programme = current,
                    bottomLine =
                        next?.let {
                            val at = Instant.ofEpochMilli(it.startMillis).atZone(zone).format(times)
                            "${stringResource(R.string.live_next)} · $at  ${it.title}"
                        },
                    modifier = infoModifier,
                )
            }
        }
    }
}

@Composable
private fun Preview(
    live: LiveState,
    player: Player?,
    current: Programme?,
    focus: FocusRequester,
    onFullscreen: () -> Unit,
    modifier: Modifier,
) {
    val c = OntTheme.colors
    val shape = RoundedCornerShape(OntDimens.RadiusCard)
    val channel = live.channel
    Surface(
        onClick = onFullscreen,
        modifier = modifier.focusRequester(focus),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = c.selectedRow, focusedContainerColor = c.selectedRow),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(OntDimens.Hairline, c.line), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, c.focusSurface), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Box(Modifier.fillMaxSize()) {
            LiveVideo(player, Modifier.fillMaxSize())
            if (channel == null) {
                Text(
                    stringResource(R.string.live_pick_channel),
                    style = OntTheme.type.meta,
                    color = c.textTertiary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LiveStatusOverlay(live)
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveBadge(overVideo = true)
                    qualityLabel(channel, live.videoHeight)?.let { OntTag(it) }
                }
                val parsed = remember(channel.name) { parseChannelName(channel.name) }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                        .padding(start = 12.dp, end = 12.dp, top = 36.dp, bottom = 10.dp),
                ) {
                    Text(
                        parsed.title,
                        style = OntTheme.type.itemStrong.copy(fontSize = OntTheme.type.item.fontSize),
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            parsed.group ?: channel.group.ifEmpty { null },
                            current?.title ?: stringResource(R.string.live_continuous),
                        ).joinToString(" · "),
                        style = OntTheme.type.meta,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(
    channel: Channel,
    /** Decoded height of the channel in the player, 0 for any other channel. */
    videoHeight: Int,
    programme: Programme?,
    /** Under the description: what is next (channel in the player) or the programme's times (guide). */
    bottomLine: String?,
    modifier: Modifier,
) {
    val c = OntTheme.colors
    val current = programme
    val parsed = remember(channel.name) { parseChannelName(channel.name) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogoTile(parsed.initials, size = OntDimens.InfoLogoTile, logoUrl = channel.logoUrl)
            Column {
                val group = parsed.group ?: channel.group.ifEmpty { null }
                if (group != null) Text(group, style = OntTheme.type.meta, color = c.textMuted, maxLines = 1)
                Text(
                    parsed.title,
                    style = OntTheme.type.display,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            qualityLabel(channel, videoHeight)?.let { OntTag(it) }
            if (current == null) OntTag("24/7")
            channel.group.ifEmpty { null }?.let { OntTag(it) }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.textPrimary)) {
                    append(current?.title ?: stringResource(R.string.live_continuous_title))
                }
                append(" ")
                append(
                    if (current !=
                        null
                    ) {
                        current.description.orEmpty()
                    } else {
                        stringResource(R.string.live_continuous_body)
                    },
                )
            },
            style = OntTheme.type.body,
            color = c.textMuted,
            maxLines = if (bottomLine != null) 3 else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (bottomLine != null) {
            Text(
                bottomLine,
                style = OntTheme.type.meta,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A programme focused in the guide (null programme: the channel has no guide). */
internal class GuideFocus(
    val channel: Channel,
    val programme: Programme?,
)
