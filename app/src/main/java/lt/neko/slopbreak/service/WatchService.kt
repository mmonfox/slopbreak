package lt.neko.slopbreak.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lt.neko.slopbreak.Prefs
import lt.neko.slopbreak.dev.Recorder
import lt.neko.slopbreak.limit.LIMITED
import lt.neko.slopbreak.limit.Usage
import lt.neko.slopbreak.protect.Guard

class WatchService : AccessibilityService() {
    private val scope = MainScope()
    // Node queries block on the target app.
    private val looker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private lateinit var prefs: Prefs
    private lateinit var guard: Guard
    private lateinit var notice: Notice
    private lateinit var recorder: Recorder
    private lateinit var keeper: Keeper
    private var front: String? = null
    private var keeping: Job? = null
    private var guarding: Job? = null

    override fun onServiceConnected() {
        prefs = Prefs.of(this)
        guard = Guard(this)
        notice = Notice(this, scope)
        recorder = Recorder(this, scope, looker)
        keeper = Keeper(this, prefs, notice, looker, changes, recorder::judged)
        Usage.load(this)
        _running.value = true
        scope.launch { combine(prefs.developer, prefs.recording) { developer, recording -> developer && recording }.collect(recorder::show) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::keeper.isInitialized) return
        val app = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()?.let { recorder.saw(app, it) }
            scope.launch { withContext(looker) { rootInActiveWindow?.packageName?.toString() }?.let(::frontIs) }
        }
        if (guard.watches(app)) checkScreen()
        if (app == front) changes.trySend(Unit)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        _running.value = false
        keeping?.cancel()
        Usage.save()
        if (::keeper.isInitialized) {
            notice.close()
            recorder.close()
        }
        scope.cancel()
        looker.close()
        super.onDestroy()
    }

    private fun frontIs(app: String) {
        if (app == front) return
        front = app
        keeping?.cancel()
        if (app in LIMITED) {
            keeping = scope.launch { keeper.keep(app) }
        } else {
            Usage.save()
            recorder.judged(false)
            if (!notice.lasting) notice.hide()
        }
    }

    private fun checkScreen() {
        val now = SystemClock.elapsedRealtime()
        prefs.settle(this)
        if (!prefs.protection.value.guarding(now, Prefs.boot(this)) || now < prefs.askingAdminUntil || !online()) return
        if (guarding?.isActive == true) return
        guarding = scope.launch {
            val height = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
            suspend fun threatened() = withContext(looker) { rootInActiveWindow?.let { guard.threatens(it, height) } == true }
            if (!threatened()) return@launch
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(GUARD_RECHECK_MS)
            if (threatened()) performGlobalAction(GLOBAL_ACTION_HOME)
            delay(GUARD_RECHECK_MS)
        }
    }

    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
    }
}

// Offline, nothing is enforced: airplane mode is the escape hatch.
internal fun Context.online(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private const val GUARD_RECHECK_MS = 400L
