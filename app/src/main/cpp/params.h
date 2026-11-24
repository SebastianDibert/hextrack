// Shared parameter layout. MUST stay in sync with core/src/main/kotlin/com/hextrak/core/Defs.kt
#pragma once

namespace ht {

constexpr int NUM_TRACKS = 8;
constexpr int SONG_ROWS = 256;
constexpr int NUM_CHAINS = 255;
constexpr int NUM_PHRASES = 255;
constexpr int NUM_INSTR = 128;
constexpr int STEPS = 16;
constexpr int NUM_IPARAMS = 48;
constexpr int NUM_TPARAMS = 32;
constexpr int NUM_GPARAMS = 32;
constexpr int NOTE_OFF = 120;
constexpr int TICKS_PER_STEP = 6;
constexpr int VOICES_PER_TRACK = 4;

// ---- Instrument common params
enum { I_TYPE = 0, I_VOL = 1, I_TRANSP = 2, I_FINE = 3, I_LENGTH = 4, I_E0 = 8 };

enum { ENG_FM = 0, ENG_ACID, ENG_DRUM, ENG_VA, ENG_WAVE, ENG_PLUCK, ENG_CHIP, NUM_ENGINES };

// FM4: op k params at FM_OP0 + k*6 + {0 ratio, 1 level, 2 attack, 3 decay, 4 sustain, 5 release}
enum { FM_ALGO = I_E0, FM_FDBK, FM_VELS, FM_OP0 };
// ACID (303 style mono bass)
enum { AC_WAVE = I_E0, AC_CUT, AC_RES, AC_ENV, AC_DEC, AC_ACC, AC_SLIDE, AC_DRIVE };
// DRUM
enum { DR_MODEL = I_E0, DR_P1, DR_P2, DR_P3, DR_P4, DR_P5, DR_P6 };
enum { DM_KICK = 0, DM_SNARE, DM_CLHAT, DM_OPHAT, DM_CLAP, DM_TOM, DM_RIM, DM_COWBELL, DM_CYMBAL, DM_SHAKER, DM_ZAP, NUM_DM };
// VA (virtual analog)
enum { VA_OSC1 = I_E0, VA_OSC2, VA_SEMI, VA_DETUNE, VA_MIX, VA_PW, VA_SUB, VA_UNI, VA_SPREAD, VA_FTYPE, VA_CUT, VA_RES,
       VA_FENV, VA_KEYTRK, VA_FA, VA_FD, VA_FS, VA_FR, VA_AA, VA_AD, VA_AS, VA_AR, VA_LFORATE, VA_LFOAMT, VA_LFODEST,
       VA_GLIDE, VA_DRIVE };
// WAVE (wavetable)
enum { WT_TABLE = I_E0, WT_POS, WT_ENVAMT, WT_ENVDEC, WT_LFORATE, WT_LFOAMT, WT_SPREAD, WT_CUT, WT_RES, WT_AA, WT_AD, WT_AS, WT_AR };
// PLUCK (Karplus-Strong)
enum { PL_DECAY = I_E0, PL_DAMP, PL_EXCITE, PL_POS, PL_BODY, PL_REL };
// CHIP
enum { CH_WAVE = I_E0, CH_AA, CH_AD, CH_AS, CH_AR, CH_SWEEP, CH_VIBD, CH_VIBS };

// ---- Track params
enum {
    T_VOL = 0, T_PAN, T_MUTE, T_SOLO, T_INS_TYPE, T_INS_P1, T_INS_P2, T_INS_P3, T_INS_MIX,
    T_EQ_LG, T_EQ_LF, T_EQ_MG, T_EQ_MF, T_EQ_MQ, T_EQ_HG, T_EQ_HF,
    T_CMP_THR, T_CMP_RAT, T_CMP_ATK, T_CMP_REL, T_CMP_MAK, T_WIDTH, T_LIM_CEIL, T_LIM_REL,
    T_SEND_DLY, T_SEND_REV, T_SC_MASK, T_SC_MODE, T_SC_DEPTH, T_SC_ATK, T_SC_REL
};

// ---- Global params
enum {
    G_TEMPO = 0, G_SWING, G_DLY_TIME, G_DLY_FB, G_DLY_PP, G_DLY_LC, G_DLY_HC, G_DLY_RET, G_DLY_REV,
    G_REV_SIZE, G_REV_DAMP, G_REV_PRE, G_REV_WID, G_REV_LC, G_REV_RET,
    G_M_THR, G_M_RAT, G_M_ATK, G_M_REL, G_M_MAK, G_M_LIM, G_M_VOL, G_M_LO, G_M_HI
};

// ---- Phrase FX commands
enum { FX_NONE = 0, FX_ARP, FX_SLD, FX_ACC, FX_PUP, FX_PDN, FX_VIB, FX_RTG, FX_KIL, FX_DLY, FX_CHA, FX_CUT, FX_RES,
       FX_PAN, FX_TMP, FX_DSN, FX_RSN, FX_TIM, FX_VSL, FX_HOP, NUM_FX };

// ---- Insert effect types
enum { INS_OFF = 0, INS_DRIVE, INS_CRUSH, INS_CHORUS, INS_PHASER, INS_FLANGER, INS_FILTER, INS_TREMOLO, INS_RING, NUM_INS };

// ---- Play modes
enum { PLAY_SONG = 0, PLAY_CHAIN = 1, PLAY_PHRASE = 2 };

// ---- State export layout (ints)
constexpr int ST_PER_TRACK = 8; // active, songRow, chain, chainStep, phrase, phraseStep, note, instr
constexpr int ST_GLOBAL = NUM_TRACKS * ST_PER_TRACK; // playing, mode, tempo*10, cpu*1000, tick
constexpr int ST_SIZE = ST_GLOBAL + 8;
// Meter export layout (floats): per track peakL, peakR, gainReduction; then masterL, masterR, cpu
constexpr int MT_SIZE = NUM_TRACKS * 3 + 4;
constexpr int SCOPE_SIZE = 256;

} // namespace ht
