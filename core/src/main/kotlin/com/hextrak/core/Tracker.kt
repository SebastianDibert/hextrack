package com.hextrak.core

import kotlin.math.abs
import kotlin.math.min

enum class Btn { UP, DOWN, LEFT, RIGHT, A, B, X, Y, L1, R1, L2, R2, START, SELECT, BACK }

/** Kinds of editable cells in the sequencer grids. */
enum class Kind(val min: Int, val max: Int, val empty: Int, val nullable: Boolean, val fine: Int, val coarse: Int) {
    CHAIN(0, K.CHAINS - 1, -1, true, 1, 16),
    PHRASE(0, K.PHRASES - 1, -1, true, 1, 16),
    TRANSP(-48, 48, 0, false, 1, 12),
    NOTE(0, K.MAX_NOTE, -1, true, 1, 12),
    INSTR(0, K.INSTRS - 1, -1, true, 1, 16),
    VOL(0, 255, -1, true, 1, 16),
    CMD(1, K.NUM_FX - 1, 0, true, 1, 4),
    FXVAL(0, 255, 0, false, 1, 16),
}

class Clip(val tag: String, val kinds: Array<Kind>, val data: Array<IntArray>) // data[col][row]

class Tracker(val audio: AudioBackend, val platform: Platform) {
    val song = Song()

    // ---- navigation state
    var curTrack = 0
    var songRow = 0
    var viewChain = 0
    var viewPhrase = 0
    var viewInstr = 0

    val songScreen = SongScreen(this)
    val chainScreen = ChainScreen(this)
    val phraseScreen = PhraseScreen(this)
    val instrScreen = InstrumentScreen(this)
    val trackScreen = TrackScreen(this)
    val projectScreen = ProjectScreen(this)
    val mixerScreen = MixerScreen(this)
    val effectsScreen = EffectsScreen(this)
    val sidechainScreen = SidechainScreen(this)
    val helpScreen = HelpScreen(this)
    val filesScreen = FilesScreen(this)

    val middle: List<Screen> = listOf(songScreen, chainScreen, phraseScreen, instrScreen, trackScreen)
    val bottom: List<Screen> = listOf(mixerScreen, effectsScreen, sidechainScreen)
    private val middleToBottom = intArrayOf(0, 0, 1, 1, 2)
    private val bottomToMiddle = intArrayOf(0, 3, 4)

    var screen: Screen = songScreen
        private set
    var lastMiddle: Screen = songScreen
    private var overlayReturn: Screen = songScreen

    // ---- edit memory
    private val lastVals = IntArray(Kind.values().size)
    fun lastVal(k: Kind) = lastVals[k.ordinal]
    fun setLast(k: Kind, v: Int) { if (!(k.nullable && v == k.empty)) lastVals[k.ordinal] = v }

    // ---- input state
    private val held = BooleanArray(Btn.values().size)
    fun isHeld(b: Btn) = held[b.ordinal]
    private var repeatBtn: Btn? = null
    private var repeatAt = 0L
    private var repeatCount = 0
    private var lastATime = -1000L
    private var lastAKey = ""
    var now = 0L
        private set
    private var stickX = 0f
    private var stickY = 0f
    private var stickAt = 0L

    // ---- options
    var swapAB = false
    var swapXY = false
    var follow = false

    // ---- selection / clipboard
    var selecting = false
        private set
    var selCol = 0
        private set
    var selRow = 0
        private set
    var clipboard: Clip? = null
        private set

    // ---- playback / meters
    val st = IntArray(K.ST_SIZE)
    val meters = FloatArray(K.MT_SIZE)
    val scope = FloatArray(K.SCOPE_SIZE)
    var playing = false
        private set
    private var playToggledAt = -10000L
    private var auditionOffAt = -1L
    private var auditionTrack = -1

    // ---- status line
    var status = ""
        private set
    private var statusUntil = 0L
    private var confirmKey: String? = null
    private var confirmUntil = 0L

    init {
        lastVals[Kind.NOTE.ordinal] = 48
        lastVals[Kind.VOL.ordinal] = 0xC0
        lastVals[Kind.CMD.ordinal] = K.FX_ARP
        lastVals[Kind.CHAIN.ordinal] = 0
        lastVals[Kind.PHRASE.ordinal] = 0
        lastVals[Kind.INSTR.ordinal] = 0
    }

    // ================================================================== audio sync
    fun pushSongCell(row: Int, t: Int) = audio.setSongCell(row, t, song.song[t][row])
    fun pushChain(c: Int) { for (s in 0 until K.STEPS) pushChainStep(c, s) }
    fun pushChainStep(c: Int, s: Int) = audio.setChainStep(c, s, song.chains[c].phrase[s], song.chains[c].transp[s])
    fun pushPhrase(p: Int) { for (s in 0 until K.STEPS) pushPhraseStep(p, s) }
    fun pushPhraseStep(p: Int, s: Int) {
        val ph = song.phrases[p]
        audio.setPhraseStep(p, s, ph.note[s], ph.inst[s], ph.vol[s], ph.fx[0][s], ph.fxv[0][s], ph.fx[1][s], ph.fxv[1][s], ph.fx[2][s], ph.fxv[2][s])
    }
    fun pushInstr(i: Int) { val ins = song.instruments[i]; for (k in 0 until K.NIP) audio.setInstrParam(i, k, ins.p[k]) }
    fun pushTrack(t: Int) { for (k in 0 until K.NTP) audio.setTrackParam(t, k, song.tracks[t][k]) }
    fun pushGlobals() { for (k in 0 until K.NGP) audio.setGlobalParam(k, song.globals[k]) }
    fun pushAll() {
        for (t in 0 until K.TRACKS) for (r in 0 until K.ROWS) pushSongCell(r, t)
        for (c in 0 until K.CHAINS) pushChain(c)
        for (p in 0 until K.PHRASES) pushPhrase(p)
        for (i in 0 until K.INSTRS) pushInstr(i)
        for (t in 0 until K.TRACKS) pushTrack(t)
        pushGlobals()
    }

    fun loadDemo() {
        stopPlayback()
        Demo.build(song)
        resetNav()
        pushAll()
    }

    fun newSong() {
        stopPlayback()
        song.clear()
        Presets.byName("KICK").applyTo(song.instruments[0])
        Presets.byName("SNARE").applyTo(song.instruments[1])
        Presets.byName("CLHAT").applyTo(song.instruments[2])
        Presets.byName("ACID").applyTo(song.instruments[3])
        Presets.byName("PAD").applyTo(song.instruments[4])
        Presets.byName("EPIANO").applyTo(song.instruments[5])
        resetNav()
        pushAll()
    }

    fun loadText(text: String): Boolean {
        stopPlayback()
        val ok = song.deserialize(text)
        resetNav()
        pushAll()
        return ok
    }

    private fun resetNav() {
        curTrack = 0; songRow = 0; viewChain = 0; viewPhrase = 0; viewInstr = 0
        songScreen.top = 0
        chainScreen.cy = 0; chainScreen.cx = 0
        phraseScreen.cy = 0; phraseScreen.cx = 0
        selecting = false
        switchTo(songScreen)
    }

    fun saveProject(): Boolean {
        val name = song.name.trim().ifEmpty { "SONG" }
        val ok = platform.saveProject(name, song.serialize())
        flash(if (ok) "SAVED $name" else "SAVE FAILED")
        return ok
    }

    fun autosave() { platform.saveProject("AUTOSAVE", song.serialize()) }

    // ================================================================== playback
    fun togglePlay() {
        if (playing) { stopPlayback(); return }
        when (screen) {
            chainScreen -> audio.play(K.PLAY_CHAIN, curTrack, viewChain, 0)
            phraseScreen -> audio.play(K.PLAY_PHRASE, curTrack, viewPhrase, 0)
            else -> audio.play(K.PLAY_SONG, 0, 0, songRow)
        }
        playing = true
        playToggledAt = now
    }

    fun playSong() {
        if (playing) { stopPlayback(); return }
        audio.play(K.PLAY_SONG, 0, 0, songRow)
        playing = true
        playToggledAt = now
    }

    fun stopPlayback() {
        audio.stop()
        playing = false
        playToggledAt = now
    }

    fun auditionNote(inst: Int, note: Int, vol: Int, hold: Boolean = false) {
        if (inst < 0 || note < 0 || note == K.NOTE_OFF) return
        audio.audition(curTrack, inst, note, if (vol < 0) 0xFF else vol)
        auditionTrack = curTrack
        auditionOffAt = if (hold) -1 else now + 350
    }

    fun auditionRelease() {
        if (auditionTrack >= 0) audio.auditionOff(auditionTrack)
        auditionOffAt = -1
    }

    // ================================================================== status
    fun flash(msg: String, ms: Long = 2200) { status = msg; statusUntil = now + ms }

    fun confirm(key: String, msg: String, action: () -> Unit) {
        if (confirmKey == key && now < confirmUntil) {
            confirmKey = null
            action()
        } else {
            confirmKey = key
            confirmUntil = now + 2500
            flash("$msg: PRESS AGAIN", 2500)
        }
    }

    // ================================================================== screens
    fun switchTo(s: Screen) {
        val from = screen
        if (s === helpScreen || s === filesScreen) {
            if (from !== helpScreen && from !== filesScreen) overlayReturn = from
        }
        selecting = false
        screen = s
        if (s in middle) lastMiddle = s
        s.onEnter(from)
    }

    fun closeOverlay() { switchTo(overlayReturn) }

    private fun navigate(dx: Int, dy: Int) {
        val s = screen
        val mi = middle.indexOf(s)
        val bi = bottom.indexOf(s)
        when {
            mi >= 0 -> when {
                dx != 0 -> switchTo(middle[(mi + dx).coerceIn(0, middle.size - 1)])
                dy < 0 -> switchTo(projectScreen)
                dy > 0 -> switchTo(bottom[middleToBottom[mi]])
            }
            s === projectScreen -> if (dy > 0) switchTo(lastMiddle)
            bi >= 0 -> when {
                dx != 0 -> switchTo(bottom[(bi + dx).coerceIn(0, bottom.size - 1)])
                dy < 0 -> {
                    val lm = middle.indexOf(lastMiddle)
                    switchTo(if (lm >= 0 && middleToBottom[lm] == bi) lastMiddle else middle[bottomToMiddle[bi]])
                }
            }
        }
    }

    fun mapPos(s: Screen): Pair<Int, Int>? {
        val mi = middle.indexOf(s)
        if (mi >= 0) return mi to 1
        val bi = bottom.indexOf(s)
        if (bi >= 0) return bottomToMiddle[bi] to 2
        if (s === projectScreen) return 2 to 0
        return null
    }

    // ================================================================== input
    private fun remap(b: Btn): Btn = when {
        swapAB && b == Btn.A -> Btn.B
        swapAB && b == Btn.B -> Btn.A
        swapXY && b == Btn.X -> Btn.Y
        swapXY && b == Btn.Y -> Btn.X
        else -> b
    }

    fun press(raw: Btn) {
        val b = remap(raw)
        if (held[b.ordinal]) return
        held[b.ordinal] = true
        when (b) {
            Btn.UP, Btn.DOWN, Btn.LEFT, Btn.RIGHT -> {
                val nav = isHeld(Btn.SELECT) && !selecting
                dir(b)
                if (!nav) { repeatBtn = b; repeatAt = now + 260; repeatCount = 0 }
            }
            Btn.A -> onA()
            Btn.B -> onB()
            Btn.X -> {}
            Btn.Y -> if (isHeld(Btn.SELECT)) {
                if (screen === helpScreen) closeOverlay() else switchTo(helpScreen)
            } else screen.audition(true)
            Btn.START -> if (isHeld(Btn.SELECT)) playSong() else togglePlay()
            Btn.SELECT -> if (selecting) { selecting = false; flash("SELECTION CANCELLED", 900) }
            Btn.L1 -> { selecting = false; screen.trackStep(-1) }
            Btn.R1 -> { selecting = false; screen.trackStep(1) }
            Btn.L2 -> screen.page(-1)
            Btn.R2 -> screen.page(1)
            Btn.BACK -> onBack()
        }
    }

    fun release(raw: Btn) {
        val b = remap(raw)
        if (!held[b.ordinal]) return
        held[b.ordinal] = false
        if (b == repeatBtn) repeatBtn = null
        if (b == Btn.Y) screen.audition(false)
    }

    /** Right analog stick: value editing. Values in -1..1, y negative = up. */
    fun stick(x: Float, y: Float) { stickX = x; stickY = y }

    private fun dir(b: Btn) {
        val dx = when (b) { Btn.LEFT -> -1; Btn.RIGHT -> 1; else -> 0 }
        val dy = when (b) { Btn.UP -> -1; Btn.DOWN -> 1; else -> 0 }
        when {
            isHeld(Btn.SELECT) && !selecting -> navigate(dx, dy)
            isHeld(Btn.A) -> screen.edit(dx, -dy)
            isHeld(Btn.B) && !selecting -> screen.bDir(dx, -dy)
            else -> screen.move(dx, dy)
        }
    }

    private fun onA() {
        when {
            isHeld(Btn.B) -> screen.delete()
            isHeld(Btn.X) -> screen.clone()
            isHeld(Btn.SELECT) -> paste()
            selecting -> copySelection(false)
            else -> {
                val key = screen.name + ":" + screen.cursorKey()
                val double = now - lastATime < 380 && key == lastAKey
                lastATime = if (double) -1000L else now
                lastAKey = key
                screen.tapA(double)
            }
        }
    }

    private fun onB() {
        when {
            isHeld(Btn.SELECT) -> startSelection()
            isHeld(Btn.A) -> screen.delete()
            selecting -> copySelection(true)
            else -> screen.tapB()
        }
    }

    private fun onBack() {
        when {
            screen === helpScreen || screen === filesScreen -> closeOverlay()
            selecting -> selecting = false
            else -> confirm("exit", "EXIT HEXTRAK") { autosave(); platform.exit() }
        }
    }

    // ================================================================== selection
    private fun startSelection() {
        val g = screen as? GridScreen ?: run { flash("NO SELECTION HERE", 900); return }
        selecting = true
        selCol = g.cx
        selRow = g.cy
        flash("SELECT: D-PAD SIZE, A COPY, B CUT", 3000)
    }

    fun selectionRect(): IntArray? {
        val g = screen as? GridScreen ?: return null
        if (!selecting) return null
        return intArrayOf(min(selCol, g.cx), min(selRow, g.cy), maxOf(selCol, g.cx), maxOf(selRow, g.cy))
    }

    private fun copySelection(cut: Boolean) {
        val g = screen as? GridScreen ?: return
        val r = selectionRect() ?: return
        val w = r[2] - r[0] + 1
        val h = r[3] - r[1] + 1
        val kinds = Array(w) { g.kind(r[0] + it) }
        val data = Array(w) { c -> IntArray(h) { row -> g.get(r[0] + c, r[1] + row) } }
        clipboard = Clip(g.name, kinds, data)
        if (cut) {
            for (c in 0 until w) for (row in 0 until h) g.clearCell(r[0] + c, r[1] + row)
        }
        selecting = false
        flash(if (cut) "CUT ${w}x$h" else "COPIED ${w}x$h", 1200)
    }

    private fun paste() {
        val g = screen as? GridScreen ?: return
        val clip = clipboard ?: run { flash("CLIPBOARD EMPTY", 900); return }
        if (clip.tag != g.name) { flash("CLIP IS FROM ${clip.tag}", 1200); return }
        var n = 0
        for (c in clip.data.indices) for (row in clip.data[c].indices) {
            val x = g.cx + c
            val y = g.cy + row
            if (x >= g.numCols || y >= g.numRows) continue
            if (g.kind(x) != clip.kinds[c]) continue
            g.set(x, y, clip.data[c][row]); n++
        }
        flash("PASTED $n CELLS", 900)
    }

    // ================================================================== frame update
    fun update(nowMs: Long) {
        now = nowMs
        val rb = repeatBtn
        if (rb != null && now >= repeatAt) {
            dir(rb)
            repeatCount++
            repeatAt = now + if (repeatCount > 12) 28 else 55
        }
        if (abs(stickX) > 0.45f || abs(stickY) > 0.45f) {
            if (now >= stickAt) {
                val ax = abs(stickX); val ay = abs(stickY)
                val mag = maxOf(ax, ay)
                if (ay >= ax) screen.edit(0, if (stickY < 0) 1 else -1) else screen.edit(if (stickX < 0) -1 else 1, 0)
                stickAt = now + (180 - (mag - 0.45f) / 0.55f * 150).toLong().coerceAtLeast(25)
            }
        } else stickAt = 0
        if (auditionOffAt in 1..now) auditionRelease()

        audio.getState(st)
        audio.getMeters(meters)
        audio.getScope(scope)
        val enginePlaying = st[K.ST_GLOBAL] == 1
        if (now - playToggledAt > 250 && enginePlaying != playing) playing = enginePlaying
        if (statusUntil in 1..now) { status = ""; statusUntil = 0 }
        screen.tick()
    }

    // ================================================================== rendering
    fun render(g: Grid) {
        g.clear()
        // header
        g.fillBg(0, 0, 40, 1, Pal.HEADER_BG)
        g.text(0, 0, "HEXTRAK", Pal.ACCENT)
        g.text(9, 0, screen.name, Pal.YELLOW)
        val tempo = if (playing) st[K.ST_GLOBAL + 2] / 10f else song.globals[K.G_TEMPO].toFloat()
        val play = if (playing) "PLAY" else "STOP"
        g.text(28, 0, play, if (playing) Pal.ACCENT2 else Pal.DIM)
        g.text(33, 0, "%5.1f".format(tempo), Pal.TEXT)
        if (playing) {
            val beat = (st[K.ST_GLOBAL + 4] / 4) % 4
            g.rect(38.3f, 0.25f, 0.5f, 0.5f, if (beat == 0) Pal.ACCENT2 else Pal.ACCENT)
        }

        screen.draw(g)
        if (screen.showSidePanel) drawSidePanel(g)

        // info + status + hints
        val info = screen.info()
        if (info.isNotEmpty()) {
            val lines = wrap(info, 40)
            for ((i, l) in lines.take(2).withIndex()) g.text(0, 26 + i, l, Pal.DIM)
        }
        if (status.isNotEmpty()) g.text(0, 28, status.take(40), Pal.YELLOW)
        g.fillBg(0, 29, 40, 1, Pal.HEADER_BG)
        g.text(0, 29, screen.hints().take(40), Pal.DIM)
    }

    private fun wrap(s: String, w: Int): List<String> {
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in s.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > w) { out += line.toString(); line = StringBuilder() }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    fun trackMuted(t: Int): Boolean {
        val anySolo = (0 until K.TRACKS).any { song.tracks[it][K.T_SOLO] != 0 }
        return song.tracks[t][K.T_MUTE] != 0 || (anySolo && song.tracks[t][K.T_SOLO] == 0)
    }

    private fun drawSidePanel(g: Grid) {
        val x0 = 32
        g.fillBg(x0, 2, 8, 23, Pal.PANEL)
        g.text(x0, 2, "TRACKS", Pal.DIM)
        for (t in 0 until K.TRACKS) {
            val y = 3 + t
            val b = t * K.ST_PER_TRACK
            val muted = trackMuted(t)
            val cur = t == curTrack
            g.text(x0, y, if (muted) "M" else "${t + 1}", if (muted) Pal.ACCENT2 else if (cur) Pal.ACCENT else Pal.DIM)
            val note = if (playing || st[b + 6] >= 0) st[b + 6] else -1
            g.text(x0 + 1, y, Defs.noteName(note), if (note >= 0) Pal.TEXT else Pal.FAINT)
            val pk = maxOf(meters[t * 3], meters[t * 3 + 1]).coerceIn(0f, 1f)
            g.rect(x0 + 4.2f, y + 0.25f, 3.6f, 0.5f, Pal.FAINT)
            if (pk > 0.002f) g.rect(x0 + 4.2f, y + 0.25f, 3.6f * pk, 0.5f, if (pk > 0.95f) Pal.METER_HOT else Pal.METER)
            val gr = meters[t * 3 + 2]
            if (gr < 0.97f) g.rect(x0 + 7.8f - 3.6f * (1f - gr), y + 0.8f, 3.6f * (1f - gr), 0.12f, Pal.ORANGE)
        }
        // scope
        g.text(x0, 12, "SCOPE", Pal.DIM)
        val n = 96
        val pts = FloatArray(n * 2)
        for (i in 0 until n) {
            pts[i * 2] = x0 + 0.2f + 7.6f * i / (n - 1)
            pts[i * 2 + 1] = 15.5f - (scope[i * 2].coerceIn(-1f, 1f) * 2.4f)
        }
        g.rect(x0 + 0.2f, 15.48f, 7.6f, 0.04f, Pal.FAINT)
        g.line(pts, Pal.ACCENT)
        // master meter
        val ml = meters[K.TRACKS * 3].coerceIn(0f, 1f)
        val mr = meters[K.TRACKS * 3 + 1].coerceIn(0f, 1f)
        g.text(x0, 18, "MST", Pal.DIM)
        g.rect(x0 + 3.2f, 18.15f, 4.6f, 0.3f, Pal.FAINT)
        g.rect(x0 + 3.2f, 18.55f, 4.6f, 0.3f, Pal.FAINT)
        g.rect(x0 + 3.2f, 18.15f, 4.6f * ml, 0.3f, if (ml > 0.97f) Pal.METER_HOT else Pal.METER)
        g.rect(x0 + 3.2f, 18.55f, 4.6f * mr, 0.3f, if (mr > 0.97f) Pal.METER_HOT else Pal.METER)
        // mini map
        g.text(x0, 20, "MAP", Pal.DIM)
        val pos = mapPos(screen)
        val cells = listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 0 to 2, 3 to 2, 4 to 2)
        for ((cx, cy) in cells) {
            val on = pos != null && pos.first == cx && pos.second == cy
            g.rect(x0 + 0.3f + cx * 1.5f, 21.2f + cy * 0.95f, 1.2f, 0.7f, if (on) Pal.ACCENT else Pal.FAINT)
        }
        val cpu = (meters[K.TRACKS * 3 + 2] * 100).toInt()
        g.text(x0, 24, "CPU%3d%%".format(cpu), if (cpu > 70) Pal.ACCENT2 else Pal.DIM)
    }
}
