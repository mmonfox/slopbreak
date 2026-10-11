package lt.neko.slopbreak

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lt.neko.slopbreak.limit.Limit
import lt.neko.slopbreak.limit.Mode
import lt.neko.slopbreak.protect.Admin
import lt.neko.slopbreak.protect.Protection

class Prefs private constructor(context: Context) {
    private val store = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _developer = MutableStateFlow(store.getBoolean(DEVELOPER, false))
    val developer: StateFlow<Boolean> = _developer

    private val _recording = MutableStateFlow(store.getBoolean(RECORDING, false))
    val recording: StateFlow<Boolean> = _recording

    private val _protection = MutableStateFlow(
        Protection(
            store.getBoolean(PROTECTED, false),
            store.getLong(UNLOCK_ASKED, 0L),
            store.getInt(UNLOCK_BOOT, -1),
            store.getLong(ARMED_AT, 0L),
            store.getInt(ARMED_BOOT, -1),
        ),
    )
    val protection: StateFlow<Protection> = _protection

    private val _limit = MutableStateFlow(store.getString(LIMIT, null)?.let(Limit::of) ?: Limit())
    val limit: StateFlow<Limit> = _limit

    var askingAdminUntil = 0L
        private set

    val bubbleOnLeft: Boolean get() = store.getBoolean(BUBBLE_LEFT, false)

    val bubbleTop: Int get() = store.getInt(BUBBLE_TOP, -1)

    fun setDeveloper(on: Boolean) {
        store.edit { putBoolean(DEVELOPER, on) }
        _developer.value = on
        if (!on) setRecording(false)
    }

    fun setRecording(on: Boolean) {
        store.edit { putBoolean(RECORDING, on) }
        _recording.value = on
    }

    fun askingAdmin() {
        askingAdminUntil = SystemClock.elapsedRealtime() + ADMIN_ASK_MS
    }

    fun protect(context: Context) = setProtection(Protection(on = true, armedAt = SystemClock.elapsedRealtime(), armedBoot = boot(context)))

    fun turnOff(context: Context) {
        if (_protection.value.arming(SystemClock.elapsedRealtime(), boot(context))) {
            off(context)
        } else {
            setProtection(_protection.value.copy(askedAt = SystemClock.elapsedRealtime(), askedBoot = boot(context)))
        }
    }

    fun cancelTurnOff() = setProtection(_protection.value.copy(askedAt = 0L, askedBoot = -1))

    fun settle(context: Context) {
        if (_protection.value.due(SystemClock.elapsedRealtime(), boot(context))) off(context)
    }

    fun release() = setProtection(Protection(on = false))

    private fun off(context: Context) {
        setProtection(Protection(on = false))
        Admin.remove(context)
    }

    private fun setProtection(protection: Protection) {
        store.edit {
            putBoolean(PROTECTED, protection.on)
            putLong(UNLOCK_ASKED, protection.askedAt)
            putInt(UNLOCK_BOOT, protection.askedBoot)
            putLong(ARMED_AT, protection.armedAt)
            putInt(ARMED_BOOT, protection.armedBoot)
        }
        _protection.value = protection
    }

    fun setLimit(mode: Mode, minutes: Int) = saveLimit(_limit.value.changedTo(mode, minutes, LocalDate.now().toEpochDay()))

    fun freshLimit() = saveLimit(_limit.value.fresh())

    fun saveLimit(limit: Limit) {
        if (_limit.value == limit) return
        store.edit { putString(LIMIT, limit.json()) }
        _limit.value = limit
    }

    fun placeBubble(left: Boolean, top: Int) = store.edit {
        putBoolean(BUBBLE_LEFT, left)
        putInt(BUBBLE_TOP, top)
    }

    companion object {
        private const val DEVELOPER = "developer"
        private const val RECORDING = "recording"
        private const val BUBBLE_LEFT = "bubble_left"
        private const val BUBBLE_TOP = "bubble_top"
        private const val PROTECTED = "protected"
        private const val UNLOCK_ASKED = "unlock_asked"
        private const val UNLOCK_BOOT = "unlock_boot"
        private const val ARMED_AT = "armed_at"
        private const val ARMED_BOOT = "armed_boot"
        private const val LIMIT = "limit"
        private const val ADMIN_ASK_MS = 2 * 60 * 1000L

        fun boot(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

        @Volatile private var instance: Prefs? = null

        fun of(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext).also { instance = it }
        }
    }
}
