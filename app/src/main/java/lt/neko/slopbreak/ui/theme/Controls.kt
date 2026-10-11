package lt.neko.slopbreak.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull

class Haptics(private val feedback: HapticFeedback?) {
    fun tick() {
        feedback?.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null) }

fun Modifier.dimmedUnless(enabled: Boolean): Modifier = graphicsLayer {
    alpha = if (enabled) 1f else DISABLED_ALPHA
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

internal suspend fun AwaitPointerEventScope.holdSideways(pointerId: PointerId, onDrag: (Float) -> Unit) {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val held = event.changes.fastFirstOrNull { it.id == pointer } ?: return
        if (held.isConsumed) return
        if (held.changedToUpIgnoreConsumed()) {
            held.consume()
            pointer = event.changes.fastFirstOrNull { it.pressed }?.id ?: return
            continue
        }
        val dx = held.positionChange().x
        held.consume()
        if (dx != 0f) onDrag(dx)
    }
}

@Composable
fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .toggleable(value = checked, enabled = enabled, interactionSource = press, indication = rememberPressWash(CornerSmall), role = Role.Switch) {
                haptics.tick()
                onChange(it)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        GlassSwitch(checked, onCheckedChange = onChange, enabled = enabled, interactionSource = press)
    }
}

@Composable
@ReadOnlyComposable
private fun accentGlass(): Glass = clearGlass(lerp(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary, ACCENT_TINT))

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val washColor = colors.primary.copy(alpha = androidx.compose.ui.util.lerp(ACCENT_WASH_LIGHT, ACCENT_WASH_DARK, colors.darkness))
    val wash = remember(washColor) { PressWash(washColor, PILL_WELL_CORNER, PILL_WELL_INSET, PILL_WELL_INSET) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink = animateFloatAsState(if (pressed) KEY_SINK else 1f, KEY_SINK_SPRING, label = "buttonSink")
    Row(
        modifier
            .sizeIn(minWidth = BUTTON_MIN_WIDTH, minHeight = CONTROL_HEIGHT)
            .glass(accentGlass(), MaterialTheme.shapes.large)
            .clickable(interactionSource = source, indication = wash, role = Role.Button, onClick = onClick)
            .drawWithContent {
                val s = sink.value
                if (s == 1f) drawContent() else scale(s) { this@drawWithContent.drawContent() }
            }
            .padding(horizontal = BUTTON_PAD, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.primary, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Text(text)
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clickable(interactionSource = null, indication = rememberPressWash(ICON_WELL_CORNER, insetY = 4.dp), role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

private const val DISABLED_ALPHA = 0.38f
private val CONTROL_HEIGHT = 48.dp
private val ICON_WELL_CORNER = 9.dp
private val BUTTON_PAD = 20.dp
private val BUTTON_MIN_WIDTH = 112.dp
private val PILL_WELL_INSET = 4.dp
private val PILL_WELL_CORNER = 16.dp
private const val ACCENT_TINT = 0.07f
private const val ACCENT_WASH_LIGHT = 0.14f
private const val ACCENT_WASH_DARK = 0.16f
private const val KEY_SINK = 0.94f
private val KEY_SINK_SPRING = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh)
