package lt.neko.slopbreak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lt.neko.slopbreak.limit.Usage
import lt.neko.slopbreak.protect.Admin

// DUMP: only adb and the system can send these.
class Adb : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.of(context)
        when (intent.action) {
            RELEASE -> {
                Admin.remove(context)
                prefs.release()
            }
            RESET -> {
                Usage.load(context)
                Usage.clear()
                prefs.freshLimit()
            }
            DEVELOPER -> prefs.setDeveloper(intent.getBooleanExtra("on", true))
        }
    }

    private companion object {
        const val RELEASE = "lt.neko.slopbreak.RELEASE"
        const val RESET = "lt.neko.slopbreak.RESET"
        const val DEVELOPER = "lt.neko.slopbreak.DEVELOPER"
    }
}
