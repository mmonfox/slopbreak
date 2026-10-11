package lt.neko.slopbreak.limit

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object Usage {
    private class Stretch(val start: Long, var end: Long)

    private val stretches = mutableListOf<Stretch>()
    private var file: File? = null

    @Synchronized
    fun load(context: Context) {
        if (file != null) return
        val file = File(context.filesDir, "usage.json").also { this.file = it }
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                stretches += Stretch(o.getLong("start"), o.getLong("end"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Usage unreadable, starting over", e)
        }
    }

    @Synchronized
    fun count(from: Long, to: Long) {
        if (to <= from) return
        val last = stretches.lastOrNull()
        if (last != null && from - last.end <= JOIN_MS) last.end = to else stretches += Stretch(from, to)
        stretches.removeAll { it.end < to - KEEP_MS }
    }

    @Synchronized
    fun used(from: Long, to: Long): Long = stretches.sumOf { (minOf(it.end, to) - maxOf(it.start, from)).coerceAtLeast(0L) }

    @Synchronized
    fun underAgainAt(limitMs: Long, window: Long, now: Long): Long {
        val edges = stretches.flatMap { listOf(it.start + window, it.end + window) }.filter { it > now }.sorted()
        return edges.firstOrNull { used(it - window, it) < limitMs } ?: now
    }

    @Synchronized
    fun streak(gap: Long, now: Long, since: Long = 0L): Long {
        var start = now
        for (stretch in stretches.asReversed()) {
            if (start - stretch.end >= gap || stretch.end <= since) break
            start = stretch.start
        }
        return used(maxOf(start, since), now)
    }

    @Synchronized
    fun clear() {
        stretches.clear()
        save()
    }

    @Synchronized
    fun save() {
        val file = file ?: return
        val array = JSONArray()
        stretches.forEach { array.put(JSONObject().put("start", it.start).put("end", it.end)) }
        try {
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Usage not saved", e)
        }
    }
}

private const val TAG = "Slopbreak"
private const val JOIN_MS = 15_000L
private const val KEEP_MS = 2 * 24 * 60 * 60 * 1000L
