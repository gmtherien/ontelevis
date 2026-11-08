package app.ontelevis.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntTheme

/** One choice of an [OntMenu]. */
class OntMenuOption(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Small menu opened by holding OK on an item: a title (what it acts on) and a few choices.
 * The first choice takes the focus; Back closes it without doing anything.
 */
@Composable
fun OntMenu(
    title: String,
    options: List<OntMenuOption>,
    onDismiss: () -> Unit,
) {
    val c = OntTheme.colors
    val first = remember { FocusRequester() }
    // The menu opens while OK is still held: the key repeats and the release that follow belong to
    // that hold, not to the first choice. OK only counts after a fresh press inside the menu.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { event ->
                    val ok = event.key in OK_KEYS
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
                    ok && !armed
                }.width(320.dp)
                .background(c.surface1, RoundedCornerShape(OntDimens.RadiusCard))
                .border(OntDimens.Hairline, c.line, RoundedCornerShape(OntDimens.RadiusCard))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = OntTheme.type.itemStrong,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { index, option ->
                OntListItem(
                    text = option.label,
                    selected = false,
                    onClick = {
                        onDismiss()
                        option.onClick()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
