package lt.neko.slopbreak.protect

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class Admin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Slopbreak is locked. Unlock it in Slopbreak first; that takes an hour."

    companion object {
        fun component(context: Context) = ComponentName(context, Admin::class.java)

        fun active(context: Context): Boolean = context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        fun ask(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "So Android won't let Slopbreak be uninstalled while it's locked.")

        fun remove(context: Context) {
            if (active(context)) context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
        }
    }
}
