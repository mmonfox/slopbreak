package lt.neko.slopbreak.dev

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import androidx.core.graphics.scale
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class Recorder(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val looker: CoroutineDispatcher,
) {
    private val bubble = Bubble(service, scope, ::record)
    private val lastScreens = mutableMapOf<String, String>()

    fun show(on: Boolean) = if (on) bubble.show() else bubble.hide()

    fun saw(app: String, screen: String) {
        lastScreens[app] = screen
    }

    fun judged(blocked: Boolean) = bubble.judged(blocked)

    fun close() = bubble.hide()

    private fun record(verdict: Verdict) {
        scope.launch {
            val front = withContext(looker) { service.windows.firstOrNull { it.isActive } }
            val app = withContext(looker) { front?.root?.packageName?.toString() }
            val name = front?.title?.toString()?.takeIf { it.isNotBlank() } ?: app.orEmpty()
            val screen = JSONObject()
                .put("verdict", verdict.name.lowercase())
                .put("at", Instant.now().toString())
                .put("app", app)
                .put("name", name)
                .put("version", app?.let(::versionOf))
                .put("screen", app?.let(lastScreens::get))
                .put("windows", withContext(looker) { screenWindows(service) })
            val shot = bubble.outOfShot { screenshot(front?.displayId ?: Display.DEFAULT_DISPLAY) }
            withContext(Dispatchers.IO) { Recordings.save(service, verdict, name, screen, shot) }
            bubble.saved()
        }
    }

    private fun versionOf(app: String): String? = try {
        service.packageManager.getPackageInfo(app, 0).let { "${it.versionName} (${it.longVersionCode})" }
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "No version for $app", e)
        null
    }

    private suspend fun screenshot(display: Int): Bitmap? = suspendCancellableCoroutine { done ->
        service.takeScreenshot(
            display,
            service.mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val full = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    done.resume(full?.scale(full.width / 2, full.height / 2))
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed: $errorCode")
                    done.resume(null)
                }
            },
        )
    }
}

private const val TAG = "Slopbreak"
