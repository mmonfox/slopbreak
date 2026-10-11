package lt.neko.slopbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lt.neko.slopbreak.ui.theme.CornerSmall
import lt.neko.slopbreak.ui.theme.PillShape
import lt.neko.slopbreak.ui.theme.dimmedUnless
import lt.neko.slopbreak.ui.theme.glass
import lt.neko.slopbreak.ui.theme.rememberPressWash

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(glass(), MaterialTheme.shapes.extraLarge).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
internal fun <T> Segments(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().glass(glass(colors.surfaceContainer), PillShape).padding(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(PillShape)
                    .background(if (on) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(enabled = !on, interactionSource = null, indication = rememberPressWash(CornerSmall)) { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun RoundAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .dimmedUnless(enabled)
            .glass(glass(MaterialTheme.colorScheme.surfaceContainer), CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, interactionSource = null, indication = rememberPressWash(19.dp), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun rememberNow(clock: () -> Long = System::currentTimeMillis): Long {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = clock()
        }
    }
    return now
}

private const val TICK_MS = 15_000L
