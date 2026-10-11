package lt.neko.slopbreak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import lt.neko.slopbreak.limit.FIVE_HOURS_MS
import lt.neko.slopbreak.limit.FLOOR_MINUTES
import lt.neko.slopbreak.limit.Limit
import lt.neko.slopbreak.limit.MAX_MINUTES
import lt.neko.slopbreak.limit.MINUTE_MS
import lt.neko.slopbreak.limit.Mode
import lt.neko.slopbreak.limit.STEP_MINUTES
import lt.neko.slopbreak.limit.Usage

@Composable
internal fun LimitCard(limit: Limit, onLimit: (Mode, Int) -> Unit) {
    val now = rememberNow()
    val fiveHours = limit.mode == Mode.FIVE_HOURS
    val from = if (fiveHours) now - FIVE_HOURS_MS else LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val held = limit.held(LocalDate.now().toEpochDay())
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Time limit", style = MaterialTheme.typography.titleMedium)
                Note("Reels and Shorts")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Usage.used(from, now) / MINUTE_MS} min", style = MaterialTheme.typography.titleMedium)
                Note(if (fiveHours) "in 5 hours" else "today")
            }
        }
        Segments(Mode.entries.map { it to it.label }, limit.mode) { onLimit(it, limit.minutes) }
        if (limit.mode == Mode.DAY || fiveHours) Minutes(limit) { onLimit(limit.mode, it) }
        if (held.isNotEmpty()) Note("Until tomorrow, still " + held.joinToString(" and ") { it.said() })
    }
}

@Composable
private fun Minutes(limit: Limit, onMinutes: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (limit.mode == Mode.DAY) "A day" else "Every 5 hours", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RoundAction("−", enabled = limit.minutes > FLOOR_MINUTES) { onMinutes(limit.minutes - STEP_MINUTES) }
        Text("${limit.minutes} min", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
        RoundAction("+", enabled = limit.minutes < MAX_MINUTES) { onMinutes(limit.minutes + STEP_MINUTES) }
    }
}

private fun Limit.said() = when (mode) {
    Mode.DAY -> "$minutes min a day"
    Mode.FIVE_HOURS -> "$minutes min per 5 hours"
    else -> mode.label.lowercase()
}
