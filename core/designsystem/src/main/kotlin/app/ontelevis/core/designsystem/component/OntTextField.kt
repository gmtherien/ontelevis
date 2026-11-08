package app.ontelevis.core.designsystem.component

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntTheme

/** True on Android TV / Fire TV. Provided once at the root. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Text field. On TV it is "gated": a read-only tile until OK, so the D-pad can pass over it
 * without raising the keyboard. Editing ends on the IME action, on Back, or on Up / Down.
 * After editing ends, OK is ignored for a moment: Fire OS turns Back into a synthetic Enter,
 * which must not reopen the keyboard.
 */
@Composable
fun OntTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    error: String? = null,
    /** TV: Left on the (not editing) field; lets a screen send it to a precise place. */
    onLeft: (() -> Unit)? = null,
) {
    if (LocalIsTv.current) {
        TvTextField(
            value,
            onValueChange,
            label,
            modifier,
            placeholder,
            keyboardType,
            password,
            imeAction,
            error,
            onLeft,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = if (label.isEmpty()) null else ({ androidx.compose.material3.Text(label) }),
            placeholder = { androidx.compose.material3.Text(placeholder) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { androidx.compose.material3.Text(it) } },
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean,
    imeAction: ImeAction,
    error: String?,
    onLeft: (() -> Unit)?,
) {
    val c = OntTheme.colors
    val shape = RoundedCornerShape(OntDimens.RadiusButton)
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    var editEndedAt by remember { mutableLongStateOf(0L) }
    val tileFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun stopEditing() {
        if (!editing) return
        keyboard?.hide()
        editEndedAt = SystemClock.uptimeMillis()
        editing = false
    }

    LaunchedEffect(editing) {
        if (editing) {
            wasEditing = true
            fieldFocus.requestFocus()
            keyboard?.show()
        } else if (wasEditing) {
            tileFocus.requestFocus()
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) {
            Text(label, style = OntTheme.type.meta, color = if (error != null) c.live else c.textMuted)
        }
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = OntTheme.type.item.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { stopEditing() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(OntDimens.ButtonHeight + 4.dp)
                        .focusRequester(fieldFocus)
                        .onFocusChanged { if (!it.isFocused && editing && wasEditing) stopEditing() }
                        .onPreviewKeyEvent { event ->
                            val leaves =
                                event.key == Key.Back || event.key == Key.DirectionUp || event.key == Key.DirectionDown
                            if (leaves && event.type == KeyEventType.KeyUp) stopEditing()
                            leaves
                        }.background(c.surface2, shape)
                        .border(2.dp, c.accent, shape)
                        .padding(horizontal = 14.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
                },
            )
        } else {
            val shown =
                when {
                    value.isEmpty() -> placeholder
                    password -> "•".repeat(value.length.coerceAtMost(12))
                    else -> value
                }
            Surface(
                onClick = {
                    if (SystemClock.uptimeMillis() - editEndedAt > REOPEN_GUARD_MS) editing = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(OntDimens.ButtonHeight + 4.dp)
                        .focusRequester(tileFocus)
                        .onPreviewKeyEvent { event ->
                            if (onLeft != null && event.key == Key.DirectionLeft) {
                                if (event.type == KeyEventType.KeyDown) onLeft()
                                true
                            } else {
                                false
                            }
                        },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = c.surface1,
                        contentColor = if (value.isEmpty()) c.textTertiary else c.textPrimary,
                        focusedContainerColor = c.focusSurface,
                        focusedContentColor = c.onFocus,
                    ),
                border =
                    ClickableSurfaceDefaults.border(
                        border =
                            Border(
                                androidx.compose.foundation.BorderStroke(
                                    OntDimens.Hairline,
                                    if (error !=
                                        null
                                    ) {
                                        c.live
                                    } else {
                                        c.buttonBorder
                                    },
                                ),
                            ),
                        focusedBorder = Border.None,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    Text(shown, style = OntTheme.type.item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (error != null) Text(error, style = OntTheme.type.meta, color = c.live)
    }
}

private const val REOPEN_GUARD_MS = 600L
