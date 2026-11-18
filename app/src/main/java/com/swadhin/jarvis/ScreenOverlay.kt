package com.swadhin.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.WindowManager

/**
 * Drawn on top of every app by the Accessibility service (TYPE_ACCESSIBILITY_OVERLAY: no extra permission).
 * It never takes touches (FLAG_NOT_TOUCHABLE), so taps / swipes Jarvis performs go to the app underneath.
 *
 * Three modes, all controlled by voice:
 *  - CURSOR : a mouse pointer you move with "upar / neeche / left / right" and "click karo"
 *  - NUMBERS: a number on every button / link / box ("5 dabao")
 *  - GRID   : the screen split into numbered boxes ("7 par click karo"), for apps without labels (games, maps…)
 */
class ScreenOverlay(private val ctx: Context) {

    enum class Mode { NONE, CURSOR, NUMBERS, GRID }

    var mode = Mode.NONE
        private set
    var cursorVisible = false
        private set

    /** Cursor position in screen pixels. */
    var cx = 0f
        private set
    var cy = 0f
        private set

    /** Number labels (screen coordinates), index 0 = number 1. */
    var labels: List<Rect> = emptyList()
        private set

    val gridRows = 6
    val gridCols = 4

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: OverlayView? = null

    fun screenSize(): Pair<Int, Int> {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun ensureView() {
        if (view != null) return
        val v = OverlayView(ctx)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(v, lp)
        view = v
    }

    private fun refresh() {
        if (mode == Mode.NONE && !cursorVisible) {
            view?.let { try { wm.removeView(it) } catch (e: Exception) {} }
            view = null
            return
        }
        ensureView()
        view?.invalidate()
    }

    // ---- cursor ----

    fun showCursor() {
        if (!cursorVisible) {
            val (w, h) = screenSize()
            if (cx == 0f && cy == 0f) { cx = w / 2f; cy = h / 2f }
        }
        cursorVisible = true
        if (mode == Mode.NONE) mode = Mode.CURSOR
        refresh()
    }

    fun moveCursorBy(dx: Float, dy: Float) {
        val (w, h) = screenSize()
        cx = (cx + dx).coerceIn(4f, w - 4f)
        cy = (cy + dy).coerceIn(4f, h - 4f)
        refresh()
    }

    fun moveCursorTo(x: Float, y: Float) {
        val (w, h) = screenSize()
        cx = x.coerceIn(4f, w - 4f)
        cy = y.coerceIn(4f, h - 4f)
        refresh()
    }

    // ---- numbers / grid ----

    fun showNumbers(rects: List<Rect>) {
        labels = rects
        mode = Mode.NUMBERS
        refresh()
    }

    fun showGrid() {
        mode = Mode.GRID
        refresh()
    }

    /** Centre of grid box n (1-based), screen coordinates. */
    fun gridCenter(n: Int): Pair<Float, Float>? {
        if (n !in 1..gridRows * gridCols) return null
        val (w, h) = screenSize()
        val r = (n - 1) / gridCols
        val c = (n - 1) % gridCols
        return Pair((c + 0.5f) * w / gridCols, (r + 0.5f) * h / gridRows)
    }

    fun hideAll() {
        mode = Mode.NONE
        cursorVisible = false
        labels = emptyList()
        refresh()
    }

    fun hideLabels() {
        labels = emptyList()
        mode = if (cursorVisible) Mode.CURSOR else Mode.NONE
        refresh()
    }

    val active: Boolean get() = mode != Mode.NONE || cursorVisible

    // ---- drawing ----

    private inner class OverlayView(c: Context) : View(c) {
        private val d = c.resources.displayMetrics.density
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.parseColor("#22D3EE") }
        private val ringOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6 * d; color = Color.parseColor("#AA000000") }
        private val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
        private val arrowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = Color.BLACK }
        private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E60B1220") }
        private val badgeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = Color.parseColor("#22D3EE") }
        private val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1 * d; color = Color.parseColor("#8822D3EE") }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13 * d; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        private val gridLine = Paint().apply { color = Color.parseColor("#9922D3EE"); strokeWidth = 1.5f * d }
        private val gridNum = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CCFFFFFF"); textSize = 26 * d; isFakeBoldText = true; textAlign = Paint.Align.CENTER; setShadowLayer(4 * d, 0f, 0f, Color.BLACK) }
        private val loc = IntArray(2)

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(loc)
            val ox = loc[0].toFloat()
            val oy = loc[1].toFloat()
            when (mode) {
                Mode.NUMBERS -> labels.forEachIndexed { i, r ->
                    canvas.drawRect(r.left - ox, r.top - oy, r.right - ox, r.bottom - oy, boxEdge)
                    val text = (i + 1).toString()
                    val bw = num.measureText(text) + 10 * d
                    val bh = 18 * d
                    val left = (r.left - ox).coerceAtLeast(0f)
                    val top = (r.top - oy).coerceAtLeast(0f)
                    val rr = RectF(left, top, left + bw, top + bh)
                    canvas.drawRoundRect(rr, 5 * d, 5 * d, badge)
                    canvas.drawRoundRect(rr, 5 * d, 5 * d, badgeEdge)
                    canvas.drawText(text, rr.centerX(), rr.bottom - 4.5f * d, num)
                }
                Mode.GRID -> {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    for (c in 1 until gridCols) canvas.drawLine(c * w / gridCols, 0f, c * w / gridCols, h, gridLine)
                    for (r in 1 until gridRows) canvas.drawLine(0f, r * h / gridRows, w, r * h / gridRows, gridLine)
                    for (n in 1..gridRows * gridCols) {
                        val (x, y) = gridCenter(n) ?: continue
                        canvas.drawText(n.toString(), x - ox, y - oy + 9 * d, gridNum)
                    }
                }
                else -> {}
            }
            if (cursorVisible) {
                val x = cx - ox
                val y = cy - oy
                canvas.drawCircle(x, y, 16 * d, ringOuter)
                canvas.drawCircle(x, y, 16 * d, ring)
                // arrow pointer whose tip is exactly the click point
                val p = Path().apply {
                    moveTo(x, y)
                    lineTo(x, y + 22 * d)
                    lineTo(x + 6 * d, y + 16 * d)
                    lineTo(x + 11 * d, y + 26 * d)
                    lineTo(x + 15 * d, y + 24 * d)
                    lineTo(x + 10 * d, y + 14 * d)
                    lineTo(x + 17 * d, y + 14 * d)
                    close()
                }
                canvas.drawPath(p, arrowFill)
                canvas.drawPath(p, arrowStroke)
            }
        }
    }
}
