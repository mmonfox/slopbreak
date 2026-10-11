package lt.neko.slopbreak.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import lt.neko.slopbreak.limit.INSTAGRAM
import lt.neko.slopbreak.limit.YOUTUBE

// A comments sheet hides the reel under it, so it inherits that reel's verdict.
internal class ShortVideos(private val service: AccessibilityService, private val app: String) {
    private var friendsReel: String? = null
    private var last = false
    private var idPackage: String? = null

    fun showing(): Boolean {
        val roots = service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .filter { it.packageName == app }
        if (idPackage == null) idPackage = roots.firstNotNullOfOrNull(::idPackageOf)
        val owners = listOfNotNull(app, if (app == INSTAGRAM) INSTAGRAM else YOUTUBE, idPackage).distinct()
        fun visible(view: String): List<AccessibilityNodeInfo> = roots.flatMap { root ->
            owners.flatMap { root.findAccessibilityNodeInfosByViewId("$it:id/$view") }
        }.filter { it.isVisibleToUser }
        last = if (app == INSTAGRAM) reels(::visible) else visible("reel_player_page_container").isNotEmpty() || visible("reel_recycler").isNotEmpty()
        return last
    }

    private fun reels(visible: (String) -> List<AccessibilityNodeInfo>): Boolean {
        if (visible("clips_viewer_view_pager").isEmpty()) {
            if (visible("bottom_sheet_container").isNotEmpty() && visible("layout_comment_thread_edittext_multiline").isNotEmpty()) return last
            friendsReel = null
            return false
        }
        val author = visible("clips_author_username").firstOrNull()?.let { it.text ?: it.contentDescription }?.toString()
        if (visible("sender_profile_pic").isNotEmpty()) {
            friendsReel = author
            return false
        }
        if (author != null && author == friendsReel) return false
        friendsReel = null
        return true
    }
}

// Patched builds may keep the original package in their ids.
private fun idPackageOf(root: AccessibilityNodeInfo): String? {
    val queue = ArrayDeque(listOf(root))
    var budget = ID_LOOKUPS
    while (queue.isNotEmpty() && budget-- > 0) {
        val node = queue.removeFirst()
        node.viewIdResourceName?.substringBefore(":id/", "")?.takeIf { it.isNotEmpty() && it != "android" }?.let { return it }
        for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
    }
    return null
}

private const val ID_LOOKUPS = 60
