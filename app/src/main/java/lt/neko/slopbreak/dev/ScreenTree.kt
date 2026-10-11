package lt.neko.slopbreak.dev

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

internal fun screenWindows(service: AccessibilityService): JSONArray {
    val out = JSONArray()
    val budget = Budget()
    for (window in service.windows) {
        val root = window.root ?: continue
        val app = root.packageName?.toString()
        if (app == service.packageName) continue
        out.put(
            JSONObject()
                .put("app", app)
                .put("type", windowType(window.type))
                .put("title", window.title?.toString())
                .put("active", window.isActive)
                .put("layer", window.layer)
                .put("bounds", Rect().also(window::getBoundsInScreen).json())
                .put("root", node(root, 0, budget)),
        )
    }
    return out
}

private class Budget(var nodes: Int = MAX_NODES)

private fun node(info: AccessibilityNodeInfo, depth: Int, budget: Budget): JSONObject {
    budget.nodes--
    val json = JSONObject()
    info.className?.let { json.put("class", it.toString().substringAfterLast('.')) }
    info.viewIdResourceName?.let { json.put("id", it.substringAfter(":id/")) }
    info.text?.takeIf { it.isNotEmpty() }?.let { json.put("text", it.toString().take(MAX_TEXT)) }
    info.contentDescription?.takeIf { it.isNotEmpty() }?.let { json.put("desc", it.toString().take(MAX_TEXT)) }
    info.paneTitle?.let { json.put("pane", it.toString()) }
    info.stateDescription?.let { json.put("state", it.toString()) }
    json.put("bounds", Rect().also(info::getBoundsInScreen).json())
    if (info.isClickable) json.put("clickable", true)
    if (info.isScrollable) json.put("scrollable", true)
    if (info.isSelected) json.put("selected", true)
    if (info.isChecked) json.put("checked", true)
    if (!info.isVisibleToUser) json.put("hidden", true)
    if (info.childCount > 0) {
        if (depth >= MAX_DEPTH || budget.nodes <= 0) {
            json.put("cut", info.childCount)
        } else {
            val children = JSONArray()
            for (i in 0 until info.childCount) info.getChild(i)?.let { children.put(node(it, depth + 1, budget)) }
            json.put("children", children)
        }
    }
    return json
}

private fun Rect.json() = JSONArray().put(left).put(top).put(right).put(bottom)

private fun windowType(type: Int) = when (type) {
    AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "keyboard"
    AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
    else -> "other"
}

private const val MAX_NODES = 4000
private const val MAX_DEPTH = 60
private const val MAX_TEXT = 300
