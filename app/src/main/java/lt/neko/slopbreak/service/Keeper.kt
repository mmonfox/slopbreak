package lt.neko.slopbreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import lt.neko.slopbreak.Prefs
import lt.neko.slopbreak.limit.MINUTE_MS
import lt.neko.slopbreak.limit.Usage

internal class Keeper(
    private val service: AccessibilityService,
    private val prefs: Prefs,
    private val notice: Notice,
    private val looker: CoroutineDispatcher,
    private val changes: ReceiveChannel<Unit>,
    private val onWatching: (Boolean) -> Unit,
) {
    private var backedAt = 0L

    suspend fun keep(app: String) {
        val power = service.getSystemService(PowerManager::class.java)
        val shorts = ShortVideos(service, app)
        var last = System.currentTimeMillis()
        var ticks = 0
        var watching = false
        var left = Long.MAX_VALUE
        while (true) {
            val now = System.currentTimeMillis()
            if (watching && power.isInteractive && left > 0) Usage.count(last, now)
            last = now
            val arriving = !watching
            watching = withContext(looker) { shorts.showing() }
            if (watching == arriving) Log.i(TAG, "$app: " + if (watching) "short videos" else "elsewhere")
            onWatching(watching)
            if (watching) {
                left = hold(now, if (arriving) Long.MIN_VALUE else left)
            } else if (!arriving && !notice.lasting) {
                notice.hide()
            }
            if (++ticks % SAVE_EVERY_TICKS == 0) Usage.save()
            val mark = (MARKS + LIVE_LEFT_MS + 0L).filter { it < left }.maxOrNull()
            val wait = if (watching && mark != null && power.isInteractive) (left - mark).coerceAtMost(TICK_MS) else TICK_MS
            if (wait <= LOOK_GAP_MS) {
                delay(wait)
            } else {
                delay(LOOK_GAP_MS)
                withTimeoutOrNull(wait - LOOK_GAP_MS) { changes.receive() }
            }
        }
    }

    private fun hold(now: Long, before: Long): Long {
        val today = LocalDate.now().toEpochDay()
        val limit = prefs.limit.value
        if (!service.online()) return Long.MAX_VALUE
        val stops = limit.inForce(today).flatMap { it.stops(limit, now) }
        val next = stops.minByOrNull { it.inMs } ?: return Long.MAX_VALUE
        val lead = if (next.isBreak) "Break in " else ""
        val trail = if (next.isBreak) "" else " left"
        when {
            next.inMs <= 0 -> {
                // A second Back before the first lands exits the app.
                if (now - backedAt > BACK_SETTLE_MS) {
                    service.performGlobalAction(GLOBAL_ACTION_BACK)
                    backedAt = now
                }
                val back = stops.filter { it.inMs <= 0 }.maxOf { it.until() }
                if (stops.any { it.isBreak && it.inMs <= 0 } && now >= limit.breakUntil) prefs.saveLimit(limit.breakStarted(now, today))
                if (back - now <= LIVE_BACK_MS) {
                    notice.countdown(back, before = "Break · ", lasting = true)
                } else {
                    notice.show("Break until ${backAt(back)}", lasting = true)
                }
            }
            next.inMs <= LIVE_LEFT_MS -> notice.countdown(now + next.inMs, lead, trail)
            MARKS.any { next.inMs <= it && before > it } -> notice.countdown(now + next.inMs, lead, trail, forMs = MARK_SHOWN_MS)
        }
        return next.inMs
    }

    private fun backAt(at: Long): String {
        val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return if (at >= tomorrow) "tomorrow" else DateFormat.getTimeFormat(service).format(Date(at))
    }
}

private const val TAG = "Slopbreak"
private const val TICK_MS = 5_000L
private const val LOOK_GAP_MS = 1_000L
private const val SAVE_EVERY_TICKS = 12
private const val LIVE_LEFT_MS = 1 * MINUTE_MS
private val MARKS = listOf(10 * MINUTE_MS, 5 * MINUTE_MS, 2 * MINUTE_MS)
private const val MARK_SHOWN_MS = 10_000L
private const val BACK_SETTLE_MS = 1_500L
private const val LIVE_BACK_MS = 10 * MINUTE_MS
