package lt.neko.slopbreak.dev

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.VelocityTracker
import android.view.WindowInsets
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.neko.slopbreak.Prefs
import lt.neko.slopbreak.service.OverlayOwner
import lt.neko.slopbreak.ui.theme.SlopbreakTheme
import lt.neko.slopbreak.ui.theme.Lucide
import lt.neko.slopbreak.ui.theme.PillShape
import lt.neko.slopbreak.ui.theme.glass
import lt.neko.slopbreak.ui.theme.rememberPressWash

internal class Bubble(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val onPick: (Verdict) -> Unit,
) {
    private class Overlay(val view: ComposeView, val owner: OverlayOwner)

    private val windows = service.getSystemService(WindowManager::class.java)
    private val prefs = Prefs.of(service)
    private val margin = (EDGE_MARGIN_DP * service.resources.displayMetrics.density).roundToInt()
    private val slop = ViewConfiguration.get(service).scaledTouchSlop
    private val params = overlayParams()
    private val menuParams = overlayParams()
    private var button: Overlay? = null
    private var menu: Overlay? = null
    private var saved by mutableStateOf(false)
    private var blocked by mutableStateOf(false)
    private var menuOut by mutableStateOf(false)
    private var onLeft by mutableStateOf(false)
    private var docked = true
    private var settling: Job? = null
    private var gliding: Job? = null

    fun show() {
        if (button != null) return
        dock(prefs.bubbleOnLeft, prefs.bubbleTop.takeIf { it >= 0 } ?: (screen().height() * DEFAULT_TOP).roundToInt())
        button = add(params) { Button() }
    }

    fun hide() {
        gliding?.cancel()
        closeMenu()
        button?.let(::remove)
        button = null
        saved = false
    }

    suspend fun <T> outOfShot(block: suspend () -> T): T {
        closeMenu()
        val view = button?.view
        view?.visibility = View.INVISIBLE
        try {
            delay(HIDE_MS)
            return block()
        } finally {
            view?.visibility = View.VISIBLE
        }
    }

    fun judged(blocked: Boolean) {
        this.blocked = blocked
    }

    fun saved() {
        saved = true
        settling?.cancel()
        settling = scope.launch {
            delay(SAVED_MS)
            saved = false
        }
    }

    private fun add(params: WindowManager.LayoutParams, content: @Composable () -> Unit): Overlay {
        val owner = OverlayOwner()
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }
        windows.addView(view, params)
        owner.resume()
        return Overlay(view, owner)
    }

    private fun remove(overlay: Overlay) {
        windows.removeView(overlay.view)
        overlay.owner.destroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun openMenu() {
        if (menu != null) return
        menuParams.gravity = params.gravity
        menuParams.x = params.x
        menuParams.y = params.y
        menuOut = true
        val menu = add(menuParams) { Menu() }
        menu.view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) menuOut = false
            false
        }
        this.menu = menu
    }

    private fun closeMenu() {
        menu?.let(::remove)
        menu = null
        menuOut = false
        button?.view?.alpha = 1f
    }

    @Composable
    private fun Button() {
        SlopbreakTheme(dark = isSystemInDarkTheme()) {
            val colors = MaterialTheme.colorScheme
            val verdict by animateColorAsState(if (blocked) colors.error else colors.tertiary, spring(stiffness = Spring.StiffnessMediumLow))
            Box(Modifier.glass(glass(floating = true), PillShape).padding(PILL_PAD)) {
                if (!saved) {
                    val said = if (blocked) "Record this screen, now blocked" else "Record this screen, now kept"
                    Box(Modifier.size(FACE).clip(CircleShape).then(dragOrTap()).semantics { contentDescription = said }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).border(1.75.dp, verdict, CircleShape).padding(5.dp).background(verdict, CircleShape))
                    }
                } else {
                    Box(Modifier.size(FACE), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Check, "Saved", tint = colors.tertiary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun Menu() {
        SlopbreakTheme(dark = isSystemInDarkTheme()) {
            val colors = MaterialTheme.colorScheme
            val reveal = remember { Animatable(0f) }
            LaunchedEffect(menuOut) {
                if (menuOut) {
                    withFrameNanos {}
                    withFrameNanos {}
                    button?.view?.alpha = 0f
                    reveal.animateTo(1f, REVEAL)
                } else {
                    reveal.animateTo(0f, REVEAL)
                    closeMenu()
                }
            }
            val left = onLeft
            val round = with(LocalDensity.current) { (FACE + PILL_PAD * 2).toPx() }
            Box(
                Modifier.drawWithContent {
                    val width = lerp(round, size.width, reveal.value)
                    val from = if (left) 0f else size.width - width
                    clipRect(from, 0f, from + width, size.height) { this@drawWithContent.drawContent() }
                },
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val full = constraints.maxWidth
                            val width = lerp(round, full.toFloat(), reveal.value).roundToInt().coerceIn(0, full)
                            val pane = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                            layout(full, constraints.maxHeight) { pane.place(if (left) 0 else full - width, 0) }
                        }
                        .glass(glass(floating = true), PillShape),
                )
                Row(
                    Modifier.padding(PILL_PAD).graphicsLayer { alpha = ((reveal.value - FADE_FROM) / (1f - FADE_FROM)).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (left) CloseFace()
                    Choice(Lucide.Ban, "Block", colors.error) { pick(Verdict.BLOCK) }
                    Choice(Lucide.Check, "Keep", colors.tertiary) { pick(Verdict.KEEP) }
                    if (!left) CloseFace()
                }
            }
        }
    }

    @Composable
    private fun CloseFace() {
        Box(Modifier.size(FACE).clickable(interactionSource = null, indication = rememberPressWash(PILL_CORNER)) { menuOut = false }, contentAlignment = Alignment.Center) {
            Icon(Lucide.X, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    @Composable
    private fun Choice(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
        Row(
            Modifier.height(FACE).clickable(interactionSource = null, indication = rememberPressWash(PILL_CORNER), onClick = onClick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    private fun pick(verdict: Verdict) {
        scope.launch {
            closeMenu()
            onPick(verdict)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @OptIn(ExperimentalComposeUiApi::class)
    private fun dragOrTap(): Modifier {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var speed: VelocityTracker? = null
        fun track(event: MotionEvent) {
            val raw = MotionEvent.obtain(event).apply { setLocation(event.rawX, event.rawY) }
            speed?.addMovement(raw)
            raw.recycle()
        }
        return Modifier.pointerInteropFilter { event ->
            val view = button?.view ?: return@pointerInteropFilter false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gliding?.cancel()
                    downX = event.rawX
                    downY = event.rawY
                    val at = IntArray(2).also(view::getLocationOnScreen)
                    startX = at[0]
                    startY = at[1]
                    dragging = false
                    speed?.recycle()
                    speed = VelocityTracker.obtain()
                    track(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    track(event)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        docked = false
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        windows.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    track(event)
                    val tracker = speed
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    val vy = tracker?.yVelocity ?: 0f
                    tracker?.recycle()
                    speed = null
                    when {
                        dragging || !docked -> glide(view, vx, vy)
                        event.actionMasked == MotionEvent.ACTION_UP -> openMenu()
                    }
                }
            }
            true
        }
    }

    private fun glide(view: View, vx: Float, vy: Float) {
        val screen = screen()
        val bars = windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val left = when {
            vx > FLING -> false
            vx < -FLING -> true
            else -> params.x + view.width / 2 < screen.width() / 2
        }
        val toX = if (left) margin else screen.width() - view.width - margin
        val high = bars.top + margin
        val toY = params.y.coerceIn(high, (screen.height() - bars.bottom - view.height - margin).coerceAtLeast(high))
        gliding?.cancel()
        docked = false
        gliding = scope.launch(AndroidUiDispatcher.Main) {
            Animatable(Offset(params.x.toFloat(), params.y.toFloat()), Offset.VectorConverter)
                .animateTo(Offset(toX.toFloat(), toY.toFloat()), GLIDE, initialVelocity = Offset(vx, vy)) {
                    params.x = value.x.roundToInt()
                    params.y = value.y.roundToInt()
                    if (button?.view === view) windows.updateViewLayout(view, params)
                }
            dock(left, toY)
            if (button?.view === view) windows.updateViewLayout(view, params)
            prefs.placeBubble(left, toY)
        }
    }

    private fun dock(left: Boolean, top: Int) {
        onLeft = left
        docked = true
        params.gravity = Gravity.TOP or (if (left) Gravity.START else Gravity.END)
        params.x = margin
        params.y = top
    }

    private fun screen(): Rect = windows.currentWindowMetrics.bounds
}

private fun overlayParams() = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    PixelFormat.TRANSLUCENT,
)

private val FACE = 44.dp

private val PILL_PAD = 4.dp

private const val FADE_FROM = 0.4f

private val REVEAL = spring<Float>(dampingRatio = 0.9f, stiffness = 1100f)

private val GLIDE = spring<Offset>(dampingRatio = 0.75f, stiffness = 420f)

private const val FLING = 900f

private val PILL_CORNER = 22.dp

private const val EDGE_MARGIN_DP = 8

private const val DEFAULT_TOP = 0.42f

private const val HIDE_MS = 120L

private const val SAVED_MS = 900L
