package app.ontelevis.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.R
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntTheme
import kotlinx.coroutines.delay

/** Number of digits of the parental PIN. */
const val PIN_LENGTH = 4

/**
 * Asks for the parental PIN. [check] says whether it is right; a wrong PIN shakes nothing, it
 * just says so and empties the dots.
 */
@Composable
fun OntVerifyPinDialog(
    title: String,
    check: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    val wrong = stringResource(R.string.ont_pin_wrong)
    PinPad(
        title = title,
        subtitle = stringResource(R.string.ont_pin_enter),
        error = error,
        onComplete = { pin ->
            if (check(pin)) {
                onSuccess()
                true
            } else {
                error = wrong
                false
            }
        },
        onDismiss = onDismiss,
    )
}

/** Creates a PIN: typed twice; [onCreated] gets it when both match. */
@Composable
fun OntCreatePinDialog(
    title: String,
    onCreated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.ont_pin_mismatch)
    PinPad(
        title = title,
        subtitle = stringResource(if (first == null) R.string.ont_pin_new else R.string.ont_pin_confirm),
        error = error,
        // A new key per step resets the dots.
        step = if (first == null) 0 else 1,
        onComplete = { pin ->
            val typed = first
            when {
                typed == null -> {
                    first = pin
                    error = null
                    true
                }

                typed == pin -> {
                    onCreated(pin)
                    true
                }

                else -> {
                    first = null
                    error = mismatch
                    false
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/**
 * Four dots and a 3 x 4 keypad, usable with the D-pad; the remote's number keys type too
 * (and Delete erases). [onComplete] gets the 4 digits; it returns false to empty the dots.
 */
@Composable
private fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    onComplete: (String) -> Boolean,
    onDismiss: () -> Unit,
    step: Int = 0,
) {
    val c = OntTheme.colors
    var digits by remember(step) { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }

    fun type(d: Char) {
        if (digits.length >= PIN_LENGTH) return
        digits += d
    }

    LaunchedEffect(digits) {
        if (digits.length == PIN_LENGTH) {
            // Let the last dot show before acting on it.
            delay(150)
            val pin = digits
            digits = ""
            onComplete(pin)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = DIGIT_KEYS[event.key]
                    when {
                        digit != null -> {
                            type(digit)
                            true
                        }

                        event.key == Key.Backspace || event.key == Key.Delete -> {
                            digits = digits.dropLast(1)
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.width(340.dp)
                .background(c.surface1, RoundedCornerShape(OntDimens.RadiusCard))
                .border(OntDimens.Hairline, c.line, RoundedCornerShape(OntDimens.RadiusCard))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = OntTheme.type.itemStrong, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(subtitle, style = OntTheme.type.meta, color = c.textMuted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                repeat(PIN_LENGTH) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (i < digits.length) c.accent else c.surface3, CircleShape),
                    )
                }
            }
            Text(
                error.orEmpty(),
                style = OntTheme.type.meta,
                color = c.live,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(18.dp),
            )
            Spacer(Modifier.height(2.dp))
            KEYPAD.forEachIndexed { row, keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    keys.forEachIndexed { col, key ->
                        PadKey(
                            label = key,
                            onClick = {
                                when (key) {
                                    ERASE -> digits = digits.dropLast(1)
                                    CLOSE -> onDismiss()
                                    else -> type(key.first())
                                }
                            },
                            modifier = if (row == 0 && col == 0) Modifier.focusRequester(firstKey) else Modifier,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
}

@Composable
private fun PadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 72.dp, height = 48.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OntDimens.RadiusButton)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = c.surface2,
                contentColor = c.textPrimary,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        // Fills the key so the digit / icon sits in its middle.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when (label) {
                    ERASE -> "⌫"
                    CLOSE -> "✕"
                    else -> label
                },
                style = OntTheme.type.title,
            )
        }
    }
}

private const val ERASE = "erase"
private const val CLOSE = "close"

private val KEYPAD =
    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(CLOSE, "0", ERASE),
    )

private val DIGIT_KEYS: Map<Key, Char> =
    mapOf(
        Key.Zero to '0',
        Key.One to '1',
        Key.Two to '2',
        Key.Three to '3',
        Key.Four to '4',
        Key.Five to '5',
        Key.Six to '6',
        Key.Seven to '7',
        Key.Eight to '8',
        Key.Nine to '9',
        Key.NumPad0 to '0',
        Key.NumPad1 to '1',
        Key.NumPad2 to '2',
        Key.NumPad3 to '3',
        Key.NumPad4 to '4',
        Key.NumPad5 to '5',
        Key.NumPad6 to '6',
        Key.NumPad7 to '7',
        Key.NumPad8 to '8',
        Key.NumPad9 to '9',
    )
