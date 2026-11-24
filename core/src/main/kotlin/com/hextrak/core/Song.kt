package com.hextrak.core

class Phrase {
    val note = IntArray(K.STEPS) { -1 }
    val inst = IntArray(K.STEPS) { -1 }
    val vol = IntArray(K.STEPS) { -1 }
    val fx = Array(3) { IntArray(K.STEPS) }
    val fxv = Array(3) { IntArray(K.STEPS) }

    fun isEmpty(): Boolean {
        for (s in 0 until K.STEPS) {
            if (note[s] >= 0 || inst[s] >= 0 || vol[s] >= 0) return false
            for (k in 0 until 3) if (fx[k][s] != 0) return false
        }
        return true
    }
    fun clear() {
        note.fill(-1); inst.fill(-1); vol.fill(-1)
        for (k in 0 until 3) { fx[k].fill(0); fxv[k].fill(0) }
    }
    fun copyFrom(o: Phrase) {
        o.note.copyInto(note); o.inst.copyInto(inst); o.vol.copyInto(vol)
        for (k in 0 until 3) { o.fx[k].copyInto(fx[k]); o.fxv[k].copyInto(fxv[k]) }
    }
}

class Chain {
    val phrase = IntArray(K.STEPS) { -1 }
    val transp = IntArray(K.STEPS)
    fun isEmpty() = phrase.all { it < 0 }
    fun clear() { phrase.fill(-1); transp.fill(0) }
    fun copyFrom(o: Chain) { o.phrase.copyInto(phrase); o.transp.copyInto(transp) }
}

class Instrument {
    var name = "--------"
    val p = IntArray(K.NIP)

    init { reset(K.ENG_VA) }

    val engine get() = p[K.I_TYPE]

    /** Reset to engine defaults (keeps nothing). */
    fun reset(engine: Int, model: Int = 0) {
        p.fill(0)
        for (d in Defs.COMMON) p[d.idx] = d.def
        p[K.I_TYPE] = engine
        for (d in Defs.engineParams(engine, model)) p[d.idx] = d.def
        if (engine == K.ENG_DRUM) {
            p[K.DR_MODEL] = model
            val dd = Defs.DRUM_DEFAULTS[model.coerceIn(0, Defs.DRUM_DEFAULTS.size - 1)]
            for (k in 0 until 6) p[K.DR_P1 + k] = dd[k]
        }
        name = Defs.ENGINE_NAMES[engine]
    }
    fun copyFrom(o: Instrument) { o.p.copyInto(p); name = o.name }
    fun isDefault(): Boolean {
        val d = Instrument()
        return d.p.contentEquals(p) && d.name == name
    }
}

class Song {
    var name = "NEWSONG"
    /** song[track][row] = chain index or -1 */
    val song = Array(K.TRACKS) { IntArray(K.ROWS) { -1 } }
    val chains = Array(K.CHAINS) { Chain() }
    val phrases = Array(K.PHRASES) { Phrase() }
    val instruments = Array(K.INSTRS) { Instrument() }
    val tracks = Array(K.TRACKS) { IntArray(K.NTP) }
    val globals = IntArray(K.NGP)

    init { resetMixer() }

    fun resetMixer() {
        for (t in 0 until K.TRACKS) {
            tracks[t].fill(0)
            for (d in Defs.ALL_TRACK) tracks[t][d.idx] = d.def
            tracks[t][K.T_SC_MASK] = 0
        }
        globals.fill(0)
        for (d in Defs.ALL_GLOBAL) globals[d.idx] = d.def
    }

    fun clear() {
        name = "NEWSONG"
        for (t in 0 until K.TRACKS) song[t].fill(-1)
        chains.forEach { it.clear() }
        phrases.forEach { it.clear() }
        instruments.forEach { it.reset(K.ENG_VA) }
        resetMixer()
    }

    fun chainUsed(c: Int): Boolean {
        if (!chains[c].isEmpty()) return true
        for (t in 0 until K.TRACKS) for (r in 0 until K.ROWS) if (song[t][r] == c) return true
        return false
    }
    fun phraseUsed(p: Int): Boolean {
        if (!phrases[p].isEmpty()) return true
        for (c in chains) for (s in 0 until K.STEPS) if (c.phrase[s] == p) return true
        return false
    }
    fun instrUsed(i: Int): Boolean {
        if (!instruments[i].isDefault()) return true
        for (ph in phrases) for (s in 0 until K.STEPS) if (ph.inst[s] == i) return true
        return false
    }
    fun freeChain(): Int = (0 until K.CHAINS).firstOrNull { !chainUsed(it) } ?: -1
    fun freePhrase(): Int = (0 until K.PHRASES).firstOrNull { !phraseUsed(it) } ?: -1
    fun freeInstr(): Int = (0 until K.INSTRS).firstOrNull { !instrUsed(it) } ?: -1

    // ------------------------------------------------------------------ serialization
    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("HEXTRAK 1\n")
        sb.append("NAME ").append(name.replace(' ', '_')).append('\n')
        sb.append("G"); for (v in globals) sb.append(' ').append(v); sb.append('\n')
        for (t in 0 until K.TRACKS) { sb.append("T ").append(t); for (v in tracks[t]) sb.append(' ').append(v); sb.append('\n') }
        for (r in 0 until K.ROWS) {
            if ((0 until K.TRACKS).all { song[it][r] < 0 }) continue
            sb.append("S ").append(r); for (t in 0 until K.TRACKS) sb.append(' ').append(song[t][r]); sb.append('\n')
        }
        for (c in 0 until K.CHAINS) {
            val ch = chains[c]
            if (ch.isEmpty()) continue
            sb.append("C ").append(c)
            for (s in 0 until K.STEPS) sb.append(' ').append(ch.phrase[s]).append(' ').append(ch.transp[s])
            sb.append('\n')
        }
        for (p in 0 until K.PHRASES) {
            val ph = phrases[p]
            if (ph.isEmpty()) continue
            sb.append("P ").append(p)
            for (s in 0 until K.STEPS) {
                sb.append(' ').append(ph.note[s]).append(' ').append(ph.inst[s]).append(' ').append(ph.vol[s])
                for (k in 0 until 3) sb.append(' ').append(ph.fx[k][s]).append(' ').append(ph.fxv[k][s])
            }
            sb.append('\n')
        }
        for (i in 0 until K.INSTRS) {
            val ins = instruments[i]
            if (ins.isDefault()) continue
            sb.append("I ").append(i).append(' ').append(ins.name.replace(' ', '_'))
            for (v in ins.p) sb.append(' ').append(v)
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Returns false if the text is not a valid project. Leaves the song cleared on failure. */
    fun deserialize(text: String): Boolean {
        val lines = text.lines()
        if (lines.isEmpty() || !lines[0].startsWith("HEXTRAK")) return false
        clear()
        try {
            for (line in lines.drop(1)) {
                if (line.isBlank()) continue
                val tok = line.trim().split(' ')
                when (tok[0]) {
                    "NAME" -> name = tok.getOrElse(1) { "SONG" }.replace('_', ' ').take(8)
                    "G" -> for (k in 1 until tok.size) if (k - 1 < K.NGP) globals[k - 1] = tok[k].toInt()
                    "T" -> { val t = tok[1].toInt(); for (k in 2 until tok.size) if (k - 2 < K.NTP) tracks[t][k - 2] = tok[k].toInt() }
                    "S" -> { val r = tok[1].toInt(); for (t in 0 until K.TRACKS) song[t][r] = tok[2 + t].toInt() }
                    "C" -> {
                        val ch = chains[tok[1].toInt()]
                        for (s in 0 until K.STEPS) { ch.phrase[s] = tok[2 + s * 2].toInt(); ch.transp[s] = tok[3 + s * 2].toInt() }
                    }
                    "P" -> {
                        val ph = phrases[tok[1].toInt()]
                        var i = 2
                        for (s in 0 until K.STEPS) {
                            ph.note[s] = tok[i++].toInt(); ph.inst[s] = tok[i++].toInt(); ph.vol[s] = tok[i++].toInt()
                            for (k in 0 until 3) { ph.fx[k][s] = tok[i++].toInt(); ph.fxv[k][s] = tok[i++].toInt() }
                        }
                    }
                    "I" -> {
                        val ins = instruments[tok[1].toInt()]
                        ins.name = tok[2].replace('_', ' ').take(8)
                        for (k in 3 until tok.size) if (k - 3 < K.NIP) ins.p[k - 3] = tok[k].toInt()
                    }
                }
            }
        } catch (e: Exception) {
            clear()
            return false
        }
        return true
    }
}
