package com.swadhin.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.swadhin.jarvis.nlu.Phonetic
import com.swadhin.jarvis.nlu.YouTubeResults
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        reviveJarvis()
    }

    private var lastRevive = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Android keeps accessibility services alive. If the system killed Jarvis in the background
        // (and the user didn't press STOP), bring it back.
        if (JarvisService.instance == null) reviveJarvis()
        // Numbers mode: a new screen opened -> renumber its buttons.
        if (overlayReady && overlay.mode == ScreenOverlay.Mode.NUMBERS &&
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != packageName
        ) {
            mainHandler.removeCallbacks(renumber)
            mainHandler.postDelayed(renumber, 700)
        }
    }

    private val renumber = Runnable { if (overlay.mode == ScreenOverlay.Mode.NUMBERS) showNumbers() }

    private fun reviveJarvis() {
        val now = System.currentTimeMillis()
        if (now - lastRevive < 30_000 || !Prefs.shouldRun(this) || JarvisService.instance != null) return
        lastRevive = now
        try {
            startForegroundService(Intent(this, JarvisService::class.java))
            JarvisService.log("Jarvis background me band ho gaya tha, wapas chalu kiya")
        } catch (e: Exception) {
            JarvisService.log("Jarvis apne aap chalu nahi ho saka: app khol ke START dabao (${e.javaClass.simpleName})")
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (overlayReady) try { overlay.hideAll() } catch (e: Exception) {}
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

    // ---------- WhatsApp ----------

    enum class WaState { NOT_OPEN, CHAT_READY, INVALID_NUMBER, OTHER }

    private fun waRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return if (root.packageName?.toString()?.startsWith("com.whatsapp") == true) root else null
    }

    private fun byId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? =
        try {
            root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/$id").firstOrNull { it.isVisibleToUser }
        } catch (e: Exception) { null }

    private val sendLabels = listOf("send", "भेजें", "भेजे", "भेजो", "ପଠାନ୍ତୁ", "পাঠান", "पाठवा", "அனுப்பு", "పంపు")

    private fun waSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        byId(root, "send")?.let { return it }
        var found: AccessibilityNodeInfo? = null
        walk(root) { n ->
            if (found == null && n.isVisibleToUser) {
                val d = n.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
                if (d.isNotEmpty() && sendLabels.any { d == it }) found = n
            }
        }
        return found
    }

    fun whatsAppState(): WaState {
        val root = waRoot() ?: return WaState.NOT_OPEN
        val text = readScreen().lowercase()
        if (text.contains("invalid") || text.contains("isn't on whatsapp") || text.contains("is not on whatsapp") ||
            text.contains("अमान्य") || text.contains("व्हाट्सएप पर नहीं") || text.contains("whatsapp पर नहीं")
        ) return WaState.INVALID_NUMBER
        return if (waSendButton(root) != null) WaState.CHAT_READY else WaState.OTHER
    }

    /** Text currently in the chat box ("" when empty / showing the hint). */
    fun whatsAppDraft(): String? {
        val root = waRoot() ?: return null
        val e = byId(root, "entry") ?: run {
            var f: AccessibilityNodeInfo? = null
            walk(root) { n -> if (f == null && n.isEditable && n.isVisibleToUser) f = n }
            f
        } ?: return null
        if (Build.VERSION.SDK_INT >= 26 && e.isShowingHintText) return ""
        return e.text?.toString().orEmpty()
    }

    /** Types into the chat box (for WhatsApp versions that ignore the pre-filled text). */
    fun whatsAppType(message: String): Boolean {
        val root = waRoot() ?: return false
        val e = byId(root, "entry") ?: return typeText(message, false)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        return e.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun whatsAppPressSend(): Boolean {
        val root = waRoot() ?: return false
        val b = waSendButton(root) ?: return false
        return clickNode(b)
    }

    // ---------- helpers ----------

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayReady = false
    val overlay: ScreenOverlay by lazy { overlayReady = true; ScreenOverlay(this) }

    /** Window / overlay calls must run on the main thread; Jarvis's commands run on a worker thread. */
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var r: Result<T>? = null
        mainHandler.post { r = runCatching(block); latch.countDown() }
        latch.await(3, TimeUnit.SECONDS)
        return (r ?: throw IllegalStateException("UI thread busy")).getOrThrow()
    }

    private fun screen(): Pair<Int, Int> = overlay.screenSize()

    /** All windows' roots: the app, plus dialogs / keyboard / system bars. Active window first. */
    private fun roots(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { out.add(it) }
        try {
            for (w in windows) w.root?.let { r -> if (out.none { it == r } && r.packageName != packageName) out.add(r) }
        } catch (e: Exception) {}
        return out
    }

    // ---------- Gestures: wait until Android has really performed them ----------

    private fun perform(g: GestureDescription, maxMs: Long): Boolean {
        val latch = CountDownLatch(1)
        var done = false
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { done = true; latch.countDown() }
            override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
        }
        val sent = onMain { dispatchGesture(g, cb, null) }
        if (!sent) return false
        if (Looper.myLooper() == Looper.getMainLooper()) return true
        latch.await(maxMs + 2000, TimeUnit.MILLISECONDS)
        return done
    }

    private fun point(x: Float, y: Float) = Path().apply { moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f)) }

    fun tapAt(x: Float, y: Float): Boolean =
        perform(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(point(x, y), 0, 60)).build(), 60)

    fun longPressAt(x: Float, y: Float): Boolean =
        perform(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(point(x, y), 0, 1000)).build(), 1000)

    fun doubleTapAt(x: Float, y: Float): Boolean = perform(
        GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(point(x, y), 0, 50))
            .addStroke(GestureDescription.StrokeDescription(point(x, y), 140, 50))
            .build(), 200
    )

    fun swipeLine(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 380): Boolean {
        val (w, h) = screen()
        val p = Path().apply {
            moveTo(x1.coerceIn(2f, w - 2f), y1.coerceIn(2f, h - 2f))
            lineTo(x2.coerceIn(2f, w - 2f), y2.coerceIn(2f, h - 2f))
        }
        return perform(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), ms)
    }

    // ---------- Find & tap by text ----------

    /** Best visible element for a spoken label: exact > starts-with > contains > sounds-like ("सब्सक्राइब" -> "Subscribe"). */
    fun findByText(text: String): AccessibilityNodeInfo? {
        val want = text.trim().lowercase()
        if (want.isEmpty()) return null
        val wantKey = Phonetic.key(want)
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0.0
        var bestTop = Int.MAX_VALUE
        val r = Rect()
        for (root in roots()) walk(root) { n ->
            if (!n.isVisibleToUser) return@walk
            val l = label(n).lowercase()
            if (l.isEmpty()) return@walk
            var score = when {
                l == want -> 1.0
                l.startsWith(want) -> 0.9
                want.length >= 3 && l.contains(want) -> 0.8
                else -> 0.0
            }
            if (score < 0.75 && wantKey.length >= 3 && l.length <= 60) {
                val k = Phonetic.key(l)
                val sim = maxOf(Phonetic.similarity(wantKey, k), k.split(' ').maxOfOrNull { Phonetic.similarity(wantKey, it) } ?: 0.0)
                if (sim >= 0.75) score = maxOf(score, sim * 0.85)
            }
            if (score <= 0.0) return@walk
            n.getBoundsInScreen(r)
            if (score > bestScore || (score == bestScore && r.top < bestTop)) {
                best = n; bestScore = score; bestTop = r.top
            }
        }
        return best
    }

    fun tapText(text: String): Boolean {
        val node = findByText(text) ?: return false
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

    fun center(node: AccessibilityNodeInfo): Pair<Float, Float> {
        val r = Rect()
        node.getBoundsInScreen(r)
        return Pair(r.exactCenterX(), r.exactCenterY())
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
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && pressEnter) pressEnter()
        return ok
    }

    /** Keyboard "Enter / Search / Go". */
    fun pressEnter(): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (f != null && Build.VERSION.SDK_INT >= 30 &&
            f.performAction(AccessibilityAction.ACTION_IME_ENTER.id)
        ) return true
        for (t in listOf("Search", "Go", "Send", "Done", "खोजें", "जाएं", "भेजें")) if (tapText(t)) return true
        return false
    }

    // ---------- Scroll ----------

    private fun preciseAction(dir: String): AccessibilityAction = when (dir) {
        "up" -> AccessibilityAction.ACTION_SCROLL_UP
        "left" -> AccessibilityAction.ACTION_SCROLL_LEFT
        "right" -> AccessibilityAction.ACTION_SCROLL_RIGHT
        else -> AccessibilityAction.ACTION_SCROLL_DOWN
    }

    /** The list / page that should scroll: under the point if given, else the biggest one on screen. */
    private fun scrollableFor(dir: String, x: Float?, y: Float?): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val vertical = dir == "up" || dir == "down"
        val precise = preciseAction(dir).id
        val all = ArrayList<Pair<AccessibilityNodeInfo, Rect>>()
        walk(root) { n ->
            if (n.isScrollable && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                if (!r.isEmpty) all.add(n to r)
            }
        }
        if (all.isEmpty()) return null
        var pool = all.filter { (n, _) -> n.actionList.any { it.id == precise } }
        if (pool.isEmpty()) pool = all.filter { (n, _) ->
            val cls = n.className?.toString().orEmpty()
            if (vertical) !cls.contains("ViewPager") && !cls.contains("HorizontalScrollView") else cls.contains("ViewPager") || cls.contains("Horizontal")
        }
        if (pool.isEmpty()) return null
        if (x != null && y != null) {
            pool.filter { it.second.contains(x.toInt(), y.toInt()) }
                .minByOrNull { it.second.width().toLong() * it.second.height() }?.let { return it.first }
        }
        return pool.maxByOrNull { it.second.width().toLong() * it.second.height() }?.first
    }

    /**
     * direction: down = neeche ka content dekhna, up = upar, left / right.
     * First the app's own scroll action (works in lists, web pages, settings), then a finger swipe as backup.
     */
    fun scroll(direction: String, atX: Float? = null, atY: Float? = null): Boolean {
        val dir = direction.lowercase()
        val node = scrollableFor(dir, atX, atY)
        if (node != null) {
            val precise = preciseAction(dir)
            if (node.actionList.any { it.id == precise.id } && node.performAction(precise.id)) return true
            if (dir == "up" || dir == "down") {
                val generic = if (dir == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                if (node.performAction(generic)) return true
            }
        }
        return swipe(dir, atX, atY)
    }

    /** Finger swipe. "down" moves the finger UP, so the content below comes into view. */
    fun swipe(direction: String, atX: Float? = null, atY: Float? = null): Boolean {
        val (wi, hi) = screen()
        val w = wi.toFloat()
        val h = hi.toFloat()
        val x = atX ?: (w / 2)
        val y = atY ?: (h / 2)
        val dy = h * 0.22f
        val dx = w * 0.35f
        return when (direction.lowercase()) {
            "up" -> swipeLine(x, y - dy, x, y + dy)
            "left" -> swipeLine(x - dx, y, x + dx, y)
            "right" -> swipeLine(x + dx, y, x - dx, y)
            else -> swipeLine(x, y + dy, x, y - dy)
        }
    }

    // ---------- Voice cursor / numbers / grid ----------

    fun cursorShow() = onMain { overlay.showCursor() }

    /** fraction = part of the screen to move (0.04 thoda, 0.1 normal, 0.25 bahut) */
    fun cursorMove(dir: String, fraction: Float) = onMain {
        overlay.showCursor()
        val (w, h) = screen()
        when (dir) {
            "up" -> overlay.moveCursorBy(0f, -h * fraction)
            "down" -> overlay.moveCursorBy(0f, h * fraction)
            "left" -> overlay.moveCursorBy(-w * fraction, 0f)
            else -> overlay.moveCursorBy(w * fraction, 0f)
        }
    }

    fun cursorTo(x: Float, y: Float) = onMain { overlay.showCursor(); overlay.moveCursorTo(x, y) }

    fun cursorPos(): Pair<Float, Float>? = onMain { if (overlay.cursorVisible) Pair(overlay.cx, overlay.cy) else null }

    fun overlayMode(): ScreenOverlay.Mode = if (!overlayReady) ScreenOverlay.Mode.NONE else onMain { overlay.mode }

    fun cursorVisible(): Boolean = overlayReady && onMain { overlay.cursorVisible }

    val overlayActive: Boolean get() = overlayReady && overlay.active

    fun hideOverlay() = onMain { overlay.hideAll() }

    private var numbered: List<AccessibilityNodeInfo> = emptyList()

    /** Puts a number on every button / link / text box. Returns how many. */
    fun showNumbers(): Int = onMain {
        val (w, h) = screen()
        val d = resources.displayMetrics.density
        val seen = HashSet<String>()
        val items = ArrayList<Pair<Rect, AccessibilityNodeInfo>>()
        for (root in roots()) walk(root) { n ->
            if (n.isVisibleToUser && (n.isClickable || n.isLongClickable || n.isEditable || n.isCheckable)) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() > 8 * d && r.height() > 8 * d && r.intersect(0, 0, w, h) && seen.add(r.flattenToString())) items.add(r to n)
            }
        }
        val row = (24 * d).toInt().coerceAtLeast(1)
        val sorted = items.sortedWith(compareBy({ it.first.top / row }, { it.first.left })).take(99)
        numbered = sorted.map { it.second }
        overlay.showNumbers(sorted.map { it.first })
        sorted.size
    }

    fun showGrid(): Int = onMain { overlay.showGrid(); overlay.gridRows * overlay.gridCols }

    /** Acts on number n of the current numbers / grid. */
    fun pickNumber(n: Int, how: String = "click"): Boolean {
        return when (overlayMode()) {
            ScreenOverlay.Mode.NUMBERS -> {
                val node = numbered.getOrNull(n - 1) ?: return false
                val (x, y) = center(node)
                when (how) {
                    "long" -> longPressAt(x, y)
                    "double" -> doubleTapAt(x, y)
                    "cursor" -> { cursorTo(x, y); true }
                    else -> if (node.isEditable) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) || clickNode(node) else clickNode(node)
                }
            }
            ScreenOverlay.Mode.GRID -> {
                val (x, y) = onMain { overlay.gridCenter(n) } ?: return false
                if (cursorVisible()) cursorTo(x, y)
                when (how) {
                    "long" -> longPressAt(x, y)
                    "double" -> doubleTapAt(x, y)
                    "cursor" -> { cursorTo(x, y); true }
                    else -> tapAt(x, y)
                }
            }
            else -> false
        }
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
