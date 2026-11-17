package com.swadhin.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.swadhin.jarvis.nlu.YouTubeResults

/**
 * Phone control ka engine: screen padhna, tap, type, scroll, Home/Back waghera.
 * Settings > Accessibility me "Jarvis Phone Control" on karna padta hai.
 */
class JarvisAccessibility : AccessibilityService() {

    companion object {
        @Volatile
        var instance: JarvisAccessibility? = null
    }

    /** One video in YouTube's search results, as seen through accessibility. */
    class YtResult(val item: YouTubeResults.Item, val node: AccessibilityNodeInfo, val top: Int)

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ---------- Screen padhna ----------

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "(screen khali hai ya padhi nahi ja saki)"
        val sb = StringBuilder()
        sb.append("App: ").append(root.packageName ?: "?").append('\n')
        var count = 0
        walk(root) { n ->
            if (count < 160 && sb.length < 5000 && n.isVisibleToUser) {
                val t = label(n)
                if (t.isNotEmpty()) {
                    val tag = when {
                        n.isEditable -> "[input] "
                        n.isClickable || n.isCheckable -> "[button] "
                        else -> ""
                    }
                    val state = if (n.isCheckable) (if (n.isChecked) " (ON)" else " (OFF)") else ""
                    sb.append(tag).append(t.take(120)).append(state).append('\n')
                    count++
                }
            }
        }
        return sb.toString()
    }

    private fun label(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return n.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun walk(n: AccessibilityNodeInfo?, f: (AccessibilityNodeInfo) -> Unit) {
        if (n == null) return
        f(n)
        for (i in 0 until n.childCount) walk(n.getChild(i), f)
    }

    // ---------- YouTube search results ----------

    /**
     * Video results currently visible in the YouTube app, top to bottom, without ads and Shorts.
     * Returns null when YouTube is not the foreground app.
     */
    fun youtubeResults(): List<YtResult>? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != "com.google.android.youtube") return null
        val out = ArrayList<YtResult>()
        val loose = ArrayList<YtResult>()
        walk(root) { n ->
            if (n.isVisibleToUser) {
                val d = n.contentDescription?.toString()
                if (!d.isNullOrBlank()) {
                    val r = Rect()
                    n.getBoundsInScreen(r)
                    val item = YouTubeResults.parse(d)
                    if (item != null && !item.isAd && !item.isShort && r.height() >= 40) {
                        out.add(YtResult(item, n, r.top))
                    } else if (item == null && r.height() >= 120 && d.length > 25 &&
                        (d.contains("views", true) || d.contains("व्यू") || d.contains("बार देखा"))
                    ) {
                        loose.add(YtResult(YouTubeResults.Item(d.substringBefore(" - ").substringBefore(", "), "", false, false), n, r.top))
                    }
                }
            }
        }
        val list = if (out.isNotEmpty()) out else loose
        return list.distinctBy { it.item.title }.sortedBy { it.top }
    }

    fun tapNode(node: AccessibilityNodeInfo): Boolean = clickNode(node)

    // ---------- Tap ----------

    fun tapText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val want = text.trim().lowercase()
        if (want.isEmpty()) return false
        var exact: AccessibilityNodeInfo? = null
        var partial: AccessibilityNodeInfo? = null
        walk(root) { n ->
            if (n.isVisibleToUser) {
                val l = label(n).lowercase()
                if (l.isNotEmpty()) {
                    if (exact == null && l == want) exact = n
                    else if (partial == null && l.contains(want)) partial = n
                }
            }
        }
        val node = exact ?: partial ?: return false
        return clickNode(node)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tapAt(r.exactCenterX(), r.exactCenterY())
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build()
        return dispatchGesture(g, null, null)
    }

    // ---------- Type ----------

    fun typeText(text: String, pressEnter: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        var found: AccessibilityNodeInfo? = null
        if (focused?.isEditable == true) {
            found = focused
        } else {
            walk(root) { n -> if (found == null && n.isEditable && n.isVisibleToUser) found = n }
        }
        val node = found ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && pressEnter && Build.VERSION.SDK_INT >= 30) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return ok
    }

    // ---------- Scroll / swipe ----------

    /** direction: down = aage ka content dekhna, up = wapas upar, left / right */
    fun swipe(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val p = Path()
        when (direction.lowercase()) {
            "up" -> { p.moveTo(w / 2, h * 0.3f); p.lineTo(w / 2, h * 0.75f) }
            "left" -> { p.moveTo(w * 0.2f, h / 2); p.lineTo(w * 0.85f, h / 2) }
            "right" -> { p.moveTo(w * 0.85f, h / 2); p.lineTo(w * 0.2f, h / 2) }
            else -> { p.moveTo(w / 2, h * 0.75f); p.lineTo(w / 2, h * 0.3f) }
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 300))
            .build()
        return dispatchGesture(g, null, null)
    }

    // ---------- System buttons ----------

    fun global(action: String): Boolean {
        val id = when (action.lowercase()) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_menu" -> GLOBAL_ACTION_POWER_DIALOG
            "lock_screen" -> GLOBAL_ACTION_LOCK_SCREEN
            "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> return false
        }
        return performGlobalAction(id)
    }
}
