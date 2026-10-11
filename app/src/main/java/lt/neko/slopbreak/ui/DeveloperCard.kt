package lt.neko.slopbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lt.neko.slopbreak.dev.Recording
import lt.neko.slopbreak.dev.Verdict
import lt.neko.slopbreak.ui.theme.SwitchRow
import lt.neko.slopbreak.ui.theme.TextAction

@Composable
internal fun DeveloperCard(recording: Boolean, recordings: List<Recording>, onRecording: (Boolean) -> Unit, onClear: () -> Unit) {
    Card {
        SwitchRow(
            "Record button",
            "Red where the rules count the time, green where they don't. Record a screen they get wrong.",
            recording,
            onRecording,
        )
        if (recording || recordings.isNotEmpty()) Recorded(recordings, onClear)
    }
}

@Composable
private fun Recorded(recordings: List<Recording>, onClear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (recordings.isEmpty()) "Nothing recorded yet." else "${recordings.size} recorded",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        when {
            recordings.isEmpty() -> Unit
            !confirm -> TextAction("Clear", { confirm = true })
            else -> {
                TextAction("Cancel", { confirm = false })
                TextAction("Delete all", { confirm = false; onClear() }, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    recordings.groupBy { it.app }.forEach { (app, mine) -> AppCount(app, mine) }
}

@Composable
private fun AppCount(app: String, recordings: List<Recording>) {
    val colors = MaterialTheme.colorScheme
    val blocks = recordings.count { it.verdict == Verdict.BLOCK }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(app, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Count(blocks, "block", colors.error)
        Spacer(Modifier.width(14.dp))
        Count(recordings.size - blocks, "keep", colors.tertiary)
    }
}

@Composable
private fun Count(count: Int, label: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("$count $label", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
