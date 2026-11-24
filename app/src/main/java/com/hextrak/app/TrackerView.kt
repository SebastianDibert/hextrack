package com.hextrak.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import com.hextrak.core.Grid
import com.hextrak.core.Pal
import com.hextrak.core.Tracker
import kotlin.math.floor
import kotlin.math.min

/**
 * Paints the 40x30 character grid scaled to fit the screen with integer-aligned cells.
 * On the RG477M (1280x960) each cell is exactly 32x32 pixels.
 */
@SuppressLint("ViewConstructor")
class TrackerView(context: Context, private val tracker: Tracker) : View(context), Choreographer.FrameCallback {
    private val grid = Grid()
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val text = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isSubpixelText = true
    }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private var running = false
    private var lastFrame = 0L
    private var lastCell = -1f
    private var glyphW = 0f
    private var baseline = 0f
    private val runBuf = CharArray(64)

    fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val now = SystemClock.uptimeMillis()
        tracker.update(now)
        // ~60 fps is plenty for a tracker UI and saves battery on the 120 Hz panel
        if (now - lastFrame >= 15) {
            lastFrame = now
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun setupFont(cell: Float) {
        if (cell == lastCell) return
        lastCell = cell
        text.textSize = cell * 0.92f
        text.textScaleX = 1f
        text.letterSpacing = 0f
        val adv = text.measureText("M")
        // widen glyphs a little for a chunky, pixel-like look, then pad to exactly one cell per char
        val scale = min(1.25f, cell * 0.80f / adv)
        text.textScaleX = scale
        glyphW = text.measureText("M")
        text.letterSpacing = (cell - glyphW) / text.textSize
        // calibrate so a run of N characters spans exactly N cells
        repeat(3) {
            val measured = text.measureText("MMMMMMMMMMMMMMMMMMMM")
            text.letterSpacing += (cell * 20f - measured) / (20f * text.textSize)
        }
        val fm = text.fontMetrics
        baseline = cell * 0.5f - (fm.ascent + fm.descent) * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        tracker.render(grid)
        val w = width.toFloat()
        val h = height.toFloat()
        val cell = floor(min(w / grid.cols, h / grid.rows))
        val ox = floor((w - cell * grid.cols) / 2f)
        val oy = floor((h - cell * grid.rows) / 2f)
        setupFont(cell)
        canvas.drawColor(Pal.BG)

        // background runs
        for (y in 0 until grid.rows) {
            var x = 0
            while (x < grid.cols) {
                val b = grid.bg[y * grid.cols + x]
                if (b == 0) { x++; continue }
                var e = x + 1
                while (e < grid.cols && grid.bg[y * grid.cols + e] == b) e++
                fill.color = b
                canvas.drawRect(ox + x * cell, oy + y * cell, ox + e * cell, oy + (y + 1) * cell, fill)
                x = e
            }
        }
        // shapes (meters, map, etc.)
        for (r in grid.rects) {
            fill.color = r.color
            canvas.drawRect(ox + r.x * cell, oy + r.y * cell, ox + (r.x + r.w) * cell, oy + (r.y + r.h) * cell, fill)
        }
        // text runs of equal colour
        for (y in 0 until grid.rows) {
            var x = 0
            val by = oy + y * cell + baseline
            while (x < grid.cols) {
                val o = y * grid.cols + x
                if (grid.ch[o] == ' ') { x++; continue }
                val c = grid.fg[o]
                var e = x
                var n = 0
                while (e < grid.cols && grid.fg[y * grid.cols + e] == c && n < runBuf.size) {
                    runBuf[n++] = grid.ch[y * grid.cols + e]
                    e++
                }
                while (n > 0 && runBuf[n - 1] == ' ') n--
                text.color = c
                canvas.drawText(runBuf, 0, n, ox + x * cell, by, text)
                x = e
            }
        }
        // poly lines (scope)
        stroke.strokeWidth = cell / 10f
        for (l in grid.lines) {
            if (l.pts.size < 4) continue
            path.reset()
            path.moveTo(ox + l.pts[0] * cell, oy + l.pts[1] * cell)
            var i = 2
            while (i < l.pts.size) { path.lineTo(ox + l.pts[i] * cell, oy + l.pts[i + 1] * cell); i += 2 }
            stroke.color = l.color
            canvas.drawPath(path, stroke)
        }
    }
}
