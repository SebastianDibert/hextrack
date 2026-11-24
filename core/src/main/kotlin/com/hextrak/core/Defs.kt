package com.hextrak.core

/** Shared constants. MUST stay in sync with app/src/main/cpp/params.h */
object K {
    const val TRACKS = 8
    const val ROWS = 256
    const val CHAINS = 255
    const val PHRASES = 255
    const val INSTRS = 128
    const val STEPS = 16
    const val NIP = 48
    const val NTP = 32
    const val NGP = 32
    const val NOTE_OFF = 120
    const val MAX_NOTE = 119

    // instrument common
    const val I_TYPE = 0; const val I_VOL = 1; const val I_TRANSP = 2; const val I_FINE = 3; const val I_LENGTH = 4; const val I_E0 = 8

    const val ENG_FM = 0; const val ENG_ACID = 1; const val ENG_DRUM = 2; const val ENG_VA = 3
    const val ENG_WAVE = 4; const val ENG_PLUCK = 5; const val ENG_CHIP = 6; const val NUM_ENGINES = 7

    // FM
    const val FM_ALGO = I_E0; const val FM_FDBK = I_E0 + 1; const val FM_VELS = I_E0 + 2; const val FM_OP0 = I_E0 + 3
    // ACID
    const val AC_WAVE = I_E0; const val AC_CUT = I_E0 + 1; const val AC_RES = I_E0 + 2; const val AC_ENV = I_E0 + 3
    const val AC_DEC = I_E0 + 4; const val AC_ACC = I_E0 + 5; const val AC_SLIDE = I_E0 + 6; const val AC_DRIVE = I_E0 + 7
    // DRUM
    const val DR_MODEL = I_E0; const val DR_P1 = I_E0 + 1
    const val DM_KICK = 0; const val DM_SNARE = 1; const val DM_CLHAT = 2; const val DM_OPHAT = 3; const val DM_CLAP = 4
    const val DM_TOM = 5; const val DM_RIM = 6; const val DM_COWBELL = 7; const val DM_CYMBAL = 8; const val DM_SHAKER = 9; const val DM_ZAP = 10
    // VA
    const val VA_OSC1 = I_E0; const val VA_OSC2 = I_E0 + 1; const val VA_SEMI = I_E0 + 2; const val VA_DETUNE = I_E0 + 3
    const val VA_MIX = I_E0 + 4; const val VA_PW = I_E0 + 5; const val VA_SUB = I_E0 + 6; const val VA_UNI = I_E0 + 7
    const val VA_SPREAD = I_E0 + 8; const val VA_FTYPE = I_E0 + 9; const val VA_CUT = I_E0 + 10; const val VA_RES = I_E0 + 11
    const val VA_FENV = I_E0 + 12; const val VA_KEYTRK = I_E0 + 13; const val VA_FA = I_E0 + 14; const val VA_FD = I_E0 + 15
    const val VA_FS = I_E0 + 16; const val VA_FR = I_E0 + 17; const val VA_AA = I_E0 + 18; const val VA_AD = I_E0 + 19
    const val VA_AS = I_E0 + 20; const val VA_AR = I_E0 + 21; const val VA_LFORATE = I_E0 + 22; const val VA_LFOAMT = I_E0 + 23
    const val VA_LFODEST = I_E0 + 24; const val VA_GLIDE = I_E0 + 25; const val VA_DRIVE = I_E0 + 26
    // WAVE
    const val WT_TABLE = I_E0; const val WT_POS = I_E0 + 1; const val WT_ENVAMT = I_E0 + 2; const val WT_ENVDEC = I_E0 + 3
    const val WT_LFORATE = I_E0 + 4; const val WT_LFOAMT = I_E0 + 5; const val WT_SPREAD = I_E0 + 6; const val WT_CUT = I_E0 + 7
    const val WT_RES = I_E0 + 8; const val WT_AA = I_E0 + 9; const val WT_AD = I_E0 + 10; const val WT_AS = I_E0 + 11; const val WT_AR = I_E0 + 12
    // PLUCK
    const val PL_DECAY = I_E0; const val PL_DAMP = I_E0 + 1; const val PL_EXCITE = I_E0 + 2; const val PL_POS = I_E0 + 3
    const val PL_BODY = I_E0 + 4; const val PL_REL = I_E0 + 5
    // CHIP
    const val CH_WAVE = I_E0; const val CH_AA = I_E0 + 1; const val CH_AD = I_E0 + 2; const val CH_AS = I_E0 + 3
    const val CH_AR = I_E0 + 4; const val CH_SWEEP = I_E0 + 5; const val CH_VIBD = I_E0 + 6; const val CH_VIBS = I_E0 + 7

    // track params
    const val T_VOL = 0; const val T_PAN = 1; const val T_MUTE = 2; const val T_SOLO = 3; const val T_INS_TYPE = 4
    const val T_INS_P1 = 5; const val T_INS_P2 = 6; const val T_INS_P3 = 7; const val T_INS_MIX = 8
    const val T_EQ_LG = 9; const val T_EQ_LF = 10; const val T_EQ_MG = 11; const val T_EQ_MF = 12; const val T_EQ_MQ = 13
    const val T_EQ_HG = 14; const val T_EQ_HF = 15
    const val T_CMP_THR = 16; const val T_CMP_RAT = 17; const val T_CMP_ATK = 18; const val T_CMP_REL = 19; const val T_CMP_MAK = 20
    const val T_WIDTH = 21; const val T_LIM_CEIL = 22; const val T_LIM_REL = 23; const val T_SEND_DLY = 24; const val T_SEND_REV = 25
    const val T_SC_MASK = 26; const val T_SC_MODE = 27; const val T_SC_DEPTH = 28; const val T_SC_ATK = 29; const val T_SC_REL = 30

    // global params
    const val G_TEMPO = 0; const val G_SWING = 1; const val G_DLY_TIME = 2; const val G_DLY_FB = 3; const val G_DLY_PP = 4
    const val G_DLY_LC = 5; const val G_DLY_HC = 6; const val G_DLY_RET = 7; const val G_DLY_REV = 8
    const val G_REV_SIZE = 9; const val G_REV_DAMP = 10; const val G_REV_PRE = 11; const val G_REV_WID = 12; const val G_REV_LC = 13
    const val G_REV_RET = 14; const val G_M_THR = 15; const val G_M_RAT = 16; const val G_M_ATK = 17; const val G_M_REL = 18
    const val G_M_MAK = 19; const val G_M_LIM = 20; const val G_M_VOL = 21; const val G_M_LO = 22; const val G_M_HI = 23

    // fx commands
    const val FX_NONE = 0; const val FX_ARP = 1; const val FX_SLD = 2; const val FX_ACC = 3; const val FX_PUP = 4; const val FX_PDN = 5
    const val FX_VIB = 6; const val FX_RTG = 7; const val FX_KIL = 8; const val FX_DLY = 9; const val FX_CHA = 10; const val FX_CUT = 11
    const val FX_RES = 12; const val FX_PAN = 13; const val FX_TMP = 14; const val FX_DSN = 15; const val FX_RSN = 16; const val FX_TIM = 17
    const val FX_VSL = 18; const val FX_HOP = 19; const val NUM_FX = 20

    const val PLAY_SONG = 0; const val PLAY_CHAIN = 1; const val PLAY_PHRASE = 2

    const val ST_PER_TRACK = 8
    const val ST_GLOBAL = TRACKS * ST_PER_TRACK
    const val ST_SIZE = ST_GLOBAL + 8
    const val MT_SIZE = TRACKS * 3 + 4
    const val SCOPE_SIZE = 256
}

enum class Fmt { HEX, SIGNED, DEC, ENUM }

val HEX2: Array<String> = Array(256) { "%02X".format(it) }
val HEX1: Array<String> = Array(16) { "%X".format(it) }

class P(
    val idx: Int,
    val name: String,
    val min: Int,
    val max: Int,
    val def: Int,
    val fmt: Fmt = Fmt.HEX,
    val labels: Array<String>? = null,
    val help: String = "",
) {
    fun format(v: Int): String = when (fmt) {
        Fmt.HEX -> HEX2[v.coerceIn(0, 255)]
        Fmt.SIGNED -> if (v > 0) "+$v" else "$v"
        Fmt.DEC -> "$v"
        Fmt.ENUM -> labels!![v.coerceIn(0, labels.size - 1)]
    }
    val coarse: Int get() = when (fmt) { Fmt.HEX -> 16; Fmt.SIGNED, Fmt.DEC -> if (max - min > 30) 10 else 4; Fmt.ENUM -> 1 }
    fun clamp(v: Int) = v.coerceIn(min, max)
}

class Group(val name: String, val params: List<P>)

object Defs {
    val ENGINE_NAMES = arrayOf("FM4", "ACID", "DRUM", "VA", "WAVE", "PLUCK", "CHIP")
    val ENGINE_DESC = arrayOf(
        "4-operator FM synth, 8 algorithms",
        "303-style mono bass: ladder filter, accent, slide",
        "Drum synth: 11 percussion models",
        "Virtual analog: 2 osc + sub, unison, filter",
        "Wavetable synth: 8 morphing tables",
        "Karplus-Strong plucked string",
        "Chiptune pulse / wave / noise voice",
    )

    val NOTE_NAMES = arrayOf("C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-")
    fun noteName(n: Int): String = when {
        n < 0 -> "---"
        n == K.NOTE_OFF -> "OFF"
        else -> NOTE_NAMES[n % 12] + (n / 12)
    }

    val FX_NAMES = arrayOf("---", "ARP", "SLD", "ACC", "PUP", "PDN", "VIB", "RTG", "KIL", "DLY", "CHA", "CUT", "RES",
        "PAN", "TMP", "DSN", "RSN", "TIM", "VSL", "HOP")
    val FX_HELP = arrayOf(
        "No effect",
        "ARP xy: arpeggio cycles note, +x, +y semitones",
        "SLD xx: slide/legato from previous note (xx=time)",
        "ACC xx: accent (ACID: filter+amp kick, others: louder)",
        "PUP xx: pitch up xx/64 semitone per tick",
        "PDN xx: pitch down xx/64 semitone per tick",
        "VIB xy: vibrato speed x, depth y",
        "RTG xx: retrigger note every xx ticks (6/step)",
        "KIL xx: cut note after xx ticks",
        "DLY xx: delay note start by xx ticks",
        "CHA xx: chance to play (FF=always, 80=50%)",
        "CUT xx: override filter cutoff",
        "RES xx: override filter resonance",
        "PAN xx: track pan (80 = center)",
        "TMP xx: set tempo to xx BPM (hex)",
        "DSN xx: track delay send level",
        "RSN xx: track reverb send level",
        "TIM xx: timbre macro (80 = neutral)",
        "VSL xy: volume slide up x / down y per tick",
        "HOP xx: jump to phrase step xx after this step",
    )

    // ---------------------------------------------------------------- instrument params
    private val ADSR_HELP = "Envelope time: 00 = 0.5ms ... FF = 10s"
    val COMMON = listOf(
        P(K.I_TYPE, "TYPE", 0, K.NUM_ENGINES - 1, K.ENG_VA, Fmt.ENUM, ENGINE_NAMES, "Synth engine. Changing it resets the sound."),
        P(K.I_VOL, "VOLUME", 0, 255, 0xC0, help = "Instrument output level"),
        P(K.I_TRANSP, "TRANSP", -48, 48, 0, Fmt.SIGNED, help = "Transpose in semitones"),
        P(K.I_FINE, "FINE", -50, 50, 0, Fmt.SIGNED, help = "Fine tune in cents"),
        P(K.I_LENGTH, "LENGTH", 0, 255, 0, help = "Auto note-off after xx ticks (00 = hold until next note/OFF)"),
    )

    val FM_RATIOS = arrayOf("0.25", "0.5", "0.75", "1", "1.41", "1.5", "2", "2.5", "3", "3.5", "4", "4.5",
        "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16")
    val FM_ALGOS = arrayOf("4>3>2>1", "34>2>1", "3>2>1+4", "4>3>1+2", "2>1 4>3", "4>123", "4>3 1 2", "1 2 3 4")

    private fun fmOp(k: Int): Group {
        val b = K.FM_OP0 + k * 6
        val n = k + 1
        return Group("OP$n", listOf(
            P(b, "RATIO", 0, 23, 3, Fmt.ENUM, FM_RATIOS, "OP$n frequency ratio"),
            P(b + 1, "LEVEL", 0, 255, if (k == 0) 0xFF else 0x60, help = "OP$n output (carrier) or mod depth (modulator)"),
            P(b + 2, "ATTACK", 0, 255, 0x00, help = ADSR_HELP),
            P(b + 3, "DECAY", 0, 255, 0x90, help = ADSR_HELP),
            P(b + 4, "SUSTN", 0, 255, 0x80, help = "Sustain level"),
            P(b + 5, "RELEAS", 0, 255, 0x80, help = ADSR_HELP),
        ))
    }

    val DRUM_MODELS = arrayOf("KICK", "SNARE", "CLHAT", "OPHAT", "CLAP", "TOM", "RIM", "COWBL", "CYMBL", "SHAKR", "ZAP")
    val DRUM_LABELS = arrayOf(
        arrayOf("TUNE", "DECAY", "PUNCH", "PDECAY", "CLICK", "DRIVE"),
        arrayOf("TUNE", "DECAY", "SNAPPY", "NDECAY", "TONE", "DRIVE"),
        arrayOf("TUNE", "DECAY", "TONE", "METAL", "-", "DRIVE"),
        arrayOf("TUNE", "DECAY", "TONE", "METAL", "-", "DRIVE"),
        arrayOf("TONE", "DECAY", "SPREAD", "BURSTS", "Q", "DRIVE"),
        arrayOf("TUNE", "DECAY", "PUNCH", "PDECAY", "NOISE", "DRIVE"),
        arrayOf("TUNE", "DECAY", "TONE", "NOISE", "-", "DRIVE"),
        arrayOf("TUNE", "DECAY", "TONE", "-", "-", "DRIVE"),
        arrayOf("TUNE", "DECAY", "TONE", "METAL", "-", "DRIVE"),
        arrayOf("TONE", "DECAY", "ATTACK", "-", "-", "DRIVE"),
        arrayOf("TUNE", "DECAY", "SWEEP", "SDECAY", "-", "DRIVE"),
    )
    val DRUM_DEFAULTS = arrayOf(
        intArrayOf(0x80, 0x70, 0x90, 0x50, 0x40, 0x10),
        intArrayOf(0x80, 0x50, 0xA0, 0x60, 0x90, 0x10),
        intArrayOf(0x80, 0x30, 0x90, 0x90, 0, 0),
        intArrayOf(0x80, 0x70, 0x90, 0x90, 0, 0),
        intArrayOf(0x80, 0x70, 0x60, 0x70, 0x60, 0x10),
        intArrayOf(0x80, 0x80, 0x80, 0x60, 0x30, 0),
        intArrayOf(0x80, 0x40, 0x80, 0x60, 0, 0x20),
        intArrayOf(0x80, 0x60, 0x80, 0, 0, 0),
        intArrayOf(0x80, 0xA0, 0x80, 0xA0, 0, 0),
        intArrayOf(0x80, 0x50, 0x40, 0, 0, 0),
        intArrayOf(0x80, 0x60, 0x90, 0x50, 0, 0),
    )
    val DRUM_HELP = mapOf(
        "TUNE" to "Pitch offset (80 = default, note also tunes)",
        "DECAY" to "Amplitude decay time",
        "PUNCH" to "Pitch envelope depth",
        "PDECAY" to "Pitch envelope time",
        "CLICK" to "Transient click level",
        "DRIVE" to "Saturation",
        "SNAPPY" to "Snare wire (noise) level",
        "NDECAY" to "Noise decay time",
        "TONE" to "Filter frequency / brightness",
        "METAL" to "Metallic oscillators vs noise",
        "SPREAD" to "Time between clap bursts",
        "BURSTS" to "Number of clap bursts",
        "Q" to "Filter resonance",
        "NOISE" to "Noise layer level",
        "ATTACK" to "Attack time",
        "SWEEP" to "Pitch sweep depth",
        "SDECAY" to "Pitch sweep time",
    )

    fun drumGroup(model: Int): Group {
        val m = model.coerceIn(0, DRUM_MODELS.size - 1)
        val ps = mutableListOf(P(K.DR_MODEL, "MODEL", 0, DRUM_MODELS.size - 1, 0, Fmt.ENUM, DRUM_MODELS, "Percussion model"))
        for (k in 0 until 6) {
            val lbl = DRUM_LABELS[m][k]
            ps += P(K.DR_P1 + k, lbl, 0, 255, DRUM_DEFAULTS[m][k], help = DRUM_HELP[lbl] ?: "Unused for this model")
        }
        return Group("DRUM", ps)
    }

    val VA_WAVES1 = arrayOf("SAW", "SQR", "TRI", "SINE")
    val VA_WAVES2 = arrayOf("SAW", "SQR", "TRI", "SINE", "NOISE")
    val VA_FTYPES = arrayOf("LP12", "LP24", "BP", "HP")
    val VA_LFODEST = arrayOf("PITCH", "CUT", "PW", "AMP", "PAN")
    val WT_TABLES = arrayOf("BASIC", "PWM", "VOCAL", "DIGI", "ORGAN", "SYNC", "BELL", "FOLD")
    val CHIP_WAVES = arrayOf("P12", "P25", "P50", "P75", "TRI", "SAW", "NOISE", "METAL")
    val WAVE_2 = arrayOf("SAW", "SQR")
    val UNISON = arrayOf("1", "2", "3", "4")

    fun engineGroups(engine: Int, model: Int = 0): List<Group> = when (engine) {
        K.ENG_FM -> listOf(
            Group("FM", listOf(
                P(K.FM_ALGO, "ALGO", 0, 7, 0, Fmt.ENUM, FM_ALGOS, "Algorithm: a>b means a modulates b; carriers on the right"),
                P(K.FM_FDBK, "FDBK", 0, 255, 0x20, help = "OP4 self-feedback"),
                P(K.FM_VELS, "VELSNS", 0, 255, 0x80, help = "Velocity to modulation depth"),
            )),
            fmOp(0), fmOp(1), fmOp(2), fmOp(3),
        )
        K.ENG_ACID -> listOf(Group("ACID", listOf(
            P(K.AC_WAVE, "WAVE", 0, 1, 0, Fmt.ENUM, WAVE_2, "Oscillator waveform"),
            P(K.AC_CUT, "CUTOFF", 0, 255, 0x40, help = "Filter cutoff"),
            P(K.AC_RES, "RESO", 0, 255, 0xB0, help = "Filter resonance"),
            P(K.AC_ENV, "ENVMOD", 0, 255, 0x90, help = "Filter envelope depth"),
            P(K.AC_DEC, "DECAY", 0, 255, 0x60, help = "Filter envelope decay"),
            P(K.AC_ACC, "ACCENT", 0, 255, 0xC0, help = "Accent strength (use ACC fx in phrase)"),
            P(K.AC_SLIDE, "SLIDE", 0, 255, 0x30, help = "Default slide time reference (use SLD fx)"),
            P(K.AC_DRIVE, "DRIVE", 0, 255, 0x30, help = "Output overdrive"),
        )))
        K.ENG_DRUM -> listOf(drumGroup(model))
        K.ENG_VA -> listOf(
            Group("OSC", listOf(
                P(K.VA_OSC1, "OSC1", 0, 3, 0, Fmt.ENUM, VA_WAVES1, "Oscillator 1 waveform"),
                P(K.VA_OSC2, "OSC2", 0, 4, 0, Fmt.ENUM, VA_WAVES2, "Oscillator 2 waveform"),
                P(K.VA_SEMI, "SEMI", -24, 24, 0, Fmt.SIGNED, help = "OSC2 semitone offset"),
                P(K.VA_DETUNE, "DETUNE", -50, 50, 7, Fmt.SIGNED, help = "OSC2 detune in cents"),
                P(K.VA_MIX, "MIX", 0, 255, 0x80, help = "OSC1 <-> OSC2 balance"),
                P(K.VA_PW, "PW", 0, 255, 0, help = "Pulse width (square waves)"),
                P(K.VA_SUB, "SUB", 0, 255, 0, help = "Sub oscillator (-1 oct square)"),
                P(K.VA_UNI, "UNISON", 0, 3, 0, Fmt.ENUM, UNISON, "Unison voices per note"),
                P(K.VA_SPREAD, "SPREAD", 0, 255, 0x40, help = "Unison detune + stereo spread"),
                P(K.VA_GLIDE, "GLIDE", 0, 255, 0, help = "Portamento time"),
            )),
            Group("FILTER", listOf(
                P(K.VA_FTYPE, "TYPE", 0, 3, 1, Fmt.ENUM, VA_FTYPES, "Filter type"),
                P(K.VA_CUT, "CUTOFF", 0, 255, 0xB0, help = "Filter cutoff"),
                P(K.VA_RES, "RESO", 0, 255, 0x30, help = "Filter resonance"),
                P(K.VA_FENV, "ENVAMT", -127, 127, 0, Fmt.SIGNED, help = "Filter envelope amount"),
                P(K.VA_KEYTRK, "KEYTRK", 0, 255, 0x40, help = "Cutoff follows note pitch"),
                P(K.VA_DRIVE, "DRIVE", 0, 255, 0, help = "Pre-filter saturation"),
            )),
            Group("FILT ENV", listOf(
                P(K.VA_FA, "ATTACK", 0, 255, 0, help = ADSR_HELP), P(K.VA_FD, "DECAY", 0, 255, 0x90, help = ADSR_HELP),
                P(K.VA_FS, "SUSTN", 0, 255, 0x40), P(K.VA_FR, "RELEAS", 0, 255, 0x80, help = ADSR_HELP),
            )),
            Group("AMP ENV", listOf(
                P(K.VA_AA, "ATTACK", 0, 255, 0x08, help = ADSR_HELP), P(K.VA_AD, "DECAY", 0, 255, 0x90, help = ADSR_HELP),
                P(K.VA_AS, "SUSTN", 0, 255, 0xC0), P(K.VA_AR, "RELEAS", 0, 255, 0x80, help = ADSR_HELP),
            )),
            Group("LFO", listOf(
                P(K.VA_LFORATE, "RATE", 0, 255, 0x80, help = "LFO rate 0.05 - 25 Hz"),
                P(K.VA_LFOAMT, "AMOUNT", 0, 255, 0, help = "LFO depth"),
                P(K.VA_LFODEST, "DEST", 0, 4, 0, Fmt.ENUM, VA_LFODEST, "LFO destination"),
            )),
        )
        K.ENG_WAVE -> listOf(
            Group("WAVE", listOf(
                P(K.WT_TABLE, "TABLE", 0, 7, 0, Fmt.ENUM, WT_TABLES, "Wavetable"),
                P(K.WT_POS, "POS", 0, 255, 0x40, help = "Wavetable position (morph)"),
                P(K.WT_ENVAMT, "ENVAMT", -127, 127, 0, Fmt.SIGNED, help = "Mod envelope -> position"),
                P(K.WT_ENVDEC, "ENVDEC", 0, 255, 0x90, help = "Mod envelope decay"),
                P(K.WT_LFORATE, "LFORT", 0, 255, 0x70, help = "LFO rate"),
                P(K.WT_LFOAMT, "LFOAMT", 0, 255, 0, help = "LFO -> position"),
                P(K.WT_SPREAD, "SPREAD", 0, 255, 0x30, help = "Stereo detune"),
                P(K.WT_CUT, "CUTOFF", 0, 255, 0xFF, help = "Lowpass cutoff (FF = off)"),
                P(K.WT_RES, "RESO", 0, 255, 0x20, help = "Filter resonance"),
            )),
            Group("AMP ENV", listOf(
                P(K.WT_AA, "ATTACK", 0, 255, 0x08, help = ADSR_HELP), P(K.WT_AD, "DECAY", 0, 255, 0x90, help = ADSR_HELP),
                P(K.WT_AS, "SUSTN", 0, 255, 0xC0), P(K.WT_AR, "RELEAS", 0, 255, 0x80, help = ADSR_HELP),
            )),
        )
        K.ENG_PLUCK -> listOf(Group("PLUCK", listOf(
            P(K.PL_DECAY, "DECAY", 0, 255, 0x80, help = "String sustain time"),
            P(K.PL_DAMP, "DAMP", 0, 255, 0x50, help = "High frequency damping"),
            P(K.PL_EXCITE, "EXCITE", 0, 255, 0xA0, help = "Pluck brightness"),
            P(K.PL_POS, "POSITN", 0, 255, 0x40, help = "Pluck position on string"),
            P(K.PL_BODY, "BODY", 0, 255, 0x30, help = "Body resonance"),
            P(K.PL_REL, "RELEAS", 0, 255, 0x40, help = "Damping after note off"),
        )))
        else -> listOf(
            Group("CHIP", listOf(
                P(K.CH_WAVE, "WAVE", 0, 7, 2, Fmt.ENUM, CHIP_WAVES, "Pulse duty / waveform / noise"),
                P(K.CH_SWEEP, "SWEEP", -64, 64, 0, Fmt.SIGNED, help = "Pitch sweep speed"),
                P(K.CH_VIBD, "VIBDEP", 0, 255, 0, help = "Vibrato depth"),
                P(K.CH_VIBS, "VIBSPD", 0, 255, 0x60, help = "Vibrato speed"),
            )),
            Group("ENV", listOf(
                P(K.CH_AA, "ATTACK", 0, 255, 0, help = ADSR_HELP), P(K.CH_AD, "DECAY", 0, 255, 0x80, help = ADSR_HELP),
                P(K.CH_AS, "SUSTN", 0, 255, 0x90), P(K.CH_AR, "RELEAS", 0, 255, 0x50, help = ADSR_HELP),
            )),
        )
    }

    fun engineParams(engine: Int, model: Int = 0): List<P> = engineGroups(engine, model).flatMap { it.params }

    // ---------------------------------------------------------------- track params
    val INS_TYPES = arrayOf("OFF", "DRIVE", "CRUSH", "CHORUS", "PHASER", "FLANGR", "FILTER", "TREMOL", "RING")
    val INS_LABELS = arrayOf(
        arrayOf("-", "-", "-"),
        arrayOf("GAIN", "TONE", "MODE"),
        arrayOf("BITS", "RATE", "-"),
        arrayOf("RATE", "DEPTH", "FDBK"),
        arrayOf("RATE", "DEPTH", "FDBK"),
        arrayOf("RATE", "DEPTH", "FDBK"),
        arrayOf("CUTOFF", "RESO", "MODE"),
        arrayOf("RATE", "DEPTH", "MODE"),
        arrayOf("FREQ", "-", "-"),
    )
    val INS_HELP = arrayOf(
        "No insert effect",
        "MODE: 00-54 soft, 55-A9 hard clip, AA-FF wavefold",
        "BITS: 00 = 16 bit ... FF = 1 bit. RATE: downsampling",
        "Stereo chorus",
        "6-stage stereo phaser",
        "Stereo flanger (FDBK for jet sounds)",
        "MODE: 00-54 lowpass, 55-A9 bandpass, AA-FF highpass",
        "MODE: 00-7F tremolo, 80-FF autopan",
        "Ring modulator, FREQ 20Hz-2.5kHz",
    )
    val RATIOS = arrayOf("1:1", "1.5:1", "2:1", "3:1", "4:1", "6:1", "8:1", "12:1", "20:1", "INF")
    val SC_MODES = arrayOf("DUCK", "COMP")
    val ONOFF = arrayOf("OFF", "ON")

    val T_VOL = P(K.T_VOL, "VOLUME", 0, 255, 0xC0, help = "Track fader (C0 = 0 dB)")
    val T_PAN = P(K.T_PAN, "PAN", 0, 255, 0x80, help = "Pan (80 = center)")
    val T_WIDTH = P(K.T_WIDTH, "WIDTH", 0, 255, 0x80, help = "Stereo width: 00 mono, 80 normal, FF extra wide")
    val T_SDLY = P(K.T_SEND_DLY, "DLY SND", 0, 255, 0, help = "Send to delay")
    val T_SREV = P(K.T_SEND_REV, "REV SND", 0, 255, 0, help = "Send to reverb")
    val T_MUTE = P(K.T_MUTE, "MUTE", 0, 1, 0, Fmt.ENUM, ONOFF)
    val T_SOLO = P(K.T_SOLO, "SOLO", 0, 1, 0, Fmt.ENUM, ONOFF)
    val T_INS = P(K.T_INS_TYPE, "TYPE", 0, INS_TYPES.size - 1, 0, Fmt.ENUM, INS_TYPES, "Insert effect type")
    fun insP(k: Int, type: Int) = P(K.T_INS_P1 + k, INS_LABELS[type.coerceIn(0, INS_LABELS.size - 1)][k], 0, 255, 0x80,
        help = INS_HELP[type.coerceIn(0, INS_HELP.size - 1)])
    val T_INS_MIX = P(K.T_INS_MIX, "MIX", 0, 255, 0xFF, help = "Insert dry/wet")
    val T_EQ = listOf(
        P(K.T_EQ_LG, "LO GAIN", -15, 15, 0, Fmt.SIGNED, help = "Low shelf gain (dB)"),
        P(K.T_EQ_LF, "LO FREQ", 0, 255, 0x50, help = "Low shelf frequency 20Hz-1kHz"),
        P(K.T_EQ_MG, "MID GN", -15, 15, 0, Fmt.SIGNED, help = "Mid peak gain (dB)"),
        P(K.T_EQ_MF, "MID FRQ", 0, 255, 0x80, help = "Mid frequency 100Hz-10kHz"),
        P(K.T_EQ_MQ, "MID Q", 0, 255, 0x60, help = "Mid bandwidth (higher = narrower)"),
        P(K.T_EQ_HG, "HI GAIN", -15, 15, 0, Fmt.SIGNED, help = "High shelf gain (dB)"),
        P(K.T_EQ_HF, "HI FREQ", 0, 255, 0x90, help = "High shelf frequency 1k-16kHz"),
    )
    val T_COMP = listOf(
        P(K.T_CMP_THR, "THRESH", -60, 0, 0, Fmt.SIGNED, help = "Compressor threshold (dB)"),
        P(K.T_CMP_RAT, "RATIO", 0, 9, 0, Fmt.ENUM, RATIOS, "Ratio (1:1 = compressor off)"),
        P(K.T_CMP_ATK, "ATTACK", 0, 255, 0x20, help = "Attack 0.1-100 ms"),
        P(K.T_CMP_REL, "RELEAS", 0, 255, 0x40, help = "Release 10 ms - 1 s"),
        P(K.T_CMP_MAK, "MAKEUP", 0, 24, 0, Fmt.DEC, help = "Makeup gain (dB)"),
    )
    val T_LIM = listOf(
        P(K.T_LIM_CEIL, "CEIL", -24, 0, 0, Fmt.SIGNED, help = "Limiter ceiling (dB)"),
        P(K.T_LIM_REL, "RELEAS", 0, 255, 0x40, help = "Limiter release"),
    )
    val T_SC = listOf(
        P(K.T_SC_MODE, "MODE", 0, 1, 0, Fmt.ENUM, SC_MODES, "DUCK = pumping gain; COMP = keys this track's compressor"),
        P(K.T_SC_DEPTH, "DEPTH", 0, 255, 0xC0, help = "Duck depth"),
        P(K.T_SC_ATK, "ATTACK", 0, 255, 0x10, help = "Duck attack"),
        P(K.T_SC_REL, "RELEAS", 0, 255, 0x60, help = "Duck release"),
    )
    val ALL_TRACK = listOf(T_VOL, T_PAN, T_MUTE, T_SOLO, T_INS, insP(0, 0), insP(1, 0), insP(2, 0), T_INS_MIX) + T_EQ + T_COMP +
        listOf(T_WIDTH) + T_LIM + listOf(T_SDLY, T_SREV, P(K.T_SC_MASK, "MASK", 0, 255, 0)) + T_SC

    // ---------------------------------------------------------------- global params
    val DLY_TIMES = arrayOf("1/64", "1/32", "1/16T", "1/16", "1/16D", "1/8T", "1/8", "1/8D", "1/4T", "1/4", "1/4D", "1/2", "1/2D", "1/1")
    val G_TEMPO = P(K.G_TEMPO, "TEMPO", 40, 300, 120, Fmt.DEC, help = "Beats per minute (4 steps per beat)")
    val G_SWING = P(K.G_SWING, "SWING", 0, 80, 0, Fmt.DEC, help = "Swing amount %")
    val G_DELAY = listOf(
        P(K.G_DLY_TIME, "TIME", 0, 13, 7, Fmt.ENUM, DLY_TIMES, "Tempo-synced delay time"),
        P(K.G_DLY_FB, "FEEDBK", 0, 255, 0x60, help = "Delay feedback"),
        P(K.G_DLY_PP, "PINGPG", 0, 255, 0xC0, help = "Ping-pong amount"),
        P(K.G_DLY_LC, "LOWCUT", 0, 255, 0x40, help = "Feedback high-pass"),
        P(K.G_DLY_HC, "HICUT", 0, 255, 0xA0, help = "Feedback low-pass"),
        P(K.G_DLY_RET, "RETURN", 0, 255, 0xC0, help = "Delay return level"),
        P(K.G_DLY_REV, ">REVERB", 0, 255, 0x40, help = "Delay output sent into reverb"),
    )
    val G_REVERB = listOf(
        P(K.G_REV_SIZE, "SIZE", 0, 255, 0xA0, help = "Room size / decay"),
        P(K.G_REV_DAMP, "DAMP", 0, 255, 0x80, help = "High frequency damping"),
        P(K.G_REV_PRE, "PREDLY", 0, 255, 0x20, help = "Pre-delay 0-250 ms"),
        P(K.G_REV_WID, "WIDTH", 0, 255, 0xFF, help = "Stereo width"),
        P(K.G_REV_LC, "LOWCUT", 0, 255, 0x60, help = "Input high-pass"),
        P(K.G_REV_RET, "RETURN", 0, 255, 0xC0, help = "Reverb return level"),
    )
    val G_MASTER = listOf(
        P(K.G_M_LO, "LO EQ", -12, 12, 0, Fmt.SIGNED, help = "Master low shelf (dB)"),
        P(K.G_M_HI, "HI EQ", -12, 12, 0, Fmt.SIGNED, help = "Master high shelf (dB)"),
        P(K.G_M_THR, "THRESH", -40, 0, -10, Fmt.SIGNED, help = "Glue compressor threshold (dB)"),
        P(K.G_M_RAT, "RATIO", 0, 9, 2, Fmt.ENUM, RATIOS, "Glue compressor ratio"),
        P(K.G_M_ATK, "ATTACK", 0, 255, 0x50, help = "Glue attack"),
        P(K.G_M_REL, "RELEAS", 0, 255, 0x50, help = "Glue release"),
        P(K.G_M_MAK, "MAKEUP", 0, 24, 2, Fmt.DEC, help = "Glue makeup gain (dB)"),
        P(K.G_M_LIM, "LIMIT", -12, 0, -1, Fmt.SIGNED, help = "Master limiter ceiling (dB)"),
        P(K.G_M_VOL, "VOLUME", 0, 255, 0xC0, help = "Master volume (C0 = 0 dB)"),
    )
    val ALL_GLOBAL = listOf(G_TEMPO, G_SWING) + G_DELAY + G_REVERB + G_MASTER
}
