package lt.neko.slopbreak.limit

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

const val INSTAGRAM = "com.instagram.android"
const val YOUTUBE = "com.google.android.youtube"

val LIMITED = setOf(
    INSTAGRAM,
    YOUTUBE,
    "app.morphe.android.youtube",
    "app.revanced.android.youtube",
    "app.rvx.android.youtube",
)

enum class Mode(val label: String) { OFF("Off"), AUTO("Auto"), DAY("Daily"), FIVE_HOURS("5 hours") }

class Stop(val inMs: Long, val isBreak: Boolean, val until: () -> Long)

fun breakMs(n: Int): Long {
    var ms = FIRST_BREAK_MS
    for (step in 1..n.coerceAtMost(MAX_BREAK_STEPS)) ms += FIRST_BREAK_MS shl ((step - 1) / BREAK_STEPS_TO_DOUBLE)
    return ms
}

// Easing applies tomorrow; until then [held] stays enforced.
data class Limit(
    val mode: Mode = Mode.OFF,
    val minutes: Int = START_MINUTES,
    val breaks: Int = 0,
    val breaksDay: Long = -1L,
    val breakUntil: Long = 0L,
    val held: List<Limit> = emptyList(),
    val heldDay: Long = -1L,
) {
    fun inForce(today: Long): List<Limit> = listOf(rule()) + held(today)

    fun held(today: Long): List<Limit> = if (heldDay >= today) tightest(held) else emptyList()

    fun changedTo(mode: Mode, minutes: Int, today: Long): Limit {
        val next = copy(mode = mode, minutes = minutes)
        val held = tightest((held(today) + rule()).filter { next.rule().eases(it) })
        return next.copy(held = held, heldDay = if (held.isEmpty()) -1L else today)
    }

    fun breaks(today: Long) = if (breaksDay == today) breaks else 0

    fun breakStarted(now: Long, today: Long) =
        copy(breakUntil = now + breakMs(breaks(today)), breaks = breaks(today) + 1, breaksDay = today)

    fun fresh() = copy(breaks = 0, breaksDay = -1L, breakUntil = 0L, held = emptyList(), heldDay = -1L)

    fun stops(state: Limit, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Stop> {
        val day = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        return when (mode) {
            Mode.OFF -> emptyList()
            Mode.DAY -> {
                val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                listOf(Stop(minutes * MINUTE_MS - Usage.used(start, now), isBreak = false) { end })
            }
            Mode.FIVE_HOURS -> listOf(fiveHours(minutes * MINUTE_MS, now))
            Mode.AUTO -> {
                val breakMs = breakMs(state.breaks(day.toEpochDay()))
                val pause = if (now < state.breakUntil) {
                    Stop(0L, isBreak = true) { state.breakUntil }
                } else {
                    Stop(AUTO_USE_MS - Usage.streak(breakMs, now, since = state.breakUntil), isBreak = true) { now + breakMs }
                }
                listOf(fiveHours(AUTO_MINUTES * MINUTE_MS, now), pause)
            }
        }
    }

    private fun fiveHours(budget: Long, now: Long) =
        Stop(budget - Usage.used(now - FIVE_HOURS_MS, now), isBreak = false) { Usage.underAgainAt(budget, FIVE_HOURS_MS, now) }

    private fun rule() = Limit(mode, if (mode == Mode.DAY || mode == Mode.FIVE_HOURS) minutes else START_MINUTES)

    private fun eases(before: Limit): Boolean = when {
        before.mode == Mode.OFF -> false
        mode == Mode.OFF -> true
        mode == Mode.AUTO || before.mode == Mode.AUTO -> mode != before.mode
        mode == Mode.FIVE_HOURS && before.mode == Mode.DAY -> true
        else -> minutes > before.minutes
    }

    private fun tightest(rules: List<Limit>): List<Limit> =
        rules.distinct().let { all -> all.filter { rule -> all.none { it !== rule && !it.eases(rule) } } }

    fun json(): String = JSONObject()
        .put("mode", mode.name)
        .put("minutes", minutes)
        .put("breaks", breaks)
        .put("breaksDay", breaksDay)
        .put("breakUntil", breakUntil)
        .put("held", JSONArray().also { array -> held.forEach { array.put(JSONObject(it.json())) } })
        .put("heldDay", heldDay)
        .toString()

    companion object {
        fun of(json: String): Limit {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return Limit()
            val held = o.optJSONArray("held")
            return Limit(
                mode = Mode.entries.firstOrNull { it.name == o.optString("mode") } ?: Mode.OFF,
                minutes = o.optInt("minutes", START_MINUTES),
                breaks = o.optInt("breaks"),
                breaksDay = o.optLong("breaksDay", -1L),
                breakUntil = o.optLong("breakUntil"),
                held = held?.let { (0 until it.length()).map { i -> of(it.getJSONObject(i).toString()) } }.orEmpty(),
                heldDay = o.optLong("heldDay", -1L),
            )
        }
    }
}

const val START_MINUTES = 120
const val STEP_MINUTES = 5
const val FLOOR_MINUTES = 5
const val MAX_MINUTES = 240
const val AUTO_MINUTES = 240
const val SECOND_MS = 1000L
const val MINUTE_MS = 60 * SECOND_MS
const val FIVE_HOURS_MS = 5 * 60 * MINUTE_MS
const val AUTO_USE_MS = 5 * MINUTE_MS
private const val FIRST_BREAK_MS = 30 * SECOND_MS
private const val BREAK_STEPS_TO_DOUBLE = 5
private const val MAX_BREAK_STEPS = 30
