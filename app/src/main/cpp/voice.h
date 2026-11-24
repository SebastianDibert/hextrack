#pragma once
#include "dsp.h"
#include "params.h"

namespace ht {

struct VoiceCtx {
    float pitchMod = 0;  // semitones (arp, slides, vibrato)
    float timbre = 0;    // -1..1 (TIM command)
    int cutOv = -1;      // CUT command override (-1 = none)
    int resOv = -1;      // RES command override
    float sr = 48000;
};

// 4-pole zero-delay-feedback ladder with tanh saturation (used by ACID)
struct Ladder {
    float s[4] = {0, 0, 0, 0};
    inline float process(float x, float g, float k) {
        float G = g / (1.f + g);
        float inv = 1.f / (1.f + g);
        float S1 = s[0] * inv, S2 = s[1] * inv, S3 = s[2] * inv, S4 = s[3] * inv;
        float G2 = G * G, G3 = G2 * G, G4 = G3 * G;
        float sig = G3 * S1 + G2 * S2 + G * S3 + S4;
        float u = (x - k * sig) / (1.f + k * G4);
        u = fastTanh(u);
        float y = u;
        for (int i = 0; i < 4; i++) {
            float v = (y - s[i]) * G;
            float o = v + s[i];
            s[i] = o + v;
            y = o;
        }
        return y;
    }
    void reset() { s[0] = s[1] = s[2] = s[3] = 0; }
};

constexpr int PLUCK_BUF = 8192;

struct Voice {
    bool active = false, gate = false, accent = false;
    int engine = 0, instr = 0;
    uint32_t age = 0;
    float note = 48, target = 48, glideCoef = 1;
    float vel = 1;
    float lastPm = 0;
    float peak = 0;
    Rng rng;

    ADSR env[4];
    float ph[8] = {0}, ph2[8] = {0};
    float fbp[2] = {0, 0};
    float lfoPh = 0;
    SVF svf[4];
    Ladder ladder;
    OnePole op1, op2;

    // ACID
    float fenv = 0, fenvCoef = 0, accEnv = 0, accSm = 0, accLevel = 0;
    // DRUM
    int model = 0;
    float penv = 0, aenv = 0, nenv = 0, cenv = 0, env2 = 0;
    float pCoef = 0, aCoef = 0, nCoef = 0, cCoef = 0, e2Coef = 0;
    float f0 = 0, fStart = 0, dGain = 1, drive = 0, param[6] = {0};
    int burst = 0, bursts = 0, timer = 0, spreadS = 0;
    float burstCoef = 0, attackInc = 0;
    // WAVE
    float modEnv = 0;
    // PLUCK
    float* pbuf = nullptr;
    int pw = 0;
    float plp = 0;
    // CHIP
    uint32_t lfsr = 0x7fff;
    float noiseCnt = 0, sweepAcc = 0, noiseOut = 1;

    void noteOn(const int* ip, float note, float vel, bool accent, float fromNote, float sr);
    void slideTo(float note, float timeSec, bool accent, float sr);
    void noteOff();
    void choke(float sr);
    void kill() { active = false; gate = false; }
    void render(const int* ip, float* L, float* R, int n, const VoiceCtx& c);

private:
    void drumOn(const int* ip, float sr);
    void pluckOn(const int* ip, float sr);
    void renderFM(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
    void renderAcid(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
    void renderDrum(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
    void renderVA(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
    void renderWT(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
    void renderPluck(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
    void renderChip(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain);
};

} // namespace ht
