package com.hextrak.core

class Preset(val name: String, val engine: Int, val model: Int = 0, val values: Map<Int, Int> = emptyMap()) {
    fun applyTo(ins: Instrument) {
        ins.reset(engine, model)
        for ((k, v) in values) ins.p[k] = v
        ins.name = name.take(8)
    }
}

object Presets {
    private fun op(k: Int, ratio: Int, level: Int, a: Int, d: Int, s: Int, r: Int): Array<Pair<Int, Int>> {
        val b = K.FM_OP0 + k * 6
        return arrayOf(b to ratio, b + 1 to level, b + 2 to a, b + 3 to d, b + 4 to s, b + 5 to r)
    }
    private fun m(vararg p: Pair<Int, Int>) = mapOf(*p)
    private fun fm(algo: Int, fb: Int, vararg ops: Array<Pair<Int, Int>>): Map<Int, Int> {
        val res = mutableMapOf(K.FM_ALGO to algo, K.FM_FDBK to fb)
        for (o in ops) for ((k, v) in o) res[k] = v
        return res
    }
    private fun drum(model: Int, vararg p: Int): Map<Int, Int> {
        val res = mutableMapOf<Int, Int>()
        p.forEachIndexed { i, v -> res[K.DR_P1 + i] = v }
        return res
    }

    val ALL: List<Preset> = listOf(
        // ---- FM
        Preset("EPIANO", K.ENG_FM, 0, fm(4, 0x10, op(0, 3, 0xF0, 0, 0xB8, 0x30, 0x78), op(1, 3, 0x78, 0, 0x98, 0x00, 0x70),
            op(2, 3, 0xC0, 0, 0xA8, 0x20, 0x78), op(3, 21, 0x48, 0, 0x70, 0x00, 0x60))),
        Preset("FMBASS", K.ENG_FM, 0, fm(0, 0x58, op(0, 3, 0xFF, 0, 0xA0, 0x70, 0x50), op(1, 3, 0x98, 0, 0x88, 0x30, 0x50),
            op(2, 6, 0x50, 0, 0x70, 0x20, 0x50), op(3, 3, 0x40, 0, 0x80, 0x40, 0x50))),
        Preset("BELL", K.ENG_FM, 0, fm(4, 0x00, op(0, 3, 0xE0, 0, 0xD0, 0x00, 0xC8), op(1, 9, 0x88, 0, 0xC0, 0x00, 0xB0),
            op(2, 6, 0x90, 0, 0xD8, 0x00, 0xC8), op(3, 14, 0x58, 0, 0xA0, 0x00, 0x90))),
        Preset("BRASS", K.ENG_FM, 0, fm(0, 0x70, op(0, 3, 0xF0, 0x38, 0x90, 0xC0, 0x60), op(1, 3, 0xA0, 0x58, 0x90, 0x80, 0x60),
            op(2, 3, 0x40, 0x20, 0x80, 0x40, 0x60), op(3, 3, 0x48, 0x30, 0x80, 0x60, 0x60))),
        Preset("ORGAN", K.ENG_FM, 0, fm(7, 0x00, op(0, 1, 0xC0, 0, 0x80, 0xFF, 0x38), op(1, 3, 0xC8, 0, 0x80, 0xFF, 0x38),
            op(2, 6, 0xA0, 0, 0x80, 0xFF, 0x38), op(3, 8, 0x80, 0, 0x80, 0xFF, 0x38))),
        Preset("METALIC", K.ENG_FM, 0, fm(5, 0xA0, op(0, 3, 0xC0, 0, 0xB0, 0x00, 0xA0), op(1, 8, 0xA0, 0, 0xA0, 0x00, 0x90),
            op(2, 13, 0x90, 0, 0x98, 0x00, 0x90), op(3, 4, 0xC0, 0, 0xA0, 0x40, 0x80))),
        Preset("PLUCKY", K.ENG_FM, 0, fm(0, 0x30, op(0, 3, 0xFF, 0, 0x98, 0x00, 0x70), op(1, 6, 0xB0, 0, 0x70, 0x00, 0x60),
            op(2, 3, 0x60, 0, 0x60, 0x00, 0x60), op(3, 10, 0x40, 0, 0x60, 0x00, 0x60))),
        // ---- ACID
        Preset("ACID", K.ENG_ACID, 0, m(K.AC_CUT to 0x38, K.AC_RES to 0xC0, K.AC_ENV to 0xA0, K.AC_DEC to 0x58, K.AC_DRIVE to 0x40, K.I_LENGTH to 3)),
        Preset("SQUELCH", K.ENG_ACID, 0, m(K.AC_WAVE to 1, K.AC_CUT to 0x28, K.AC_RES to 0xE8, K.AC_ENV to 0xD0, K.AC_DEC to 0x40, K.AC_DRIVE to 0x80, K.I_LENGTH to 3)),
        Preset("DEEPSUB", K.ENG_ACID, 0, m(K.AC_CUT to 0x30, K.AC_RES to 0x40, K.AC_ENV to 0x30, K.AC_DEC to 0x80, K.AC_DRIVE to 0x10, K.I_TRANSP to -12)),
        Preset("DIRTY303", K.ENG_ACID, 0, m(K.AC_CUT to 0x50, K.AC_RES to 0xD0, K.AC_ENV to 0x80, K.AC_DEC to 0x70, K.AC_DRIVE to 0xE0, K.I_LENGTH to 4)),
        // ---- DRUM
        Preset("KICK", K.ENG_DRUM, K.DM_KICK, drum(K.DM_KICK, 0x80, 0x70, 0x90, 0x50, 0x40, 0x18)),
        Preset("808KICK", K.ENG_DRUM, K.DM_KICK, drum(K.DM_KICK, 0x70, 0xC8, 0x60, 0x70, 0x10, 0x30)),
        Preset("HARDKICK", K.ENG_DRUM, K.DM_KICK, drum(K.DM_KICK, 0x88, 0x60, 0xD0, 0x38, 0x90, 0x90)),
        Preset("SNARE", K.ENG_DRUM, K.DM_SNARE, drum(K.DM_SNARE, 0x80, 0x50, 0xA8, 0x60, 0x98, 0x18)),
        Preset("CLHAT", K.ENG_DRUM, K.DM_CLHAT, drum(K.DM_CLHAT, 0x80, 0x30, 0x90, 0x90, 0, 0)),
        Preset("OPHAT", K.ENG_DRUM, K.DM_OPHAT, drum(K.DM_OPHAT, 0x80, 0x78, 0x90, 0x90, 0, 0)),
        Preset("CLAP", K.ENG_DRUM, K.DM_CLAP, drum(K.DM_CLAP, 0x80, 0x70, 0x60, 0x70, 0x60, 0x18)),
        Preset("TOM", K.ENG_DRUM, K.DM_TOM, drum(K.DM_TOM, 0x80, 0x80, 0x80, 0x60, 0x30, 0)),
        Preset("CONGA", K.ENG_DRUM, K.DM_TOM, drum(K.DM_TOM, 0xC0, 0x40, 0x30, 0x20, 0x10, 0)),
        Preset("RIM", K.ENG_DRUM, K.DM_RIM, drum(K.DM_RIM, 0x80, 0x40, 0x80, 0x60, 0, 0x20)),
        Preset("COWBELL", K.ENG_DRUM, K.DM_COWBELL, drum(K.DM_COWBELL, 0x80, 0x60, 0x80, 0, 0, 0)),
        Preset("CYMBAL", K.ENG_DRUM, K.DM_CYMBAL, drum(K.DM_CYMBAL, 0x80, 0xA0, 0x80, 0xA0, 0, 0)),
        Preset("SHAKER", K.ENG_DRUM, K.DM_SHAKER, drum(K.DM_SHAKER, 0x80, 0x50, 0x40, 0, 0, 0)),
        Preset("ZAP", K.ENG_DRUM, K.DM_ZAP, drum(K.DM_ZAP, 0x80, 0x60, 0x90, 0x50, 0, 0)),
        // ---- VA
        Preset("SUPRSAW", K.ENG_VA, 0, m(K.VA_UNI to 3, K.VA_SPREAD to 0x70, K.VA_DETUNE to 12, K.VA_CUT to 0xC0, K.VA_AA to 0x10, K.VA_AR to 0x90)),
        Preset("PAD", K.ENG_VA, 0, m(K.VA_UNI to 3, K.VA_SPREAD to 0x60, K.VA_DETUNE to 9, K.VA_CUT to 0x90, K.VA_RES to 0x20, K.VA_FENV to 30,
            K.VA_FA to 0x90, K.VA_FD to 0xC0, K.VA_AA to 0x98, K.VA_AS to 0xD0, K.VA_AR to 0xB0, K.VA_LFOAMT to 0x18, K.VA_LFODEST to 2, K.VA_LFORATE to 0x50)),
        Preset("VABASS", K.ENG_VA, 0, m(K.VA_OSC2 to 1, K.VA_SEMI to -12, K.VA_SUB to 0x80, K.VA_CUT to 0x58, K.VA_RES to 0x50, K.VA_FENV to 70,
            K.VA_FD to 0x70, K.VA_FS to 0x20, K.VA_AA to 0, K.VA_AS to 0xC0, K.VA_AR to 0x40)),
        Preset("LEAD", K.ENG_VA, 0, m(K.VA_OSC1 to 1, K.VA_PW to 0x40, K.VA_DETUNE to 10, K.VA_CUT to 0xA8, K.VA_RES to 0x40, K.VA_FENV to 40,
            K.VA_GLIDE to 0x30, K.VA_LFOAMT to 0x20, K.VA_LFORATE to 0x98, K.VA_AA to 0x04, K.VA_AR to 0x60)),
        Preset("STRINGS", K.ENG_VA, 0, m(K.VA_UNI to 2, K.VA_SPREAD to 0x50, K.VA_FTYPE to 0, K.VA_CUT to 0x98, K.VA_AA to 0x80, K.VA_AS to 0xE0,
            K.VA_AR to 0xA8, K.VA_LFOAMT to 0x10, K.VA_LFORATE to 0x90)),
        Preset("VABRASS", K.ENG_VA, 0, m(K.VA_CUT to 0x60, K.VA_RES to 0x30, K.VA_FENV to 90, K.VA_FA to 0x60, K.VA_FD to 0x90, K.VA_FS to 0x60,
            K.VA_AA to 0x40, K.VA_AS to 0xD0)),
        // ---- WAVE
        Preset("VOXPAD", K.ENG_WAVE, 0, m(K.WT_TABLE to 2, K.WT_POS to 0x20, K.WT_LFOAMT to 0x90, K.WT_LFORATE to 0x40, K.WT_SPREAD to 0x60,
            K.WT_AA to 0x90, K.WT_AS to 0xD0, K.WT_AR to 0xB0)),
        Preset("DIGIBELL", K.ENG_WAVE, 0, m(K.WT_TABLE to 6, K.WT_POS to 0x10, K.WT_ENVAMT to 100, K.WT_ENVDEC to 0x98, K.WT_AD to 0xC0, K.WT_AS to 0x00, K.WT_AR to 0xA0)),
        Preset("WTORGAN", K.ENG_WAVE, 0, m(K.WT_TABLE to 4, K.WT_POS to 0xA0, K.WT_AA to 0x02, K.WT_AS to 0xFF, K.WT_AR to 0x40)),
        Preset("SYNCLEAD", K.ENG_WAVE, 0, m(K.WT_TABLE to 5, K.WT_POS to 0x30, K.WT_ENVAMT to 80, K.WT_ENVDEC to 0x90, K.WT_CUT to 0xD0, K.WT_RES to 0x40)),
        Preset("FOLDBASS", K.ENG_WAVE, 0, m(K.WT_TABLE to 7, K.WT_POS to 0x20, K.WT_ENVAMT to 90, K.WT_ENVDEC to 0x70, K.WT_CUT to 0x90,
            K.WT_SPREAD to 0, K.WT_AA to 0, K.WT_AR to 0x40, K.I_TRANSP to -12)),
        // ---- PLUCK
        Preset("GUITAR", K.ENG_PLUCK, 0, m(K.PL_DECAY to 0x90, K.PL_DAMP to 0x50, K.PL_EXCITE to 0xA0, K.PL_POS to 0x30, K.PL_BODY to 0x60)),
        Preset("HARP", K.ENG_PLUCK, 0, m(K.PL_DECAY to 0xA8, K.PL_DAMP to 0x30, K.PL_EXCITE to 0x70, K.PL_POS to 0x60, K.PL_BODY to 0x30)),
        Preset("KOTO", K.ENG_PLUCK, 0, m(K.PL_DECAY to 0x78, K.PL_DAMP to 0x18, K.PL_EXCITE to 0xF0, K.PL_POS to 0x18, K.PL_BODY to 0x20)),
        Preset("MUTED", K.ENG_PLUCK, 0, m(K.PL_DECAY to 0x40, K.PL_DAMP to 0xA0, K.PL_EXCITE to 0x80, K.PL_POS to 0x40, K.PL_BODY to 0x50)),
        // ---- CHIP
        Preset("SQUARE", K.ENG_CHIP, 0, m(K.CH_WAVE to 2)),
        Preset("PULSE", K.ENG_CHIP, 0, m(K.CH_WAVE to 0, K.CH_VIBD to 0x30, K.CH_VIBS to 0x70)),
        Preset("TRIBASS", K.ENG_CHIP, 0, m(K.CH_WAVE to 4, K.CH_AS to 0xFF, K.CH_AR to 0x20)),
        Preset("NOISEHAT", K.ENG_CHIP, 0, m(K.CH_WAVE to 6, K.CH_AD to 0x40, K.CH_AS to 0, K.CH_AR to 0x30)),
        Preset("CHIPSNR", K.ENG_CHIP, 0, m(K.CH_WAVE to 7, K.CH_AD to 0x60, K.CH_AS to 0, K.CH_SWEEP to -40)),
        Preset("LASER", K.ENG_CHIP, 0, m(K.CH_WAVE to 1, K.CH_AD to 0x70, K.CH_AS to 0, K.CH_SWEEP to -64)),
    )

    fun forEngine(engine: Int) = ALL.filter { it.engine == engine }
    fun byName(name: String) = ALL.first { it.name == name }
}

object Demo {
    private fun note(ph: Phrase, s: Int, n: Int, inst: Int, vol: Int = -1, fx: Int = 0, v: Int = 0, fx2: Int = 0, v2: Int = 0) {
        ph.note[s] = n; ph.inst[s] = inst; ph.vol[s] = vol
        ph.fx[0][s] = fx; ph.fxv[0][s] = v
        ph.fx[1][s] = fx2; ph.fxv[1][s] = v2
    }

    fun build(song: Song) {
        song.clear()
        song.name = "DEMO"
        song.globals[K.G_TEMPO] = 124
        song.globals[K.G_SWING] = 8
        song.globals[K.G_DLY_TIME] = 7 // 1/8D

        val ins = song.instruments
        Presets.byName("KICK").applyTo(ins[0])
        Presets.byName("CLAP").applyTo(ins[1])
        Presets.byName("CLHAT").applyTo(ins[2])
        Presets.byName("OPHAT").applyTo(ins[3])
        Presets.byName("ACID").applyTo(ins[4])
        Presets.byName("PAD").applyTo(ins[5]); ins[5].p[K.I_VOL] = 0x98
        Presets.byName("GUITAR").applyTo(ins[6]); ins[6].name = "PLUCK"
        Presets.byName("BELL").applyTo(ins[7])
        Presets.byName("RIM").applyTo(ins[8])
        // a few extra ready-to-use sounds
        Presets.byName("SNARE").applyTo(ins[9])
        Presets.byName("808KICK").applyTo(ins[10])
        Presets.byName("SUPRSAW").applyTo(ins[11])
        Presets.byName("EPIANO").applyTo(ins[12])
        Presets.byName("VOXPAD").applyTo(ins[13])
        Presets.byName("SQUARE").applyTo(ins[14])
        Presets.byName("FMBASS").applyTo(ins[15])

        val p = song.phrases
        val C4 = 48; val A2 = 33; val A3 = 45; val E4 = 52; val A4 = 57
        // 00 kick
        for (s in 0 until 16 step 4) note(p[0], s, C4, 0)
        // 0C kick with fill
        for (s in 0 until 16 step 4) note(p[0x0C], s, C4, 0)
        note(p[0x0C], 14, C4, 0, 0x90); note(p[0x0C], 15, C4, 0, 0x70)
        // 01 clap + rim ghosts
        note(p[1], 4, C4, 1); note(p[1], 12, C4, 1)
        note(p[1], 7, C4, 8, 0x50); note(p[1], 15, C4, 8, 0x40, K.FX_RTG, 3)
        // 02 hats
        for (s in 0 until 16) {
            if (s % 4 == 2) note(p[2], s, C4, 3, 0x90)
            else note(p[2], s, C4, 2, if (s % 2 == 0) 0xB0 else 0x68)
        }
        // 03 acid
        val acid = intArrayOf(A2, A2, A3, A2, 36, A2, 43, A2, A2, 40, A2, 38, A2, C4, A2, 31)
        for (s in 0 until 16) note(p[3], s, acid[s], 4)
        for (s in intArrayOf(0, 5, 10, 13)) { p[3].fx[0][s] = K.FX_ACC; p[3].fxv[0][s] = 0 }
        for (s in intArrayOf(2, 6, 11)) { p[3].fx[1][s] = K.FX_SLD; p[3].fxv[1][s] = 0x10 }
        // 04 / 05 pad root + fifth
        note(p[4], 0, A3, 5)
        note(p[5], 0, E4, 5, 0xD0)
        // 06 minor arp pluck / 07 major arp pluck
        val arpSteps = intArrayOf(0, 3, 6, 8, 11, 14)
        for ((i, s) in arpSteps.withIndex()) {
            note(p[6], s, A4, 6, if (i % 2 == 0) 0xD0 else 0x90, K.FX_ARP, 0x37)
            note(p[7], s, A4, 6, if (i % 2 == 0) 0xD0 else 0x90, K.FX_ARP, 0x47)
        }
        // 08..0B bell melody
        val mel = arrayOf(
            intArrayOf(64, 60, 57, 59), intArrayOf(57, 60, 65, 64),
            intArrayOf(67, 64, 60, 62), intArrayOf(59, 62, 67, 62),
        )
        val melSteps = intArrayOf(0, 6, 10, 14)
        for (b in 0 until 4) for (k in 0 until 4) note(p[8 + b], melSteps[k], mel[b][k], 7, if (k == 0) 0xE0 else 0xA8)
        p[0x0B].fx[0][14] = K.FX_VIB; p[0x0B].fxv[0][14] = 0x64

        val c = song.chains
        fun chain(i: Int, vararg steps: Pair<Int, Int>) { steps.forEachIndexed { s, (ph, tr) -> c[i].phrase[s] = ph; c[i].transp[s] = tr } }
        chain(0, 0 to 0, 0 to 0, 0 to 0, 0x0C to 0)
        chain(1, 1 to 0, 1 to 0, 1 to 0, 1 to 0)
        chain(2, 2 to 0, 2 to 0, 2 to 0, 2 to 0)
        chain(3, 3 to 0, 3 to -4, 3 to 3, 3 to -2)
        chain(4, 4 to 0, 4 to -4, 4 to 3, 4 to -2)
        chain(5, 5 to 0, 5 to -4, 5 to 3, 5 to -2)
        chain(6, 6 to 0, 7 to -4, 7 to 3, 7 to -2)
        chain(7, 8 to 0, 9 to 0, 0x0A to 0, 0x0B to 0)

        val rows = arrayOf(
            intArrayOf(0, -1, 2, 3, -1, -1, -1, -1),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, -1),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7),
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7),
        )
        for ((r, row) in rows.withIndex()) for (t in 0 until K.TRACKS) song.song[t][r] = row[t]

        // mixer
        val tr = song.tracks
        val vols = intArrayOf(0xC8, 0xA8, 0x90, 0xA8, 0x90, 0x88, 0x98, 0x98)
        for (t in 0 until K.TRACKS) tr[t][K.T_VOL] = vols[t]
        tr[0][K.T_EQ_LG] = 3; tr[0][K.T_EQ_LF] = 0x40
        tr[1][K.T_SEND_REV] = 0x60; tr[1][K.T_SEND_DLY] = 0x38
        tr[1][K.T_CMP_THR] = -18; tr[1][K.T_CMP_RAT] = 4; tr[1][K.T_CMP_MAK] = 4
        tr[2][K.T_PAN] = 0x98; tr[2][K.T_EQ_HG] = 2
        tr[3][K.T_EQ_MG] = 3; tr[3][K.T_EQ_MF] = 0x70; tr[3][K.T_SEND_DLY] = 0x30
        tr[4][K.T_PAN] = 0x60; tr[5][K.T_PAN] = 0xA0
        tr[4][K.T_SEND_REV] = 0x90; tr[5][K.T_SEND_REV] = 0x90
        tr[4][K.T_WIDTH] = 0xC0; tr[5][K.T_WIDTH] = 0xC0
        tr[6][K.T_INS_TYPE] = 3; tr[6][K.T_INS_P1] = 0x50; tr[6][K.T_INS_P2] = 0x80; tr[6][K.T_INS_P3] = 0x20; tr[6][K.T_INS_MIX] = 0x70
        tr[6][K.T_SEND_DLY] = 0x90; tr[6][K.T_SEND_REV] = 0x60
        tr[7][K.T_SEND_REV] = 0xA0; tr[7][K.T_SEND_DLY] = 0x70
        // sidechain: kick (track 1) ducks acid, pads and pluck
        for ((t, depth) in listOf(3 to 0x70, 4 to 0xD0, 5 to 0xD0, 6 to 0x90)) {
            tr[t][K.T_SC_MASK] = 1; tr[t][K.T_SC_MODE] = 0; tr[t][K.T_SC_DEPTH] = depth
            tr[t][K.T_SC_ATK] = 0x08; tr[t][K.T_SC_REL] = 0x58
        }
    }
}
