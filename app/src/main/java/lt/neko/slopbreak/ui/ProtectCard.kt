package lt.neko.slopbreak.ui

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import lt.neko.slopbreak.Prefs
import lt.neko.slopbreak.protect.Protection
import lt.neko.slopbreak.ui.theme.SwitchRow
import lt.neko.slopbreak.ui.theme.TextAction

@Composable
internal fun ProtectCard(protection: Protection, adminActive: Boolean, onProtect: () -> Unit, onTurnOff: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val boot = remember { Prefs.boot(context) }
    val now = rememberNow(SystemClock::elapsedRealtime)
    val arming = protection.arming(now, boot)
    val waiting = protection.waiting(now, boot)
    fun clock(at: Long) = DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (at - now)))
    Card {
        SwitchRow(
            "Lock",
            when {
                !protection.on -> "Stops Slopbreak from being uninstalled or switched off. Unlocking then takes an hour."
                waiting -> "Unlocks at ${clock(protection.offAt)}."
                arming -> "Locks at ${clock(protection.armsAt)}. Until then you can unlock right away."
                else -> "Locked: can't be uninstalled or switched off. Unlocking takes an hour."
            },
            checked = protection.on && !waiting,
            onChange = { on ->
                when {
                    on && waiting -> onCancel()
                    on -> onProtect()
                    else -> onTurnOff()
                }
            },
        )
        if (protection.on && !adminActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Android isn't refusing to uninstall it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextAction("Fix", onProtect)
            }
        }
    }
}
