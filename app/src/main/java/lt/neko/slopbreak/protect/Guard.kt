package lt.neko.slopbreak.protect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import lt.neko.slopbreak.R

internal class Guard(context: Context) {
    private val name = context.getString(R.string.app_name).lowercase()
    private val settings = handlers(context, Intent(Settings.ACTION_SETTINGS)) + MANAGERS
    private val uninstallers = handlers(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))) + UNINSTALLERS
    private val launchers = handlers(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    fun watches(app: String?) = app in settings || app in uninstallers || app in launchers

    fun threatens(root: AccessibilityNodeInfo, screenHeight: Int): Boolean {
        val app = root.packageName?.toString() ?: return false
        if (!watches(app)) return false
        val lines = mutableListOf<Line>()
        collect(root, lines, intArrayOf(MAX_NODES))
        return when (app) {
            in uninstallers -> lines.any { name in it.text }
            in launchers -> lines.any { !it.described && name in it.text && it.text != name } && lines.any { it.button }
            else -> lines.any { it.text == name && it.top < screenHeight * TITLE_ZONE }
        }
    }

    private class Line(val text: String, val top: Int, val described: Boolean = false, val button: Boolean = false)

    private fun collect(node: AccessibilityNodeInfo, into: MutableList<Line>, budget: IntArray) {
        if (budget[0]-- <= 0) return
        val top by lazy { Rect().also(node::getBoundsInScreen).top }
        val button = node.className?.toString()?.endsWith("Button") == true
        node.text?.let { into += Line(it.toString().trim().lowercase(), top, button = button) }
        node.contentDescription?.let { into += Line(it.toString().trim().lowercase(), top, described = true, button = button) }
        if (button && node.text == null && node.contentDescription == null) into += Line("", top, button = true)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, into, budget) }
    }
}

private fun handlers(context: Context, intent: Intent): Set<String> =
    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapTo(mutableSetOf()) { it.activityInfo.packageName }

private val MANAGERS = setOf(
    "com.miui.securitycenter",
    "com.samsung.android.lool",
    "com.samsung.android.sm",
    "com.samsung.android.sm_cn",
    "com.huawei.systemmanager",
    "com.hihonor.systemmanager",
    "com.coloros.safecenter",
    "com.oplus.safecenter",
    "com.iqoo.secure",
)

private val UNINSTALLERS = setOf(
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.samsung.android.packageinstaller",
    "com.miui.packageinstaller",
)

private const val TITLE_ZONE = 0.35f
private const val MAX_NODES = 800
