package com.hextrak.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemPlatform : Platform {
    val files = mutableMapOf<String, String>()
    override fun listProjects() = files.keys.sorted()
    override fun saveProject(name: String, data: String): Boolean { files[name] = data; return true }
    override fun loadProject(name: String) = files[name]
    override fun deleteProject(name: String) = files.remove(name) != null
    override fun renderWav(name: String, onDone: (String) -> Unit) { onDone("RENDERED $name") }
    override fun exit() {}
}

object Shots {
    fun save(g: Grid, file: File, cell: Int = 32) {
        val img = BufferedImage(g.cols * cell, g.rows * cell, BufferedImage.TYPE_INT_RGB)
        val gr = img.createGraphics()
        gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        gr.color = Color(Pal.BG, true)
        gr.fillRect(0, 0, img.width, img.height)
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val b = g.bg[y * g.cols + x]
            if (b != 0) { gr.color = Color(b, true); gr.fillRect(x * cell, y * cell, cell, cell) }
        }
        for (r in g.rects) {
            gr.color = Color(r.color, true)
            gr.fillRect((r.x * cell).toInt(), (r.y * cell).toInt(), maxOf(1, (r.w * cell).toInt()), maxOf(1, (r.h * cell).toInt()))
        }
        gr.font = Font(Font.MONOSPACED, Font.BOLD, (cell * 0.95).toInt())
        val fm = gr.fontMetrics
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val c = g.ch[y * g.cols + x]
            if (c == ' ') continue
            gr.color = Color(g.fg[y * g.cols + x], true)
            val w = fm.charWidth(c)
            gr.drawString(c.toString(), x * cell + (cell - w) / 2, y * cell + (cell + fm.ascent - fm.descent) / 2)
        }
        gr.stroke = BasicStroke(cell / 12f)
        for (l in g.lines) {
            gr.color = Color(l.color, true)
            for (i in 0 until l.pts.size / 2 - 1) {
                gr.drawLine((l.pts[i * 2] * cell).toInt(), (l.pts[i * 2 + 1] * cell).toInt(),
                    (l.pts[i * 2 + 2] * cell).toInt(), (l.pts[i * 2 + 3] * cell).toInt())
            }
        }
        gr.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
    }
}

class HostTest {
    private val outDir = File(System.getProperty("hextrak.out") ?: "build/host").also { it.mkdirs() }
    private val nativeLib = File(System.getProperty("hextrak.native") ?: "build/host/libhextrak_host.so")

    companion object {
        private var loaded = false
        fun loadNative(f: File): Boolean {
            if (loaded) return true
            if (!f.exists()) return false
            System.load(f.absolutePath)
            NativeAudio.init(false, 48000)
            loaded = true
            return true
        }
    }

    private fun wavStats(f: File): DoubleArray {
        val raf = RandomAccessFile(f, "r")
        val bytes = ByteArray((raf.length() - 44).toInt())
        raf.seek(44); raf.readFully(bytes); raf.close()
        val n = bytes.size / 2
        var peak = 0.0; var sum = 0.0
        for (i in 0 until n) {
            val s = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort() / 32768.0
            peak = maxOf(peak, abs(s)); sum += s * s
        }
        return doubleArrayOf(n / 2 / 48000.0, peak, sqrt(sum / maxOf(1, n)))
    }

    @Test
    fun serializationRoundTrip() {
        val a = Song(); Demo.build(a)
        val text = a.serialize()
        val b = Song()
        assertTrue(b.deserialize(text))
        assertEquals(text, b.serialize())
        assertEquals(124, b.globals[K.G_TEMPO])
        assertEquals("DEMO", b.name)
    }

    @Test
    fun inputFlows() {
        val app = Tracker(NullAudio(), MemPlatform())
        app.newSong()
        var t = 0L
        fun tap(vararg bs: Btn) { t += 50; app.update(t); bs.forEach { app.press(it) }; bs.reversed().forEach { app.release(it) }; t += 400; app.update(t) }
        fun combo(mod: Btn, b: Btn) { t += 50; app.press(mod); app.press(b); app.release(b); app.release(mod); t += 400; app.update(t) }
        // insert chain at song 00 track 1, double tap -> new chain
        tap(Btn.A)
        assertEquals(0, app.song.song[0][0])
        app.press(Btn.A); app.release(Btn.A); t += 100; app.update(t); app.press(Btn.A); app.release(Btn.A)
        val nc = app.song.song[0][0]
        assertTrue(nc >= 0)
        // into chain, new phrase
        combo(Btn.SELECT, Btn.RIGHT)
        assertEquals("CHAIN", app.screen.name)
        assertEquals(nc, app.viewChain)
        app.press(Btn.A); app.release(Btn.A); t += 100; app.update(t); app.press(Btn.A); app.release(Btn.A)
        val np = app.song.chains[nc].phrase[0]
        assertTrue(np >= 0)
        combo(Btn.SELECT, Btn.RIGHT)
        assertEquals("PHRASE", app.screen.name)
        assertEquals(np, app.viewPhrase)
        tap(Btn.A) // note C-4
        assertEquals(48, app.song.phrases[np].note[0])
        combo(Btn.A, Btn.UP) // +octave
        assertEquals(60, app.song.phrases[np].note[0])
        combo(Btn.A, Btn.RIGHT)
        assertEquals(61, app.song.phrases[np].note[0])
        tap(Btn.DOWN); combo(Btn.B, Btn.A) // empty note -> OFF
        assertEquals(K.NOTE_OFF, app.song.phrases[np].note[1])
        // FX column
        tap(Btn.UP); tap(Btn.RIGHT); tap(Btn.RIGHT); tap(Btn.RIGHT); tap(Btn.A)
        assertEquals(K.FX_ARP, app.song.phrases[np].fx[0][0])
        // copy row 0 and paste on row 2
        tap(Btn.LEFT); tap(Btn.LEFT); tap(Btn.LEFT)
        combo(Btn.SELECT, Btn.B)
        assertTrue(app.selecting)
        for (i in 0 until 8) tap(Btn.RIGHT)
        tap(Btn.A)
        assertTrue(!app.selecting)
        tap(Btn.DOWN); tap(Btn.DOWN)
        for (i in 0 until 8) tap(Btn.LEFT)
        combo(Btn.SELECT, Btn.A)
        assertEquals(61, app.song.phrases[np].note[2])
        assertEquals(K.FX_ARP, app.song.phrases[np].fx[0][2])
        // navigate to bottom row and project
        combo(Btn.SELECT, Btn.DOWN)
        assertEquals("EFFECTS", app.screen.name)
        combo(Btn.SELECT, Btn.RIGHT)
        assertEquals("SIDECHAIN", app.screen.name)
        combo(Btn.SELECT, Btn.UP)
        assertEquals("TRACK FX", app.screen.name)
        combo(Btn.SELECT, Btn.UP)
        assertEquals("PROJECT", app.screen.name)
        // save via project screen
        val plat = app.platform as MemPlatform
        for (i in 0 until 3) tap(Btn.DOWN)
        tap(Btn.A)
        assertTrue(plat.files.isNotEmpty(), "project saved")
    }

    @Test
    fun screenshots() {
        val native = loadNative(nativeLib)
        val app = Tracker(if (native) NativeAudio else NullAudio(), MemPlatform())
        app.loadDemo()
        val g = Grid()
        var t = 0L
        if (native) { app.playSong(); }
        fun shot(name: String) {
            // let audio "run" a little by offline rendering through the state (host: no realtime stream)
            if (native) NativeAudio.process(48000 * 3 / 2 + 7000)
            t += 16; app.update(t); app.render(g); Shots.save(g, File(outDir, "screen_$name.png"))
        }
        fun combo(mod: Btn, b: Btn) { app.press(mod); app.press(b); app.release(b); app.release(mod); t += 300; app.update(t) }
        app.songRow = 2; app.curTrack = 3
        shot("song")
        combo(Btn.SELECT, Btn.RIGHT); shot("chain")
        combo(Btn.SELECT, Btn.RIGHT); shot("phrase")
        combo(Btn.SELECT, Btn.RIGHT); shot("instrument_acid")
        app.viewInstr = 7; shot("instrument_fm")
        app.viewInstr = 5; shot("instrument_va")
        app.viewInstr = 0; shot("instrument_drum")
        combo(Btn.SELECT, Btn.RIGHT); shot("track")
        combo(Btn.SELECT, Btn.DOWN); shot("sidechain")
        combo(Btn.SELECT, Btn.LEFT); shot("effects")
        combo(Btn.SELECT, Btn.LEFT); shot("mixer")
        combo(Btn.SELECT, Btn.UP); combo(Btn.SELECT, Btn.UP); shot("project")
        app.press(Btn.SELECT); app.press(Btn.Y); app.release(Btn.Y); app.release(Btn.SELECT); shot("help")
        if (native) app.stopPlayback()
    }

    @Test
    fun nativeRenderDemo() {
        if (!loadNative(nativeLib)) { println("native lib missing, skipping"); return }
        val app = Tracker(NativeAudio, MemPlatform())
        app.loadDemo()
        val f = File(outDir, "demo.wav")
        val secs = NativeAudio.renderWav(f.absolutePath, 120)
        val (dur, peak, rms) = wavStats(f).let { Triple(it[0], it[1], it[2]) }
        println("demo.wav: %.1fs reported=%d peak=%.3f rms=%.3f".format(dur, secs, peak, rms))
        assertTrue(dur > 30 && dur < 40, "demo length ~33s (16 bars @124 + tail), got $dur")
        assertTrue(peak > 0.3 && peak <= 1.0, "peak sane $peak")
        assertTrue(rms > 0.03, "not silent $rms")
    }

    @Test
    fun nativeEngineSweep() {
        if (!loadNative(nativeLib)) { println("native lib missing, skipping"); return }
        val app = Tracker(NativeAudio, MemPlatform())
        // every preset plays a short phrase solo; check output is finite, audible and not clipping badly
        val report = StringBuilder()
        for (p in Presets.ALL) {
            app.song.clear()
            p.applyTo(app.song.instruments[0])
            val ph = app.song.phrases[0]
            val notes = intArrayOf(36, 43, 48, 55, 60, 48, 41, 48)
            for (s in 0 until 8) { ph.note[s * 2] = notes[s]; ph.inst[s * 2] = 0 }
            ph.fx[0][4] = K.FX_SLD; ph.fxv[0][4] = 0x20
            ph.fx[0][6] = K.FX_ACC
            app.song.chains[0].phrase[0] = 0
            app.song.song[0][0] = 0
            app.song.globals[K.G_M_RAT] = 0 // no master comp for measurement
            app.pushAll()
            val f = File(outDir, "eng_${p.name}.wav")
            NativeAudio.renderWav(f.absolutePath, 10)
            val s = wavStats(f)
            report.append("%-9s %-6s peak=%.3f rms=%.4f\n".format(p.name, Defs.ENGINE_NAMES[p.engine], s[1], s[2]))
            assertTrue(s[2] > 0.002, "${p.name} audible (rms=${s[2]})")
            assertTrue(s[1] < 1.0, "${p.name} below clipping (peak=${s[1]})")
        }
        println(report)
        File(outDir, "engine_levels.txt").writeText(report.toString())
    }
}
