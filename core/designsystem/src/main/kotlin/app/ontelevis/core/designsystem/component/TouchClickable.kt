package app.ontelevis.core.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose for TV's `Surface` and `Button` only react to the remote (OK / Enter on the focused item):
 * a tap on a phone does nothing. On touch screens this adds tap (and long press) handling. Sharing
 * the component's [interactionSource] makes the tap show its pressed colours. No effect on TV.
 * Put it first in the modifier chain given to the component.
 */
@Composable
fun Modifier.touchClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier =
    if (LocalIsTv.current) {
        this
    } else {
        combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
