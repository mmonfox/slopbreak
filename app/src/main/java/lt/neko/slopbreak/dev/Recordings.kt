package lt.neko.slopbreak.dev

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

enum class Verdict { BLOCK, KEEP }

data class Recording(val name: String, val verdict: Verdict, val app: String, val at: Long)

object Recordings {
    private val _all = MutableStateFlow<List<Recording>>(emptyList())
    val all: StateFlow<List<Recording>> = _all

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    fun load(context: Context) {
        _all.value = dir(context).listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file ->
                val (verdict, app) = NAME.matchEntire(file.nameWithoutExtension)?.destructured ?: return@mapNotNull null
                Recording(file.nameWithoutExtension, Verdict.valueOf(verdict.uppercase()), app, file.lastModified())
            }
            .sortedByDescending { it.at }
    }

    fun save(context: Context, verdict: Verdict, app: String, screen: JSONObject, shot: Bitmap?) {
        val name = LocalDateTime.now().format(STAMP) + "-" + verdict.name.lowercase() + "-" + app.replace(UNSAFE, "").ifEmpty { "app" }
        val dir = dir(context)
        shot?.let { File(dir, "$name.jpg").outputStream().use { out -> it.compress(Bitmap.CompressFormat.JPEG, SHOT_QUALITY, out) } }
        File(dir, "$name.json").writeText(screen.toString(1))
        load(context)
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach(File::delete)
        load(context)
    }

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val NAME = Regex("""[\d_-]+-(block|keep)-(.+)""")
    private val UNSAFE = Regex("""[^\p{L}\p{N} .]""")
}

private const val SHOT_QUALITY = 82
