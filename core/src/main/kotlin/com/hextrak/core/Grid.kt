package com.hextrak.core

/** Colour palette (ARGB). */
object Pal {
    const val BG = 0xFF0B0D14.toInt()
    const val PANEL = 0xFF141826.toInt()
    const val BEAT = 0xFF171C2C.toInt()
    const val TEXT = 0xFFC9CEDB.toInt()
    const val DIM = 0xFF545B6E.toInt()
    const val FAINT = 0xFF2E3446.toInt()
    const val ACCENT = 0xFF3BE8C4.toInt()
    const val ACCENT2 = 0xFFFF5C8A.toInt()
    const val YELLOW = 0xFFFFD166.toInt()
    const val BLUE = 0xFF58A6FF.toInt()
    const val ORANGE = 0xFFFF9F43.toInt()
    const val CURSOR_BG = 0xFF3BE8C4.toInt()
    const val CURSOR_FG = 0xFF0B0D14.toInt()
    const val SEL_BG = 0xFF27466B.toInt()
    const val PLAY_BG = 0xFF3A1F33.toInt()
    const val HEADER_BG = 0xFF1B2133.toInt()
    const val METER = 0xFF3BE8C4.toInt()
    const val METER_HOT = 0xFFFF5C8A.toInt()
}

/**
 * A 40x30 character display, the same aspect as the RG477M's 4:3 panel.
 * Screens draw into it; the platform view paints it scaled to the screen.
 */
class Grid(val cols: Int = 40, val rows: Int = 30) {
    val ch = CharArray(cols * rows)
    val fg = IntArray(cols * rows)
    val bg = IntArray(cols * rows)

    class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int)
    class Line(val pts: FloatArray, val color: Int)

    val rects = ArrayList<Rect>()
    val lines = ArrayList<Line>()

    fun clear() {
        ch.fill(' ')
        fg.fill(Pal.TEXT)
        bg.fill(0)
        rects.clear()
        lines.clear()
    }

    fun text(x: Int, y: Int, s: String, color: Int = Pal.TEXT, back: Int = 0) {
        if (y < 0 || y >= rows) return
        for (i in s.indices) {
            val cx = x + i
            if (cx < 0 || cx >= cols) continue
            val o = y * cols + cx
            ch[o] = s[i]
            fg[o] = color
            if (back != 0) bg[o] = back
        }
    }

    fun fillBg(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y until y + h) for (xx in x until x + w) {
            if (xx in 0 until cols && yy in 0 until rows) bg[yy * cols + xx] = color
        }
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) { rects += Rect(x, y, w, h, color) }

    fun line(pts: FloatArray, color: Int) { lines += Line(pts, color) }

    fun rowString(y: Int): String = String(ch, y * cols, cols)
}
