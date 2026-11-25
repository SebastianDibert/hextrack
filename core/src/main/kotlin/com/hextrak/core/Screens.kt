package com.hextrak.core

import kotlin.math.log10
import kotlin.math.max

abstract class Screen(val app: Tracker) {
    abstract val name: String
    open val showSidePanel = true
    val song: Song get() = app.song

    abstract fun draw(g: Grid)
    open fun move(dx: Int, dy: Int) {}
    open fun edit(dx: Int, dy: Int) {}
    open fun tapA(double: Boolean) {}
    open fun tapB() {}
    open fun delete() {}
    open fun clone() {}
    open fun bDir(dx: Int, dy: Int) {}
    open fun page(d: Int) {}
    open fun trackStep(d: Int) { app.curTrack = (app.curTrack + d).coerceIn(0, K.TRACKS - 1) }
    open fun audition(on: Boolean) {}
    open fun info(): String = ""
    open fun hints(): String = "SEL+PAD:SCREENS  START:PLAY  SEL+Y:HELP"
    open fun onEnter(from: Screen) {}
    open fun cursorKey(): String = ""
    open fun tick() {}

    protected fun cellColors(isCursor: Boolean, isSel: Boolean, fg: Int, bg: Int): Pair<Int, Int> = when {
        isCursor -> Pal.CURSOR_FG to Pal.CURSOR_BG
        isSel -> Pal.TEXT to Pal.SEL_BG
        else -> fg to bg
    }
}

// =====================================================================================================
// Grid based sequencer screens
// =====================================================================================================
abstract class GridScreen(app: Tracker) : Screen(app) {
    abstract val numCols: Int
    abstract val numRows: Int
    open var cx = 0
    open var cy = 0
    private var insertKey = ""

    abstract fun kind(col: Int): Kind
    abstract fun get(col: Int, row: Int): Int
    abstract fun set(col: Int, row: Int, v: Int)
    open fun onEdited(col: Int, row: Int) {}
    open fun newItem(col: Int, row: Int): Boolean = false
    fun clearCell(col: Int, row: Int) { val k = kind(col); set(col, row, if (k.nullable) k.empty else 0) }

    override fun cursorKey() = "$cx,$cy"

    override fun move(dx: Int, dy: Int) {
        cx = (cx + dx).coerceIn(0, numCols - 1)
        cy = (cy + dy).coerceIn(0, numRows - 1)
    }

    override fun edit(dx: Int, dy: Int) {
        val k = kind(cx)
        var v = get(cx, cy)
        v = when {
            k.nullable && v == k.empty -> app.lastVal(k)
            k == Kind.NOTE && v == K.NOTE_OFF -> app.lastVal(k)
            else -> (v + dx * k.fine + dy * k.coarse).coerceIn(k.min, k.max)
        }
        set(cx, cy, v)
        app.setLast(k, v)
        onEdited(cx, cy)
    }

    override fun tapA(double: Boolean) {
        val k = kind(cx)
        val v = get(cx, cy)
        val key = "$cx,$cy"
        if (double && insertKey == key) {
            insertKey = ""
            if (newItem(cx, cy)) return
        }
        if (k.nullable && v == k.empty) {
            val nv = app.lastVal(k)
            set(cx, cy, nv)
            insertKey = key
            onEdited(cx, cy)
        } else {
            insertKey = ""
            app.setLast(k, v)
            onEdited(cx, cy)
        }
    }

    override fun delete() {
        val k = kind(cx)
        val v = get(cx, cy)
        if (k == Kind.NOTE && v < 0) set(cx, cy, K.NOTE_OFF) else clearCell(cx, cy)
    }

    protected fun inSel(col: Int, row: Int): Boolean {
        val r = app.selectionRect() ?: return false
        return col in r[0]..r[2] && row in r[1]..r[3]
    }

    protected fun drawCell(g: Grid, x: Int, y: Int, text: String, col: Int, row: Int, fg: Int, bg: Int = 0) {
        val (f, b) = cellColors(col == cx && row == cy, inSel(col, row), fg, bg)
        g.text(x, y, text, f, b)
    }
}

// ----------------------------------------------------------------------------------------------- SONG
class SongScreen(app: Tracker) : GridScreen(app) {
    override val name = "SONG"
    override val numCols = K.TRACKS
    override val numRows = K.ROWS
    override var cx: Int
        get() = app.curTrack
        set(v) { app.curTrack = v }
    override var cy: Int
        get() = app.songRow
        set(v) { app.songRow = v }
    var top = 0
    private val vis = 22

    override fun kind(col: Int) = Kind.CHAIN
    override fun get(col: Int, row: Int) = song.song[col][row]
    override fun set(col: Int, row: Int, v: Int) { song.song[col][row] = v; app.pushSongCell(row, col) }
    override fun onEdited(col: Int, row: Int) { val c = get(col, row); if (c >= 0) app.viewChain = c }

    override fun newItem(col: Int, row: Int): Boolean {
        val c = song.freeChain()
        if (c < 0) { app.flash("NO FREE CHAINS"); return true }
        set(col, row, c); app.setLast(Kind.CHAIN, c); app.viewChain = c
        app.flash("NEW CHAIN ${HEX2[c]}", 1000)
        return true
    }

    override fun clone() {
        val c = get(cx, cy)
        if (c < 0) return
        val n = song.freeChain()
        if (n < 0) { app.flash("NO FREE CHAINS"); return }
        song.chains[n].copyFrom(song.chains[c]); app.pushChain(n)
        set(cx, cy, n); app.setLast(Kind.CHAIN, n); app.viewChain = n
        app.flash("CLONED CHAIN ${HEX2[c]} -> ${HEX2[n]}", 1500)
    }

    override fun page(d: Int) { move(0, d * 16) }
    override fun bDir(dx: Int, dy: Int) { move(dx, -dy * 16) }
    override fun trackStep(d: Int) { move(d, 0) }

    override fun draw(g: Grid) {
        if (cy < top) top = cy
        if (cy >= top + vis) top = cy - vis + 1
        g.text(0, 1, "SONG  ${song.name}", Pal.TEXT)
        g.text(18, 1, "TRACK ${cx + 1}", Pal.DIM)
        for (t in 0 until K.TRACKS) {
            val muted = app.trackMuted(t)
            g.text(4 + t * 3, 2, if (muted) "M" else "${t + 1}", if (muted) Pal.ACCENT2 else if (t == cx) Pal.ACCENT else Pal.DIM)
        }
        for (i in 0 until vis) {
            val r = top + i
            if (r >= K.ROWS) break
            val y = 3 + i
            var rowPlay = false
            for (t in 0 until K.TRACKS) {
                val b = t * K.ST_PER_TRACK
                val isPlay = app.playing && app.st[b] == 1 && app.st[b + 1] == r && app.st[K.ST_GLOBAL + 1] == K.PLAY_SONG
                if (isPlay) rowPlay = true
                val c = song.song[t][r]
                val fg = if (c < 0) Pal.FAINT else if (isPlay) Pal.ACCENT2 else Pal.TEXT
                drawCell(g, 4 + t * 3, y, if (c < 0) "--" else HEX2[c], t, r, fg, if (isPlay) Pal.PLAY_BG else 0)
            }
            if (app.playing && app.st[K.ST_GLOBAL + 5] == r) rowPlay = true
            g.text(1, y, HEX2[r], if (r == cy) Pal.ACCENT else if (r % 4 == 0) Pal.TEXT else Pal.DIM)
            if (rowPlay) g.text(0, y, ">", Pal.ACCENT2)
        }
    }

    override fun info(): String {
        val c = get(cx, cy)
        if (c < 0) return "Empty. A: insert chain ${HEX2[app.lastVal(Kind.CHAIN)]}, A A: new chain"
        val ch = song.chains[c]
        val ps = (0 until K.STEPS).filter { ch.phrase[it] >= 0 }.joinToString(" ") { HEX2[ch.phrase[it]] }
        return "CHAIN ${HEX2[c]}: $ps"
    }

    override fun hints() = "A:INS AA:NEW X+A:CLONE B+A:DEL SEL+B:SEL"
}

// ----------------------------------------------------------------------------------------------- CHAIN
class ChainScreen(app: Tracker) : GridScreen(app) {
    override val name = "CHAIN"
    override val numCols = 2
    override val numRows = K.STEPS
    private val chain get() = song.chains[app.viewChain]

    override fun kind(col: Int) = if (col == 0) Kind.PHRASE else Kind.TRANSP
    override fun get(col: Int, row: Int) = if (col == 0) chain.phrase[row] else chain.transp[row]
    override fun set(col: Int, row: Int, v: Int) {
        if (col == 0) chain.phrase[row] = v else chain.transp[row] = v
        app.pushChainStep(app.viewChain, row)
    }
    override fun onEdited(col: Int, row: Int) { val p = chain.phrase[row]; if (p >= 0) app.viewPhrase = p }

    override fun onEnter(from: Screen) {
        if (from === app.songScreen || from !in app.middle) {
            val c = song.song[app.curTrack][app.songRow]
            if (c >= 0) app.viewChain = c
        }
    }

    override fun newItem(col: Int, row: Int): Boolean {
        if (col != 0) return false
        val p = song.freePhrase()
        if (p < 0) { app.flash("NO FREE PHRASES"); return true }
        set(0, row, p); app.setLast(Kind.PHRASE, p); app.viewPhrase = p
        app.flash("NEW PHRASE ${HEX2[p]}", 1000)
        return true
    }

    override fun clone() {
        if (cx != 0) return
        val p = get(0, cy)
        if (p < 0) return
        val n = song.freePhrase()
        if (n < 0) { app.flash("NO FREE PHRASES"); return }
        song.phrases[n].copyFrom(song.phrases[p]); app.pushPhrase(n)
        set(0, cy, n); app.setLast(Kind.PHRASE, n); app.viewPhrase = n
        app.flash("CLONED PHRASE ${HEX2[p]} -> ${HEX2[n]}", 1500)
    }

    override fun bDir(dx: Int, dy: Int) {
        app.viewChain = (app.viewChain + dx * 16 + dy).coerceIn(0, K.CHAINS - 1)
    }

    override fun trackStep(d: Int) {
        app.curTrack = (app.curTrack + d).coerceIn(0, K.TRACKS - 1)
        val c = song.song[app.curTrack][app.songRow]
        if (c >= 0) app.viewChain = c
    }

    override fun page(d: Int) { move(0, d * 4) }

    override fun draw(g: Grid) {
        val inSong = song.song[app.curTrack][app.songRow] == app.viewChain
        g.text(0, 1, "CHAIN ${HEX2[app.viewChain]}", Pal.TEXT)
        g.text(10, 1, "TRACK ${app.curTrack + 1}", Pal.DIM)
        if (!inSong) g.text(19, 1, "(not at cursor)", Pal.FAINT)
        g.text(3, 2, "PH TRN  PHRASE PREVIEW", Pal.DIM)
        var playStep = -1
        if (app.playing) for (t in 0 until K.TRACKS) {
            val b = t * K.ST_PER_TRACK
            if (app.st[b] == 1 && app.st[b + 2] == app.viewChain && (t == app.curTrack || playStep < 0)) playStep = app.st[b + 3]
        }
        for (r in 0 until K.STEPS) {
            val y = 3 + r
            val beat = if (r % 4 == 0) Pal.BEAT else 0
            if (beat != 0) g.fillBg(0, y, 31, 1, beat)
            val isPlay = r == playStep
            g.text(1, y, HEX1[r], if (isPlay) Pal.ACCENT2 else if (r == cy) Pal.ACCENT else Pal.DIM)
            if (isPlay) g.text(0, y, ">", Pal.ACCENT2)
            val p = chain.phrase[r]
            drawCell(g, 3, y, if (p < 0) "--" else HEX2[p], 0, r, if (p < 0) Pal.FAINT else Pal.TEXT, beat)
            val tr = chain.transp[r]
            val ts = (if (tr >= 0) "+" else "-") + "%02d".format(kotlin.math.abs(tr))
            drawCell(g, 6, y, ts, 1, r, if (tr == 0) Pal.DIM else Pal.YELLOW, beat)
            if (p >= 0) {
                val ph = song.phrases[p]
                val sb = StringBuilder()
                for (s in 0 until K.STEPS) {
                    val n = ph.note[s]
                    sb.append(if (n == K.NOTE_OFF) 'x' else if (n >= 0) 'o' else '.')
                }
                g.text(11, y, sb.toString(), Pal.FAINT)
            }
        }
    }

    override fun tick() {
        if (!app.follow || !app.playing || app.st[K.ST_GLOBAL + 1] != K.PLAY_SONG) return
        val b = app.curTrack * K.ST_PER_TRACK
        if (app.st[b] == 1 && app.st[b + 2] >= 0) app.viewChain = app.st[b + 2]
    }

    override fun info(): String = if (cx == 0) "Phrase to play at this step. A A: new phrase, X+A: clone"
    else "Transpose this phrase step in semitones"

    override fun hints() = "B+PAD:CHAIN#  L1/R1:TRACK  START:LOOP"
}

// ----------------------------------------------------------------------------------------------- PHRASE
class PhraseScreen(app: Tracker) : GridScreen(app) {
    override val name = "PHRASE"
    override val numCols = 9
    override val numRows = K.STEPS
    private val phrase get() = song.phrases[app.viewPhrase]
    private val colX = intArrayOf(3, 7, 10, 13, 16, 19, 22, 25, 28)

    override fun kind(col: Int) = when (col) {
        0 -> Kind.NOTE; 1 -> Kind.INSTR; 2 -> Kind.VOL; 3, 5, 7 -> Kind.CMD; else -> Kind.FXVAL
    }
    override fun get(col: Int, row: Int): Int = when (col) {
        0 -> phrase.note[row]; 1 -> phrase.inst[row]; 2 -> phrase.vol[row]
        3, 5, 7 -> phrase.fx[(col - 3) / 2][row]
        else -> phrase.fxv[(col - 4) / 2][row]
    }
    override fun set(col: Int, row: Int, v: Int) {
        val ph = phrase
        when (col) {
            0 -> { ph.note[row] = v; if (v >= 0 && v != K.NOTE_OFF && ph.inst[row] < 0) ph.inst[row] = app.lastVal(Kind.INSTR) }
            1 -> ph.inst[row] = v
            2 -> ph.vol[row] = v
            3, 5, 7 -> { val k = (col - 3) / 2; ph.fx[k][row] = v; if (v == 0) ph.fxv[k][row] = 0 }
            else -> ph.fxv[(col - 4) / 2][row] = v
        }
        app.pushPhraseStep(app.viewPhrase, row)
    }

    override fun onEdited(col: Int, row: Int) {
        val ph = phrase
        when (col) {
            0 -> if (ph.note[row] in 0..K.MAX_NOTE) app.auditionNote(ph.inst[row], ph.note[row], ph.vol[row])
            1 -> if (ph.inst[row] >= 0) app.viewInstr = ph.inst[row]
        }
    }

    override fun onEnter(from: Screen) {
        if (from === app.chainScreen || from !in app.middle) {
            val p = song.chains[app.viewChain].phrase[app.chainScreen.cy]
            if (p >= 0) app.viewPhrase = p
        }
    }

    override fun newItem(col: Int, row: Int): Boolean {
        if (col != 1) return false
        val i = song.freeInstr()
        if (i < 0) { app.flash("NO FREE INSTRUMENTS"); return true }
        set(1, row, i); app.setLast(Kind.INSTR, i); app.viewInstr = i
        app.flash("NEW INSTRUMENT ${HEX2[i]}", 1000)
        return true
    }

    override fun clone() {
        if (cx != 1) return
        val i = get(1, cy)
        if (i < 0) return
        val n = song.freeInstr()
        if (n < 0) { app.flash("NO FREE INSTRUMENTS"); return }
        song.instruments[n].copyFrom(song.instruments[i]); app.pushInstr(n)
        set(1, cy, n); app.setLast(Kind.INSTR, n); app.viewInstr = n
        app.flash("CLONED INSTR ${HEX2[i]} -> ${HEX2[n]}", 1500)
    }

    override fun bDir(dx: Int, dy: Int) { app.viewPhrase = (app.viewPhrase + dx * 16 + dy).coerceIn(0, K.PHRASES - 1) }
    override fun page(d: Int) { move(0, d * 4) }

    override fun trackStep(d: Int) {
        app.curTrack = (app.curTrack + d).coerceIn(0, K.TRACKS - 1)
        val c = song.song[app.curTrack][app.songRow]
        if (c >= 0) {
            app.viewChain = c
            val p = song.chains[c].phrase[app.chainScreen.cy]
            if (p >= 0) app.viewPhrase = p
        }
    }

    override fun audition(on: Boolean) {
        if (on) {
            val ph = phrase
            var n = ph.note[cy]
            if (n < 0 || n == K.NOTE_OFF) n = app.lastVal(Kind.NOTE)
            val i = if (ph.inst[cy] >= 0) ph.inst[cy] else app.lastVal(Kind.INSTR)
            app.auditionNote(i, n, ph.vol[cy], hold = true)
        } else app.auditionRelease()
    }

    override fun draw(g: Grid) {
        g.text(0, 1, "PHRASE ${HEX2[app.viewPhrase]}", Pal.TEXT)
        g.text(11, 1, "TRACK ${app.curTrack + 1}  CHAIN ${HEX2[app.viewChain]}", Pal.DIM)
        g.text(3, 2, "NOT IN VL FX1   FX2   FX3", Pal.DIM)
        var playStep = -1
        if (app.playing) for (t in 0 until K.TRACKS) {
            val b = t * K.ST_PER_TRACK
            if (app.st[b] == 1 && app.st[b + 4] == app.viewPhrase && (t == app.curTrack || playStep < 0)) playStep = app.st[b + 5]
        }
        val ph = phrase
        for (r in 0 until K.STEPS) {
            val y = 3 + r
            val beat = if (r % 4 == 0) Pal.BEAT else 0
            if (beat != 0) g.fillBg(0, y, 31, 1, beat)
            val isPlay = r == playStep
            if (isPlay) { g.fillBg(0, y, 31, 1, Pal.PLAY_BG); g.text(0, y, ">", Pal.ACCENT2) }
            val bg = if (isPlay) Pal.PLAY_BG else beat
            g.text(1, y, HEX1[r], if (isPlay) Pal.ACCENT2 else if (r == cy) Pal.ACCENT else Pal.DIM)
            val n = ph.note[r]
            drawCell(g, colX[0], y, Defs.noteName(n), 0, r, if (n < 0) Pal.FAINT else if (n == K.NOTE_OFF) Pal.ACCENT2 else Pal.TEXT, bg)
            val ins = ph.inst[r]
            drawCell(g, colX[1], y, if (ins < 0) "--" else HEX2[ins], 1, r, if (ins < 0) Pal.FAINT else Pal.BLUE, bg)
            val v = ph.vol[r]
            drawCell(g, colX[2], y, if (v < 0) "--" else HEX2[v], 2, r, if (v < 0) Pal.FAINT else Pal.YELLOW, bg)
            for (k in 0 until 3) {
                val c = ph.fx[k][r]
                drawCell(g, colX[3 + k * 2], y, Defs.FX_NAMES[c.coerceIn(0, K.NUM_FX - 1)], 3 + k * 2, r, if (c == 0) Pal.FAINT else Pal.ORANGE, bg)
                drawCell(g, colX[4 + k * 2], y, if (c == 0) "--" else HEX2[ph.fxv[k][r] and 255], 4 + k * 2, r, if (c == 0) Pal.FAINT else Pal.TEXT, bg)
            }
        }
        // instrument of current row
        val ci = if (ph.inst[cy] >= 0) ph.inst[cy] else app.lastVal(Kind.INSTR)
        val ins = song.instruments[ci]
        g.text(0, 20, "INSTR ${HEX2[ci]} ", Pal.DIM)
        g.text(9, 20, ins.name, Pal.BLUE)
        g.text(18, 20, Defs.ENGINE_NAMES[ins.engine.coerceIn(0, K.NUM_ENGINES - 1)], Pal.DIM)
        g.text(0, 22, "OCTAVE ${app.lastVal(Kind.NOTE) / 12}  NEXT NOTE ${Defs.noteName(app.lastVal(Kind.NOTE))}", Pal.FAINT)
    }

    override fun info(): String = when (kind(cx)) {
        Kind.NOTE -> "A: note  A+L/R: semitone  A+U/D: octave  B+A on empty: OFF  Y: preview"
        Kind.INSTR -> "Instrument. A A: new, X+A: clone, SEL+RIGHT: edit it"
        Kind.VOL -> "Velocity 00-FF (empty = full)"
        else -> {
            val c = phrase.fx[(if (cx % 2 == 1) cx - 3 else cx - 4) / 2][cy]
            if (c == 0) "FX: A inserts, A+PAD picks: ARP SLD ACC PUP PDN VIB RTG KIL DLY CHA CUT RES PAN TMP DSN RSN TIM VSL HOP"
            else Defs.FX_HELP[c]
        }
    }

    override fun tick() {
        if (!app.follow || !app.playing || app.st[K.ST_GLOBAL + 1] != K.PLAY_SONG) return
        val b = app.curTrack * K.ST_PER_TRACK
        if (app.st[b] == 1 && app.st[b + 4] >= 0) {
            app.viewPhrase = app.st[b + 4]
            app.viewChain = app.st[b + 2]
            app.chainScreen.cy = app.st[b + 3]
        }
    }

    override fun hints() = "Y:PREVIEW  B+PAD:PHRASE#  START:LOOP"
}

// =====================================================================================================
// Parameter list screens
// =====================================================================================================
sealed class Item(val label: String) { open val selectable = true }
class HeaderItem(label: String) : Item(label) { override val selectable = false }
class InfoItem(label: String, val value: () -> String) : Item(label) { override val selectable = false }
class ParamItem(val def: P, val get: () -> Int, val set: (Int) -> Unit, label: String = def.name) : Item(label)
class ActionItem(label: String, val value: () -> String, val help: String, val onEdit: ((Int) -> Unit)? = null, val onA: () -> Unit) : Item(label)
class TextItem(label: String, val get: () -> String, val set: (String) -> Unit, val max: Int) : Item(label)

abstract class ParamScreen(app: Tracker) : Screen(app) {
    var col = 0
    var row = 0
    var textPos = 0
    protected open val colX = intArrayOf(0, 16)
    protected open val topY = 3
    protected val maxRows = 22

    abstract fun columns(): List<List<Item>>

    override fun cursorKey() = "$col,$row"

    private fun fix(cols: List<List<Item>>) {
        if (cols.isEmpty()) return
        col = col.coerceIn(0, cols.size - 1)
        if (cols[col].isEmpty()) col = cols.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0)
        val c = cols[col]
        row = row.coerceIn(0, max(0, c.size - 1))
        if (c.isNotEmpty() && !c[row].selectable) {
            val down = (row until c.size).firstOrNull { c[it].selectable }
            val up = (row downTo 0).firstOrNull { c[it].selectable }
            row = down ?: up ?: 0
        }
    }

    fun current(): Item? {
        val cols = columns()
        fix(cols)
        return cols.getOrNull(col)?.getOrNull(row)
    }

    override fun move(dx: Int, dy: Int) {
        val cols = columns()
        fix(cols)
        if (dy != 0) {
            val c = cols[col]
            var r = row + dy
            while (r in c.indices && !c[r].selectable) r += dy
            if (r in c.indices) row = r
        }
        if (dx != 0) {
            val nc = col + dx
            if (nc in cols.indices && cols[nc].any { it.selectable }) {
                col = nc
                val c = cols[col]
                val target = row
                row = c.indices.filter { c[it].selectable }.minByOrNull { kotlin.math.abs(it - target) } ?: 0
            }
        }
        textPos = 0
    }

    override fun edit(dx: Int, dy: Int) {
        when (val it = current()) {
            is ParamItem -> {
                val d = it.def
                val delta = if (d.fmt == Fmt.ENUM) dx + dy else dx + dy * d.coarse
                it.set(d.clamp(it.get() + delta))
            }
            is ActionItem -> it.onEdit?.invoke(if (dx != 0) dx else dy)
            is TextItem -> {
                if (dx != 0) textPos = (textPos + dx).coerceIn(0, it.max - 1)
                if (dy != 0) {
                    val s = it.get().padEnd(it.max).toCharArray()
                    val ci = CHARSET.indexOf(s[textPos]).coerceAtLeast(0)
                    s[textPos] = CHARSET[(ci + dy + CHARSET.length) % CHARSET.length]
                    it.set(String(s).trimEnd())
                }
            }
            else -> {}
        }
    }

    override fun tapA(double: Boolean) { (current() as? ActionItem)?.onA?.invoke() }

    override fun delete() {
        when (val it = current()) {
            is ParamItem -> it.set(it.def.def)
            is TextItem -> {
                val s = it.get().padEnd(it.max).toCharArray(); s[textPos] = ' '; it.set(String(s).trimEnd())
            }
            else -> {}
        }
    }

    override fun info(): String = when (val it = current()) {
        is ParamItem -> it.def.help
        is ActionItem -> it.help
        is TextItem -> "A+L/R: move  A+U/D: change letter"
        else -> ""
    }

    protected open fun drawHeader(g: Grid) {}

    override fun draw(g: Grid) {
        drawHeader(g)
        val cols = columns()
        fix(cols)
        for ((c, items) in cols.withIndex()) {
            val x = colX.getOrElse(c) { c * 16 }
            for ((i, it) in items.withIndex()) {
                val y = topY + i
                val isCur = c == col && i == row
                when (it) {
                    is HeaderItem -> g.text(x, y, it.label, Pal.ACCENT2)
                    is InfoItem -> { g.text(x, y, it.label, Pal.DIM); g.text(x + 8, y, it.value(), Pal.TEXT) }
                    is ParamItem -> {
                        g.text(x, y, it.label.take(7), if (isCur) Pal.ACCENT else Pal.TEXT)
                        val v = it.def.format(it.get())
                        val (f, b) = cellColors(isCur, false, Pal.YELLOW, 0)
                        g.text(x + 8, y, if (isCur) v.padEnd(2) else v, f, b)
                    }
                    is ActionItem -> {
                        g.text(x, y, it.label.take(7), if (isCur) Pal.ACCENT else Pal.TEXT)
                        val (f, b) = cellColors(isCur, false, Pal.BLUE, 0)
                        g.text(x + 8, y, it.value(), f, b)
                    }
                    is TextItem -> {
                        g.text(x, y, it.label.take(7), if (isCur) Pal.ACCENT else Pal.TEXT)
                        val s = it.get().padEnd(it.max)
                        g.text(x + 8, y, s, Pal.YELLOW, Pal.FAINT)
                        if (isCur) g.text(x + 8 + textPos, y, s[textPos].toString(), Pal.CURSOR_FG, Pal.CURSOR_BG)
                    }
                }
            }
        }
    }

    /** Packs groups into two columns, keeping each group together. */
    protected fun flow(first: List<Item>, groups: List<List<Item>>): List<List<Item>> {
        val a = first.toMutableList()
        val b = mutableListOf<Item>()
        var useB = false
        for (gr in groups) {
            if (!useB && a.size + gr.size > maxRows) useB = true
            if (useB) b.addAll(gr) else a.addAll(gr)
        }
        return listOf(a, b)
    }

    companion object { const val CHARSET = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_#!." }
}

// ----------------------------------------------------------------------------------------------- INSTRUMENT
class InstrumentScreen(app: Tracker) : ParamScreen(app) {
    override val name = "INSTRUMENT"
    private var presetSel = 0
    private var presetEngine = -1
    private var presetInstr = -1
    private val ins get() = song.instruments[app.viewInstr]

    override fun onEnter(from: Screen) {
        if (from === app.phraseScreen) {
            val ph = song.phrases[app.viewPhrase]
            val i = ph.inst[app.phraseScreen.cy]
            app.viewInstr = if (i >= 0) i else app.lastVal(Kind.INSTR)
        }
    }

    private fun setP(idx: Int, v: Int) {
        ins.p[idx] = v
        app.audio.setInstrParam(app.viewInstr, idx, v)
    }

    override fun columns(): List<List<Item>> {
        val i = ins
        val eng = i.engine.coerceIn(0, K.NUM_ENGINES - 1)
        val presets = Presets.forEngine(eng)
        if (presetEngine != eng || presetInstr != app.viewInstr) {
            presetEngine = eng; presetInstr = app.viewInstr
            presetSel = presets.indexOfFirst { it.name == i.name }.coerceAtLeast(0)
        }
        val first = mutableListOf<Item>(HeaderItem("INSTRUMENT"))
        first += TextItem("NAME", { i.name.trimEnd('-') }, { i.name = it }, 8)
        first += ParamItem(Defs.COMMON[0], { i.engine }, { v ->
            if (v != i.engine) {
                val keepName = i.name !in Defs.ENGINE_NAMES && presets.none { it.name == i.name }
                val old = i.name
                i.reset(v)
                if (keepName) i.name = old
                app.pushInstr(app.viewInstr)
            }
        })
        first += ActionItem("PRESET", { presets.getOrNull(presetSel)?.name ?: "-" }, "A+L/R: choose preset, A: load it", { d ->
            if (presets.isNotEmpty()) presetSel = (presetSel + d + presets.size) % presets.size
        }) {
            presets.getOrNull(presetSel)?.let { p ->
                p.applyTo(i); app.pushInstr(app.viewInstr); app.flash("LOADED ${p.name}", 1200)
            }
        }
        for (d in Defs.COMMON.drop(1)) first += ParamItem(d, { i.p[d.idx] }, { setP(d.idx, it) })
        val groups = Defs.engineGroups(eng, i.p[K.DR_MODEL]).map { gr ->
            listOf<Item>(HeaderItem(gr.name)) + gr.params.map { d ->
                if (eng == K.ENG_DRUM && d.idx == K.DR_MODEL) {
                    ParamItem(d, { i.p[d.idx] }, { v ->
                        if (v != i.p[K.DR_MODEL]) {
                            i.p[K.DR_MODEL] = v
                            val dd = Defs.DRUM_DEFAULTS[v]
                            for (k in 0 until 6) i.p[K.DR_P1 + k] = dd[k]
                            if (i.name in Defs.DRUM_MODELS || i.name in Defs.ENGINE_NAMES) i.name = Defs.DRUM_MODELS[v]
                            app.pushInstr(app.viewInstr)
                        }
                    })
                } else ParamItem(d, { i.p[d.idx] }, { setP(d.idx, it) })
            }
        }
        return flow(first, groups)
    }

    override fun bDir(dx: Int, dy: Int) { app.viewInstr = (app.viewInstr + dx * 16 + dy).coerceIn(0, K.INSTRS - 1) }

    override fun audition(on: Boolean) {
        if (on) app.auditionNote(app.viewInstr, app.lastVal(Kind.NOTE), 0xFF, hold = true) else app.auditionRelease()
    }

    override fun drawHeader(g: Grid) {
        g.text(0, 1, "INSTR ${HEX2[app.viewInstr]}", Pal.TEXT)
        g.text(10, 1, Defs.ENGINE_DESC[ins.engine.coerceIn(0, K.NUM_ENGINES - 1)].take(22), Pal.DIM)
    }

    override fun hints() = "Y:PLAY B+PAD:INSTR#  B+A:DEFAULT"
}

// ----------------------------------------------------------------------------------------------- TRACK
class TrackScreen(app: Tracker) : ParamScreen(app) {
    override val name = "TRACK FX"
    private val t get() = app.curTrack

    private fun item(d: P) = ParamItem(d, { song.tracks[t][d.idx] }, { v -> song.tracks[t][d.idx] = v; app.audio.setTrackParam(t, d.idx, v) })

    override fun columns(): List<List<Item>> {
        val type = song.tracks[t][K.T_INS_TYPE]
        val a = mutableListOf<Item>(HeaderItem("MIX"), item(Defs.T_VOL), item(Defs.T_PAN), item(Defs.T_WIDTH), item(Defs.T_SDLY), item(Defs.T_SREV))
        a += HeaderItem("INSERT")
        a += item(Defs.T_INS)
        for (k in 0 until 3) a += item(Defs.insP(k, type))
        a += item(Defs.T_INS_MIX)
        a += HeaderItem("LIMITER")
        Defs.T_LIM.forEach { a += item(it) }
        val b = mutableListOf<Item>(HeaderItem("EQ"))
        Defs.T_EQ.forEach { b += item(it) }
        b += HeaderItem("COMPRESSOR")
        Defs.T_COMP.forEach { b += item(it) }
        b += HeaderItem("SIDECHAIN")
        b += InfoItem("KEY", { scSummary() })
        return listOf(a, b)
    }

    private fun scSummary(): String {
        val m = song.tracks[t][K.T_SC_MASK]
        if (m == 0) return "NONE"
        val src = (0 until K.TRACKS).filter { (m shr it) and 1 == 1 }.joinToString("") { "${it + 1}" }
        return Defs.SC_MODES[song.tracks[t][K.T_SC_MODE].coerceIn(0, 1)] + " " + src
    }

    override fun bDir(dx: Int, dy: Int) { trackStep(dx + dy) }

    override fun audition(on: Boolean) {
        if (on) app.auditionNote(app.lastVal(Kind.INSTR), app.lastVal(Kind.NOTE), 0xFF, hold = true) else app.auditionRelease()
    }

    override fun drawHeader(g: Grid) {
        g.text(0, 1, "TRACK ${t + 1}", Pal.TEXT)
        g.text(9, 1, "EQ / COMP / WIDTH / LIMIT", Pal.DIM)
    }

    override fun hints() = "L1/R1:TRACK  B+A:DEFAULT  Y:PREVIEW"
}

// ----------------------------------------------------------------------------------------------- EFFECTS
class EffectsScreen(app: Tracker) : ParamScreen(app) {
    override val name = "EFFECTS"
    private fun item(d: P) = ParamItem(d, { song.globals[d.idx] }, { v -> song.globals[d.idx] = v; app.audio.setGlobalParam(d.idx, v) })

    override fun columns(): List<List<Item>> {
        val a = mutableListOf<Item>(HeaderItem("DELAY"))
        Defs.G_DELAY.forEach { a += item(it) }
        a += HeaderItem("REVERB")
        Defs.G_REVERB.forEach { a += item(it) }
        val b = mutableListOf<Item>(HeaderItem("MASTER"))
        Defs.G_MASTER.forEach { b += item(it) }
        b += HeaderItem("GROOVE")
        b += item(Defs.G_TEMPO)
        b += item(Defs.G_SWING)
        return listOf(a, b)
    }

    override fun drawHeader(g: Grid) { g.text(0, 1, "SEND FX + MASTER BUS", Pal.DIM) }
}

// ----------------------------------------------------------------------------------------------- PROJECT
class ProjectScreen(app: Tracker) : ParamScreen(app) {
    override val name = "PROJECT"
    private fun gItem(d: P) = ParamItem(d, { song.globals[d.idx] }, { v -> song.globals[d.idx] = v; app.audio.setGlobalParam(d.idx, v) })
    private fun onOff(b: Boolean) = if (b) "ON" else "OFF"

    override fun columns(): List<List<Item>> {
        val a = mutableListOf<Item>(
            HeaderItem("SONG"),
            TextItem("NAME", { song.name }, { song.name = it }, 8),
            gItem(Defs.G_TEMPO),
            gItem(Defs.G_SWING),
            HeaderItem("FILE"),
            ActionItem("SAVE", { "[A]" }, "Save project (name above)") { app.saveProject() },
            ActionItem("LOAD", { "[A]" }, "Open the project browser") { app.switchTo(app.filesScreen) },
            ActionItem("NEW", { "[A]" }, "Start an empty project (press A twice)") {
                app.confirm("new", "NEW PROJECT") { app.autosave(); app.newSong(); app.flash("NEW PROJECT") }
            },
            ActionItem("DEMO", { "[A]" }, "Load the demo song (press A twice)") {
                app.confirm("demo", "LOAD DEMO") { app.autosave(); app.loadDemo(); app.flash("DEMO LOADED") }
            },
            ActionItem("RENDER", { "WAV" }, "Render the song to Music/Hextrak/NAME.wav") {
                app.stopPlayback()
                app.flash("RENDERING...", 60000)
                app.platform.renderWav(song.name.trim().ifEmpty { "SONG" }) { msg -> app.flash(msg, 4000) }
            },
            ActionItem("EXIT", { "[A]" }, "Save and quit (press A twice)") {
                app.confirm("exit", "EXIT HEXTRAK") { app.autosave(); app.platform.exit() }
            },
        )
        val b = mutableListOf<Item>(
            HeaderItem("OPTIONS"),
            ActionItem("SWAP AB", { onOff(app.swapAB) }, "Swap A and B buttons", { app.swapAB = !app.swapAB }) { app.swapAB = !app.swapAB },
            ActionItem("SWAP XY", { onOff(app.swapXY) }, "Swap X and Y buttons", { app.swapXY = !app.swapXY }) { app.swapXY = !app.swapXY },
            ActionItem("FOLLOW", { onOff(app.follow) }, "Cursor follows playback on chain/phrase", { app.follow = !app.follow }) { app.follow = !app.follow },
            HeaderItem("SYSTEM"),
            ActionItem("HELP", { "[A]" }, "Controls reference") { app.switchTo(app.helpScreen) },
            ActionItem("PANIC", { "[A]" }, "Stop all sound and reset effects") { app.stopPlayback(); app.audio.panic() },
            InfoItem("RATE") { "${app.audio.sampleRate()}" },
            InfoItem("BUFFER") {
                val f = app.audio.bufferFrames()
                if (f <= 0) "-" else "$f %.1fms".format(f * 1000f / app.audio.sampleRate())
            },
            InfoItem("CPU") { "${(app.meters[K.TRACKS * 3 + 2] * 100).toInt()}%" },
            InfoItem("VERSION") { "1.0.1" },
        )
        return listOf(a, b)
    }

    override fun drawHeader(g: Grid) { g.text(0, 1, "PROJECT  ${song.name}", Pal.TEXT) }
}

// =====================================================================================================
// MIXER
// =====================================================================================================
class MixerScreen(app: Tracker) : Screen(app) {
    override val name = "MIXER"
    override val showSidePanel = false
    private var col = 0
    private var row = 0
    private val labels = arrayOf("VOL", "PAN", "DLY", "REV", "MUTE", "SOLO")
    private val params = intArrayOf(K.T_VOL, K.T_PAN, K.T_SEND_DLY, K.T_SEND_REV, K.T_MUTE, K.T_SOLO)

    override fun cursorKey() = "$col,$row"
    override fun onEnter(from: Screen) { col = app.curTrack }

    override fun move(dx: Int, dy: Int) {
        col = (col + dx).coerceIn(0, 8)
        row = (row + dy).coerceIn(0, 5)
        if (col == 8) row = 0
        if (col < 8) app.curTrack = col
    }
    override fun trackStep(d: Int) = move(d, 0)

    private fun setT(t: Int, p: Int, v: Int) { song.tracks[t][p] = v; app.audio.setTrackParam(t, p, v) }

    override fun edit(dx: Int, dy: Int) {
        if (col == 8) {
            val v = (song.globals[K.G_M_VOL] + dx + dy * 16).coerceIn(0, 255)
            song.globals[K.G_M_VOL] = v; app.audio.setGlobalParam(K.G_M_VOL, v)
            return
        }
        val p = params[row]
        if (row >= 4) setT(col, p, if (song.tracks[col][p] != 0) 0 else 1)
        else setT(col, p, (song.tracks[col][p] + dx + dy * 16).coerceIn(0, 255))
    }

    override fun tapA(double: Boolean) {
        if (col < 8 && row >= 4) setT(col, params[row], if (song.tracks[col][params[row]] != 0) 0 else 1)
    }

    override fun delete() {
        if (col == 8) { song.globals[K.G_M_VOL] = 0xC0; app.audio.setGlobalParam(K.G_M_VOL, 0xC0); return }
        val d = Defs.ALL_TRACK.first { it.idx == params[row] }
        setT(col, params[row], d.def)
    }

    override fun audition(on: Boolean) {
        if (on) app.auditionNote(app.lastVal(Kind.INSTR), app.lastVal(Kind.NOTE), 0xFF, hold = true) else app.auditionRelease()
    }

    private fun meterH(pk: Float): Float {
        if (pk <= 0.0001f) return 0f
        val db = 20f * log10(pk)
        return ((db + 48f) / 48f).coerceIn(0f, 1f)
    }

    override fun draw(g: Grid) {
        g.text(0, 1, "MIXER", Pal.TEXT)
        g.text(8, 1, "A+PAD:EDIT  A:MUTE/SOLO", Pal.DIM)
        for (c in 0 until 9) {
            val x = 4 + c * 4
            val label = if (c == 8) "MST" else " ${c + 1}"
            g.text(x, 2, label, if (c == col) Pal.ACCENT else Pal.DIM)
            g.rect(x + 0.4f, 3.2f, 2.2f, 12f, Pal.PANEL)
            if (c < 8) {
                val pk = max(app.meters[c * 3], app.meters[c * 3 + 1])
                val h = meterH(pk) * 12f
                val muted = app.trackMuted(c)
                g.rect(x + 0.6f, 15.2f - h, 1.8f, h, if (muted) Pal.DIM else if (pk > 0.95f) Pal.METER_HOT else Pal.METER)
                val gr = app.meters[c * 3 + 2]
                if (gr < 0.98f) g.rect(x + 0.6f, 3.2f, 1.8f, (1f - gr) * 12f, Pal.ORANGE)
            } else {
                val hl = meterH(app.meters[K.TRACKS * 3]) * 12f
                val hr = meterH(app.meters[K.TRACKS * 3 + 1]) * 12f
                g.rect(x + 0.5f, 15.2f - hl, 0.9f, hl, Pal.METER)
                g.rect(x + 1.6f, 15.2f - hr, 0.9f, hr, Pal.METER)
            }
        }
        for (r in 0 until 6) {
            val y = 16 + r
            g.text(0, y, labels[r], if (r == row) Pal.ACCENT else Pal.DIM)
            for (c in 0 until 9) {
                val x = 4 + c * 4
                val txt: String
                val fg: Int
                if (c == 8) {
                    if (r != 0) continue
                    txt = HEX2[song.globals[K.G_M_VOL]]; fg = Pal.YELLOW
                } else {
                    val v = song.tracks[c][params[r]]
                    when (r) {
                        4 -> { txt = if (v != 0) "M" else "-"; fg = if (v != 0) Pal.ACCENT2 else Pal.FAINT }
                        5 -> { txt = if (v != 0) "S" else "-"; fg = if (v != 0) Pal.YELLOW else Pal.FAINT }
                        else -> { txt = HEX2[v]; fg = if (r == 0) Pal.YELLOW else Pal.TEXT }
                    }
                }
                val (f, b) = cellColors(c == col && r == row, false, fg, 0)
                g.text(x, y, txt, f, b)
            }
        }
        // instrument names playing
        for (c in 0 until 8) {
            val b = c * K.ST_PER_TRACK
            val nm = if (app.st[b] == 1 || app.st[b + 6] >= 0) song.instruments[app.st[b + 7].coerceIn(0, K.INSTRS - 1)].name else "---"
            g.text(4 + c * 4, 23, nm.take(3), Pal.FAINT)
        }
        g.text(0, 23, "INS", Pal.DIM)
    }

    override fun info(): String = if (col == 8) "Master volume (C0 = 0 dB)"
    else when (row) {
        0 -> "Track ${col + 1} fader (C0 = 0 dB)"
        1 -> "Track ${col + 1} pan (80 = center)"
        2 -> "Track ${col + 1} delay send"
        3 -> "Track ${col + 1} reverb send"
        4 -> "Mute track ${col + 1}"
        else -> "Solo track ${col + 1}"
    }
}

// =====================================================================================================
// SIDECHAIN MATRIX
// =====================================================================================================
class SidechainScreen(app: Tracker) : Screen(app) {
    override val name = "SIDECHAIN"
    override val showSidePanel = false
    private var col = 0 // 0..7 sources, 8 mode, 9 depth, 10 attack, 11 release
    private var row = 0 // destination track
    private val valParams = intArrayOf(K.T_SC_MODE, K.T_SC_DEPTH, K.T_SC_ATK, K.T_SC_REL)
    private val valX = intArrayOf(23, 29, 33, 37)

    override fun cursorKey() = "$col,$row"
    override fun onEnter(from: Screen) { row = app.curTrack }

    override fun move(dx: Int, dy: Int) {
        col = (col + dx).coerceIn(0, 11)
        row = (row + dy).coerceIn(0, K.TRACKS - 1)
        app.curTrack = row
    }
    override fun trackStep(d: Int) = move(0, d)

    private fun setT(t: Int, p: Int, v: Int) { song.tracks[t][p] = v; app.audio.setTrackParam(t, p, v) }
    private fun hasSrc(d: Int, s: Int) = (song.tracks[d][K.T_SC_MASK] shr s) and 1 == 1
    private fun setSrc(d: Int, s: Int, on: Boolean) {
        if (d == s) return
        val m = song.tracks[d][K.T_SC_MASK]
        setT(d, K.T_SC_MASK, if (on) m or (1 shl s) else m and (1 shl s).inv())
    }

    override fun edit(dx: Int, dy: Int) {
        if (col < 8) { setSrc(row, col, !hasSrc(row, col)); return }
        val p = valParams[col - 8]
        if (p == K.T_SC_MODE) setT(row, p, 1 - song.tracks[row][p].coerceIn(0, 1))
        else setT(row, p, (song.tracks[row][p] + dx + dy * 16).coerceIn(0, 255))
    }

    override fun tapA(double: Boolean) {
        if (col < 8) setSrc(row, col, !hasSrc(row, col))
        else if (col == 8) setT(row, K.T_SC_MODE, 1 - song.tracks[row][K.T_SC_MODE].coerceIn(0, 1))
    }

    override fun delete() {
        if (col < 8) setSrc(row, col, false)
        else { val d = Defs.T_SC[col - 8]; setT(row, d.idx, d.def) }
    }

    /** X+A on a source column: toggle that source for every other track. */
    override fun clone() {
        if (col >= 8) return
        val on = !hasSrc(row, col)
        for (d in 0 until K.TRACKS) if (d != col) setSrc(d, col, on)
        app.flash(if (on) "TRACK ${col + 1} NOW DUCKS ALL TRACKS" else "TRACK ${col + 1} REMOVED AS SOURCE", 1800)
    }

    override fun draw(g: Grid) {
        g.text(0, 1, "SIDECHAIN MATRIX", Pal.TEXT)
        g.text(0, 2, "DEST", Pal.DIM)
        g.text(6, 2, "SOURCE TRACKS", Pal.DIM)
        g.text(23, 2, "MODE  DPT ATK REL", Pal.DIM)
        for (s in 0 until K.TRACKS) g.text(6 + s * 2, 3, "${s + 1}", if (s == col) Pal.ACCENT else Pal.DIM)
        for (d in 0 until K.TRACKS) {
            val y = 4 + d * 2
            g.text(0, y, "T${d + 1}", if (d == row) Pal.ACCENT else Pal.TEXT)
            val b = d * K.ST_PER_TRACK
            val iName = if (app.st[b] == 1 || app.st[b + 6] >= 0) song.instruments[app.st[b + 7].coerceIn(0, K.INSTRS - 1)].name else ""
            g.text(0, y + 1, iName.take(5), Pal.FAINT)
            for (s in 0 until K.TRACKS) {
                val txt: String
                val fg: Int
                if (s == d) { txt = "\\"; fg = Pal.FAINT }
                else if (hasSrc(d, s)) { txt = "X"; fg = Pal.ACCENT2 }
                else { txt = "."; fg = Pal.DIM }
                val (f, b) = cellColors(s == col && d == row, false, fg, 0)
                g.text(6 + s * 2, y, txt, f, b)
            }
            for (k in 0 until 4) {
                val v = song.tracks[d][valParams[k]]
                val txt = if (k == 0) Defs.SC_MODES[v.coerceIn(0, 1)] else HEX2[v.coerceIn(0, 255)]
                val active = song.tracks[d][K.T_SC_MASK] != 0
                val (f, b) = cellColors(col == 8 + k && d == row, false, if (active) Pal.YELLOW else Pal.DIM, 0)
                g.text(valX[k], y, txt, f, b)
            }
            val gr = app.meters[d * 3 + 2]
            g.rect(6f, y + 1.35f, 15f, 0.3f, Pal.PANEL)
            if (gr < 0.99f) g.rect(6f, y + 1.35f, 15f * (1f - gr), 0.3f, Pal.ORANGE)
        }
        g.text(0, 21, "Key = source synth output, pre-fader:", Pal.FAINT)
        g.text(0, 22, "muted 'ghost' tracks can still pump.", Pal.FAINT)
        g.text(0, 23, "DUCK = pumping gain. COMP = key the", Pal.FAINT)
        g.text(0, 24, "track compressor (set RATIO in TRACK FX)", Pal.FAINT)
    }

    override fun info(): String = when {
        col < 8 && col == row -> "A track cannot duck itself"
        col < 8 -> "A: toggle track ${col + 1} as key for track ${row + 1}. X+A: apply to all tracks"
        else -> Defs.T_SC[col - 8].help
    }

    override fun hints() = "A:TOGGLE  X+A:ALL TRACKS  A+PAD:EDIT"
}

// =====================================================================================================
// HELP
// =====================================================================================================
class HelpScreen(app: Tracker) : Screen(app) {
    override val name = "HELP"
    override val showSidePanel = false
    private var top = 0
    private val lines: List<Pair<String, Int>> by lazy {
        val l = mutableListOf<Pair<String, Int>>()
        fun h(s: String) { l += s to Pal.ACCENT2 }
        fun t(s: String) { l += s to Pal.TEXT }
        h("CONTROLS")
        t("D-PAD / L-STICK  move cursor")
        t("A                insert / action")
        t("A + D-PAD        edit: L/R fine, U/D big")
        t("A, A (double)    new chain/phrase/instr")
        t("B + A            delete (empty note=OFF)")
        t("X + A            clone chain/phrase/inst")
        t("B + D-PAD        browse chain/phrase/inst")
        t("SELECT + D-PAD   change screen")
        t("SELECT + B       select; then A=copy B=cut")
        t("SELECT + A       paste")
        t("START            play / stop")
        t("SELECT + START   play song from cursor")
        t("Y (hold)         preview sound")
        t("L1 / R1          previous / next track")
        t("L2 / R2          page up / down")
        t("R-STICK          edit value at cursor")
        t("SELECT + Y       this help   BACK x2 quit")
        t("")
        h("SCREEN MAP  (SELECT + D-PAD)")
        t("             PROJECT")
        t("SONG  CHAIN  PHRASE  INSTR  TRACK FX")
        t("   MIXER       EFFECTS      SIDECHAIN")
        t("")
        h("PLAYBACK")
        t("START on SONG plays from the cursor row.")
        t("START on CHAIN / PHRASE loops it on the")
        t("current track. Song rows play together:")
        t("a row lasts as long as its longest chain")
        t("(shorter chains repeat). Empty cells are")
        t("silent; a fully empty row loops back to")
        t("the top of that block.")
        t("")
        h("PHRASE FX COMMANDS")
        for (i in 1 until K.NUM_FX) t(Defs.FX_HELP[i].take(40))
        t("")
        h("SIDECHAIN")
        t("SIDECHAIN screen: rows are destination")
        t("tracks, columns are sources. Press A to")
        t("let a source duck a track. X+A applies")
        t("a source to every track at once.")
        t("")
        h("FILES")
        t("Projects: Android/data/com.hextrak.app/")
        t("  files/projects   WAV: Music/Hextrak")
        l
    }

    override fun move(dx: Int, dy: Int) { top = (top + dy + dx * 10).coerceIn(0, max(0, lines.size - 25)) }
    override fun page(d: Int) = move(0, d * 20)
    override fun tapB() = app.closeOverlay()
    override fun tapA(double: Boolean) = app.closeOverlay()

    override fun draw(g: Grid) {
        g.text(0, 1, "HELP  (B to close, D-PAD to scroll)", Pal.DIM)
        for (i in 0 until 25) {
            val (s, c) = lines.getOrNull(top + i) ?: break
            g.text(0, 3 + i - 1, s, c)
        }
    }
    override fun hints() = "UP/DOWN:SCROLL  B:CLOSE"
}

// =====================================================================================================
// FILE BROWSER
// =====================================================================================================
class FilesScreen(app: Tracker) : Screen(app) {
    override val name = "LOAD"
    override val showSidePanel = false
    private var files: List<String> = emptyList()
    private var sel = 0
    private var top = 0

    override fun onEnter(from: Screen) { files = app.platform.listProjects(); sel = sel.coerceIn(0, max(0, files.size - 1)) }
    override fun move(dx: Int, dy: Int) { if (files.isNotEmpty()) sel = (sel + dy + dx * 10).coerceIn(0, files.size - 1) }
    override fun cursorKey() = "$sel"
    override fun tapB() = app.closeOverlay()

    override fun tapA(double: Boolean) {
        val f = files.getOrNull(sel) ?: return
        val text = app.platform.loadProject(f)
        if (text != null && app.loadText(text)) app.flash("LOADED $f") else app.flash("COULD NOT LOAD $f")
    }

    override fun delete() {
        val f = files.getOrNull(sel) ?: return
        app.confirm("del:$f", "DELETE $f") {
            app.platform.deleteProject(f)
            files = app.platform.listProjects()
            sel = sel.coerceIn(0, max(0, files.size - 1))
            app.flash("DELETED $f")
        }
    }

    override fun draw(g: Grid) {
        g.text(0, 1, "LOAD PROJECT  (${files.size} files)", Pal.TEXT)
        if (files.isEmpty()) { g.text(2, 4, "No saved projects yet.", Pal.DIM); return }
        val vis = 22
        if (sel < top) top = sel
        if (sel >= top + vis) top = sel - vis + 1
        for (i in 0 until vis) {
            val f = files.getOrNull(top + i) ?: break
            val y = 3 + i
            val isSel = top + i == sel
            g.text(1, y, f.padEnd(30).take(30), if (isSel) Pal.CURSOR_FG else Pal.TEXT, if (isSel) Pal.CURSOR_BG else 0)
        }
    }
    override fun hints() = "A:LOAD  B+A:DELETE  B:BACK"
}
