package app.ontelevis.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.R
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Network image with a quiet placeholder ([fallback], e.g. the title) while it loads or when it
 * fails. Used for posters, backdrops and episode stills.
 */
@Composable
fun OntImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: String? = null,
) {
    val painter = rememberAsyncImagePainter(url)
    val loaded = url != null && painter.state.collectAsState().value is AsyncImagePainter.State.Success
    Box(modifier, contentAlignment = Alignment.Center) {
        if (url != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!loaded && fallback != null) {
            Text(
                fallback,
                style = OntTheme.type.meta,
                color = OntTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/**
 * Poster card of a movie or series: 2:3 image (focus = light border, slight zoom), then title and
 * a meta line under it. [progress] (0..1) draws a resume bar at the bottom of the poster.
 */
@Composable
fun OntPosterCard(
    imageUrl: String?,
    title: String,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    /** Hold OK (remote) or long press (touch): usually opens a menu. */
    onLongClick: (() -> Unit)? = null,
    /** Shows an amber star in the corner. */
    favorite: Boolean = false,
) {
    val c = OntTheme.colors
    val shape = RoundedCornerShape(OntDimens.RadiusButton)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.touchClickable(onClick, onLongClick).fillMaxWidth().aspectRatio(2f / 3f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = c.surface2, focusedContainerColor = c.surface2),
            border =
                ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(OntDimens.Hairline, c.tileBorder), shape = shape),
                    focusedBorder = Border(BorderStroke(2.dp, c.focusSurface), shape = shape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_LIST),
        ) {
            Box(Modifier.fillMaxSize()) {
                OntImage(imageUrl, Modifier.fillMaxSize(), fallback = title)
                if (progress != null && progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomStart))
                if (favorite) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(c.background.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ont_ic_star),
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
        Text(
            title,
            style = OntTheme.type.itemStrong,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!meta.isNullOrEmpty()) {
            Text(meta, style = OntTheme.type.meta, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Thin amber resume bar across the bottom of an image. */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    BoxWithConstraints(
        modifier.fillMaxWidth().height(OntDimens.ProgressBar).background(c.background.copy(alpha = 0.6f)),
    ) {
        Box(Modifier.width(maxWidth * progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
    }
}

/** Secondary action next to [OntPrimaryButton]: dark at rest, light when focused. */
@Composable
fun OntSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = OntTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.touchClickable(onClick).then(modifier).height(OntDimens.ButtonHeight),
        shape = ButtonDefaults.shape(RoundedCornerShape(OntDimens.RadiusButton)),
        colors =
            ButtonDefaults.colors(
                containerColor = c.surface1,
                contentColor = c.textSecondary,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
                pressedContainerColor = c.focusSurface,
                pressedContentColor = c.onFocus,
            ),
        border =
            ButtonDefaults.border(
                border = Border(BorderStroke(OntDimens.Hairline, c.buttonBorder)),
                focusedBorder = Border.None,
            ),
        scale = ButtonDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_BUTTON),
        contentPadding = PaddingValues(horizontal = 18.dp),
        content = content,
    )
}
