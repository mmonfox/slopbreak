package lt.neko.slopbreak.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.neko.slopbreak.ui.theme.SlopbreakTheme
import lt.neko.slopbreak.ui.theme.PillShape
import lt.neko.slopbreak.ui.theme.glass

internal class Notice(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var ticking: Job? = null
    private var removing: Job? = null
    private var before by mutableStateOf("")
    private var clock by mutableStateOf("")
    private var after by mutableStateOf("")
    private var shown by mutableStateOf(false)
    private var closing by mutableStateOf(false)
    private var to = 0L
    private var endsAt = 0L

    var lasting = false
        private set

    fun show(message: String, lasting: Boolean = false) =
        put(Long.MAX_VALUE, System.currentTimeMillis() + SHOWN_MS, lasting, message, "")

    fun countdown(to: Long, before: String = "", after: String = "", forMs: Long? = null, lasting: Boolean = false) =
        put(to, forMs?.let { System.currentTimeMillis() + it } ?: to, lasting, before, after)

    fun hide() {
        ticking?.cancel()
        ticking = null
        leave()
    }

    fun close() {
        hide()
        remove()
    }

    private fun put(to: Long, endsAt: Long, lasting: Boolean, before: String, after: String) {
        this.to = to
        this.endsAt = endsAt
        this.lasting = lasting
        this.before = before
        this.after = after
        update(System.currentTimeMillis())
        removing?.cancel()
        if (view == null) add()
        shown = true
        if (ticking?.isActive != true) ticking = scope.launch { tick() }
    }

    private fun update(now: Long) {
        clock = if (to == Long.MAX_VALUE) "" else clock(to - now)
        closing = !lasting && to - now <= CLOSING_MS
    }

    private suspend fun tick() {
        while (true) {
            val now = System.currentTimeMillis()
            val end = minOf(to, endsAt)
            if (now >= end) break
            update(now)
            delay(minOf(end - now, (to - now - 1) % SECOND_MS + 1))
        }
        leave()
    }

    private fun leave() {
        shown = false
        removing?.cancel()
        removing = scope.launch {
            delay(OUT_MS)
            remove()
        }
    }

    private fun remove() {
        view?.let(windows::removeView)
        owner?.destroy()
        view = null
        owner = null
    }

    private fun add() {
        val density = service.resources.displayMetrics.density
        val top = windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = top + ((GAP_DP - ROOM_DP) * density).roundToInt()
        }
        val owner = OverlayOwner()
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                SlopbreakTheme(dark = isSystemInDarkTheme()) { Pill() }
            }
        }
        windows.addView(view, params)
        owner.resume()
        this.view = view
        this.owner = owner
    }

    @Composable
    private fun Pill() {
        val arrive = remember { Animatable(0f) }
        LaunchedEffect(shown) {
            arrive.animateTo(if (shown) 1f else 0f, if (shown) spring(0.62f, 520f) else spring(1f, 900f))
        }
        val colors = MaterialTheme.colorScheme
        val color by animateColorAsState(if (closing) colors.primary else colors.onSurface, spring(stiffness = Spring.StiffnessLow))
        val style = MaterialTheme.typography.labelLarge.copy(color = color, fontFeatureSettings = "tnum")
        Box(Modifier.fillMaxWidth().padding(ROOM_DP.dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .graphicsLayer {
                        val t = arrive.value
                        alpha = t.coerceIn(0f, 1f)
                        scaleX = 0.8f + 0.2f * t
                        scaleY = 0.8f + 0.2f * t
                        translationY = (1f - t) * -10.dp.toPx()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .glass(glass(floating = true), PillShape)
                    .animateContentSize(spring(0.8f, Spring.StiffnessMediumLow))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swap(before, style)
                Rolling(clock, style)
                Swap(after, style)
            }
        }
    }
}

@Composable
private fun Swap(text: String, style: TextStyle) {
    AnimatedContent(
        text,
        transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)) using SizeTransform(clip = false) },
        label = "words",
    ) {
        if (it.isNotEmpty()) Text(it, style = style, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Rolling(clock: String, style: TextStyle) {
    Row {
        clock.forEachIndexed { i, char ->
            key(clock.length - i) {
                AnimatedContent(
                    char,
                    transitionSpec = {
                        val move = spring(0.75f, Spring.StiffnessMediumLow, IntOffset.VisibilityThreshold)
                        (slideInVertically(move) { -it } + fadeIn(spring(stiffness = Spring.StiffnessMedium))) togetherWith
                            (slideOutVertically(move) { it } + fadeOut(spring(stiffness = Spring.StiffnessHigh))) using
                            SizeTransform(clip = true) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold) }
                    },
                    label = "digit",
                ) { Text(it.toString(), style = style, maxLines = 1, softWrap = false) }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) + SECOND_MS - 1) / SECOND_MS
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val SECOND_MS = 1000L

private const val SHOWN_MS = 6_000L

private const val OUT_MS = 350L

private const val CLOSING_MS = 10_000L

private const val GAP_DP = 10

private const val ROOM_DP = 12
