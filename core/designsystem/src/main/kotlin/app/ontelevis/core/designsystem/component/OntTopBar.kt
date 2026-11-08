package app.ontelevis.core.designsystem.component

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.R
import app.ontelevis.core.designsystem.theme.OntTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Breadcrumb ("Télé › Relaxation") on the left, clock and date on the right. */
@Composable
fun OntTopBar(
    breadcrumb: List<String>,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            breadcrumb.forEachIndexed { index, part ->
                val last = index == breadcrumb.lastIndex
                if (index > 0) {
                    Icon(
                        painterResource(R.drawable.ont_ic_caret_right),
                        contentDescription = null,
                        tint = c.textTertiary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = part,
                    style = if (last) OntTheme.type.item.copy(fontWeight = FontWeight.Medium) else OntTheme.type.item,
                    color = if (last) c.textPrimary else c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OntClock()
    }
}

/** Time (Inter Tight, tabular) over a short date, refreshed at each new minute. */
@Composable
fun OntClock(modifier: Modifier = Modifier) {
    val c = OntTheme.colors
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(
                java.time.Duration
                    .between(LocalDateTime.now(), next)
                    .toMillis()
                    .coerceAtLeast(1_000),
            )
            now = LocalDateTime.now()
        }
    }
    val timeFormat =
        remember(locale) {
            DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
        }
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(now.format(timeFormat), style = OntTheme.type.clock, color = c.textPrimary)
        Text(now.format(dateFormat), style = OntTheme.type.meta, color = c.textMuted)
    }
}
