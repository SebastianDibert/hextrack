#include "voice.h"
#include "wavetable.h"

namespace ht {

static const float kRatios[24] = {0.25f, 0.5f, 0.75f, 1.f, 1.41421f, 1.5f, 2.f, 2.5f, 3.f, 3.5f, 4.f, 4.5f,
                                  5.f, 6.f, 7.f, 8.f, 9.f, 10.f, 11.f, 12.f, 13.f, 14.f, 15.f, 16.f};
static const float kMetal[6] = {205.3f, 304.4f, 369.6f, 522.7f, 540.0f, 800.0f};
static const int kCarriers[8] = {0x1, 0x1, 0x1, 0x1, 0x5, 0x7, 0x7, 0xF};

static inline float wrap1(float p) { return p >= 1.f ? p - 1.f : p; }

void Voice::noteOn(const int* ip, float n, float v, bool acc, float fromNote, float sr) {
    engine = ip[I_TYPE];
    active = true;
    gate = true;
    accent = acc;
    vel = v;
    target = n;
    note = n;
    glideCoef = 1.f;
    age = 0;
    peak = 1.f;
    switch (engine) {
        case ENG_FM:
            for (int k = 0; k < 4; k++) { env[k].on(); ph[k] = 0; }
            fbp[0] = fbp[1] = 0;
            break;
        case ENG_ACID:
            fenv = 1.f;
            accLevel = acc ? (0.25f + 0.75f * ip[AC_ACC] / 255.f) : 0.f;
            if (acc) accEnv = accLevel;
            env[0].set(0.0015f, 0.f, 1.f, 0.008f, sr);
            env[0].on();
            break;
        case ENG_DRUM:
            drumOn(ip, sr);
            break;
        case ENG_VA:
            env[0].on();
            env[1].on();
            if (ip[VA_GLIDE] > 0 && fromNote >= 0) {
                note = fromNote;
                glideCoef = 1.f - expCoef(0.005f + ip[VA_GLIDE] / 255.f * 0.8f, sr);
            }
            break;
        case ENG_WAVE:
            env[0].on();
            modEnv = 1.f;
            break;
        case ENG_PLUCK:
            pluckOn(ip, sr);
            break;
        case ENG_CHIP:
            env[0].on();
            sweepAcc = 0;
            lfoPh = 0;
            break;
        default:
            active = false;
            break;
    }
}

void Voice::slideTo(float n, float timeSec, bool acc, float sr) {
    target = n;
    glideCoef = 1.f - expCoef(timeSec, sr);
    if (engine == ENG_ACID && acc) {
        accLevel = 0.6f;
        accEnv = accLevel;
    }
    if (!gate) {
        gate = true;
        for (int k = 0; k < 4; k++) if (!env[k].idle()) env[k].on();
        if (engine == ENG_ACID || engine == ENG_WAVE || engine == ENG_CHIP || engine == ENG_VA) env[0].on();
        if (engine == ENG_VA) env[1].on();
    }
}

void Voice::noteOff() {
    gate = false;
    switch (engine) {
        case ENG_FM: for (int k = 0; k < 4; k++) env[k].off(); break;
        case ENG_VA: env[0].off(); env[1].off(); break;
        case ENG_ACID:
        case ENG_WAVE:
        case ENG_CHIP: env[0].off(); break;
        default: break; // drums are one-shots, pluck uses gate for damping
    }
}

void Voice::choke(float sr) {
    if (engine == ENG_DRUM) {
        aCoef = std::min(aCoef, expCoef(0.006f, sr));
        nCoef = std::min(nCoef, expCoef(0.006f, sr));
        e2Coef = std::min(e2Coef, expCoef(0.006f, sr));
    } else {
        noteOff();
    }
}

void Voice::render(const int* ip, float* L, float* R, int n, const VoiceCtx& c) {
    if (ip[I_TYPE] != engine) { active = false; return; }
    if (note != target) {
        float k = 1.f - powf(1.f - glideCoef, (float)n);
        note += (target - note) * k;
        if (fabsf(target - note) < 0.001f) note = target;
    }
    float gain = lvlCurve(ip[I_VOL]) * 1.7f * vel * vel;
    age += n;
    switch (engine) {
        case ENG_FM: renderFM(ip, L, R, n, c, gain); break;
        case ENG_ACID: renderAcid(ip, L, R, n, c, gain); break;
        case ENG_DRUM: renderDrum(ip, L, R, n, c, gain); break;
        case ENG_VA: renderVA(ip, L, R, n, c, gain); break;
        case ENG_WAVE: renderWT(ip, L, R, n, c, gain); break;
        case ENG_PLUCK: renderPluck(ip, L, R, n, c, gain); break;
        case ENG_CHIP: renderChip(ip, L, R, n, c, gain); break;
        default: active = false; break;
    }
}

// ------------------------------------------------------------------ FM4
void Voice::renderFM(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    const float sr = c.sr;
    float baseHz = noteToHz(note + c.pitchMod + ip[I_FINE] * 0.01f);
    float inc[4], lvl[4];
    for (int k = 0; k < 4; k++) {
        const int* o = ip + FM_OP0 + k * 6;
        int ri = o[0] < 0 ? 0 : (o[0] > 23 ? 23 : o[0]);
        inc[k] = std::min(0.49f, baseHz * kRatios[ri] / sr);
        lvl[k] = lvlCurve(o[1]);
        env[k].set(envTime(o[2]), envTime(o[3]), o[4] / 255.f, envTime(o[5]), sr);
    }
    int algo = ip[FM_ALGO] & 7;
    float fb = ip[FM_FDBK] / 255.f * 0.3f;
    float vs = ip[FM_VELS] / 255.f;
    float md = 1.4f * (1.f - vs * (1.f - vel)) * clampf(1.f + c.timbre, 0.f, 2.f);
    int cm = kCarriers[algo];
    int nc = __builtin_popcount(cm);
    float outScale = gain * 0.55f / sqrtf((float)nc);
    for (int i = 0; i < n; i++) {
        float a0 = env[0].next() * lvl[0], a1 = env[1].next() * lvl[1];
        float a2 = env[2].next() * lvl[2], a3 = env[3].next() * lvl[3];
        float o3 = sinCycle(ph[3] + fb * (fbp[0] + fbp[1])) * a3;
        fbp[1] = fbp[0];
        fbp[0] = o3;
        float o0, o1, o2, out;
        switch (algo) {
            case 0: o2 = sinCycle(ph[2] + o3 * md) * a2; o1 = sinCycle(ph[1] + o2 * md) * a1; out = sinCycle(ph[0] + o1 * md) * a0; break;
            case 1: o2 = sinCycle(ph[2]) * a2; o1 = sinCycle(ph[1] + (o2 + o3) * md) * a1; out = sinCycle(ph[0] + o1 * md) * a0; break;
            case 2: o2 = sinCycle(ph[2]) * a2; o1 = sinCycle(ph[1] + o2 * md) * a1; out = sinCycle(ph[0] + (o1 + o3) * md) * a0; break;
            case 3: o2 = sinCycle(ph[2] + o3 * md) * a2; o1 = sinCycle(ph[1]) * a1; out = sinCycle(ph[0] + (o1 + o2) * md) * a0; break;
            case 4: o1 = sinCycle(ph[1]) * a1; o0 = sinCycle(ph[0] + o1 * md) * a0; o2 = sinCycle(ph[2] + o3 * md) * a2; out = o0 + o2; break;
            case 5: out = (sinCycle(ph[0] + o3 * md) * a0) + (sinCycle(ph[1] + o3 * md) * a1) + (sinCycle(ph[2] + o3 * md) * a2); break;
            case 6: o2 = sinCycle(ph[2] + o3 * md) * a2; out = sinCycle(ph[0]) * a0 + sinCycle(ph[1]) * a1 + o2; break;
            default: out = sinCycle(ph[0]) * a0 + sinCycle(ph[1]) * a1 + sinCycle(ph[2]) * a2 + o3; break;
        }
        for (int k = 0; k < 4; k++) ph[k] = wrap1(ph[k] + inc[k]);
        float s = out * outScale;
        L[i] += s;
        R[i] += s;
    }
    bool alive = false;
    for (int k = 0; k < 4; k++) if ((cm >> k) & 1) alive |= !env[k].idle();
    if (!alive) active = false;
}

// ------------------------------------------------------------------ ACID
void Voice::renderAcid(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    const float sr = c.sr;
    int cut = c.cutOv >= 0 ? c.cutOv : ip[AC_CUT];
    int res = c.resOv >= 0 ? c.resOv : ip[AC_RES];
    float baseCut = 35.f * exp2f(cut / 255.f * 6.8f);
    float envMod = clampf(ip[AC_ENV] / 255.f + c.timbre * 0.5f, 0.f, 1.5f);
    float d = ip[AC_DEC] / 255.f;
    float decT = accent ? 0.2f : 0.04f + d * sqrtf(d) * 2.0f;
    float fCoef = expCoef(decT, sr);
    float accCoef = expCoef(0.18f, sr);
    float smK = 1.f - expCoef(0.035f, sr);
    float k = res / 255.f * 3.95f;
    float comp = 1.f + k * 0.45f;
    float drv = ip[AC_DRIVE] / 255.f;
    float dg = 1.f + drv * 12.f;
    float dn = 1.f / fastTanh(dg);
    float hz = noteToHz(note + c.pitchMod + ip[I_FINE] * 0.01f);
    float dt = std::min(0.49f, hz / sr);
    bool sq = ip[AC_WAVE] != 0;
    float g = 0;
    float ampBoost = 1.f + accLevel * 0.7f;
    for (int i = 0; i < n; i++) {
        if ((i & 7) == 0) {
            float fc = baseCut * exp2f(envMod * 4.5f * fenv + accSm * 2.2f);
            fc = std::min(fc, sr * 0.45f);
            g = tanf(PI * fc / sr);
        }
        float o = sq ? pulseBL(ph[0], dt, 0.5f) : sawBL(ph[0], dt);
        ph[0] = wrap1(ph[0] + dt);
        float y = ladder.process(o * 0.8f, g, k) * comp;
        fenv *= fCoef;
        accEnv *= accCoef;
        accSm += (accEnv - accSm) * smK;
        y *= env[0].next() * ampBoost;
        if (drv > 0.f) y = fastTanh(y * dg) * dn;
        float s = y * gain * 0.7f;
        L[i] += s;
        R[i] += s;
    }
    if (env[0].idle()) active = false;
}

// ------------------------------------------------------------------ DRUM
void Voice::drumOn(const int* ip, float sr) {
    model = ip[DR_MODEL] < 0 ? 0 : (ip[DR_MODEL] >= NUM_DM ? NUM_DM - 1 : ip[DR_MODEL]);
    for (int k = 0; k < 6; k++) param[k] = ip[DR_P1 + k] / 255.f;
    float tune = exp2f((ip[DR_P1] - 128) / 128.f);
    float r = exp2f((note - 48.f) / 12.f);
    penv = aenv = nenv = cenv = env2 = 1.f;
    for (int k = 0; k < 8; k++) ph[k] = 0;
    for (int k = 0; k < 4; k++) svf[k].reset();
    drive = param[5];
    dGain = 1.f + drive * 6.f;
    burst = 0; timer = 0;
    float p1 = param[1] * param[1], p3 = param[3] * param[3];
    switch (model) {
        case DM_KICK:
            f0 = 50.f * r * tune;
            fStart = f0 * (1.f + param[2] * 14.f);
            pCoef = expCoef(0.003f + p3 * 0.25f, sr);
            aCoef = expCoef(0.03f + p1 * 1.6f, sr);
            cCoef = expCoef(0.0015f, sr);
            break;
        case DM_SNARE:
            f0 = 185.f * r * tune;
            pCoef = expCoef(0.012f, sr);
            aCoef = expCoef(0.02f + p1 * 0.5f, sr);
            nCoef = expCoef(0.03f + p3 * 0.6f, sr);
            svf[0].set(900.f * exp2f(param[4] * 3.3f), 0.15f, sr);
            break;
        case DM_CLHAT:
        case DM_OPHAT: {
            f0 = tune * r;
            aCoef = expCoef(model == DM_CLHAT ? 0.006f + p1 * 0.3f : 0.08f + p1 * 1.8f, sr);
            float bp = 3500.f * exp2f(param[2] * 1.9f);
            svf[0].set(bp, 0.35f, sr);
            svf[1].set(bp * 0.55f, 0.1f, sr);
            break;
        }
        case DM_CLAP:
            svf[0].set(1100.f * tune * r, 0.2f + param[4] * 0.6f, sr);
            spreadS = std::max(1, (int)((0.004f + param[2] * 0.022f) * sr));
            bursts = 1 + (int)(param[3] * 6.99f);
            burstCoef = expCoef(spreadS / sr * 0.35f, sr);
            aCoef = expCoef(0.05f + p1 * 0.9f, sr);
            break;
        case DM_TOM:
            f0 = 100.f * r * tune;
            fStart = f0 * (1.f + param[2] * 2.5f);
            pCoef = expCoef(0.01f + p3 * 0.3f, sr);
            aCoef = expCoef(0.05f + p1 * 1.2f, sr);
            nCoef = expCoef(0.02f, sr);
            svf[0].set(f0 * 6.f, 0.1f, sr);
            break;
        case DM_RIM:
            f0 = 1650.f * r * tune;
            aCoef = expCoef(0.004f + p1 * 0.06f, sr);
            nCoef = expCoef(0.003f, sr);
            svf[0].set(2000.f * exp2f(param[2] * 2.f), 0.5f, sr);
            break;
        case DM_COWBELL:
            f0 = tune * r;
            svf[0].set(2640.f * exp2f((param[2] - 0.5f) * 1.5f), 0.55f, sr);
            aCoef = expCoef(0.012f, sr);
            e2Coef = expCoef(0.05f + p1 * 0.9f, sr);
            break;
        case DM_CYMBAL: {
            f0 = tune * r;
            aCoef = expCoef(0.3f + p1 * 3.5f, sr);
            float bp = 4500.f * exp2f(param[2] * 1.5f);
            svf[0].set(bp, 0.25f, sr);
            svf[1].set(3800.f, 0.1f, sr);
            break;
        }
        case DM_SHAKER:
            svf[0].set(std::min(sr * 0.45f, 2500.f * exp2f(param[0] * 2.2f) * r), 0.2f, sr);
            aCoef = expCoef(0.02f + p1 * 0.35f, sr);
            attackInc = 1.f / std::max(1.f, (0.001f + param[2] * 0.05f) * sr);
            env2 = 0.f;
            break;
        default: // ZAP
            f0 = 80.f * r * tune;
            fStart = 1.f + param[2] * 6.f; // octaves
            pCoef = expCoef(0.004f + p3 * 0.2f, sr);
            aCoef = expCoef(0.02f + p1 * 0.6f, sr);
            break;
    }
}

void Voice::renderDrum(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    (void)ip;
    const float sr = c.sr;
    const float isr = 1.f / sr;
    float pr = exp2f(c.pitchMod / 12.f);
    float dn = drive > 0.f ? 1.f / fastTanh(dGain) : 1.f;
    for (int i = 0; i < n; i++) {
        float s = 0;
        switch (model) {
            case DM_KICK: {
                float f = (f0 + (fStart - f0) * penv) * pr;
                ph[0] = wrap1(ph[0] + f * isr);
                s = sinCycle(ph[0]) * aenv + param[4] * cenv * rng.next() * 0.7f;
                penv *= pCoef; aenv *= aCoef; cenv *= cCoef;
                s *= 1.0f;
                break;
            }
            case DM_SNARE: {
                float f = f0 * (1.f + penv * 0.5f) * pr;
                ph[0] = wrap1(ph[0] + f * isr);
                ph[1] = wrap1(ph[1] + f * 1.78f * isr);
                float body = (sinCycle(ph[0]) * 0.65f + sinCycle(ph[1]) * 0.35f) * aenv * (1.f - param[2] * 0.5f);
                float nz = svf[0].hp(rng.next()) * nenv * param[2] * 1.3f;
                s = (body + nz) * 0.85f;
                penv *= pCoef; aenv *= aCoef; nenv *= nCoef;
                break;
            }
            case DM_CLHAT:
            case DM_OPHAT:
            case DM_CYMBAL: {
                float m = 0;
                for (int j = 0; j < 6; j++) {
                    ph[j] = wrap1(ph[j] + std::min(0.49f, kMetal[j] * f0 * pr * isr * (model == DM_CYMBAL ? 1.f : 1.6f)));
                    m += ph[j] < 0.5f ? 1.f : -1.f;
                }
                m *= (1.f / 6.f);
                float mix = param[3];
                float x = m * mix + rng.next() * (1.f - mix);
                x = svf[1].hp(svf[0].bp(x));
                s = x * aenv * (model == DM_CYMBAL ? 1.4f : 2.2f);
                aenv *= aCoef;
                break;
            }
            case DM_CLAP: {
                if (burst < bursts) {
                    if (++timer >= spreadS) {
                        timer = 0;
                        burst++;
                        if (burst < bursts) env2 = 1.f;
                    }
                    s = svf[0].bp(rng.next()) * env2 * 1.6f;
                    env2 *= burstCoef;
                    if (burst >= bursts) aenv = 0.85f;
                } else {
                    s = svf[0].bp(rng.next()) * aenv * 1.6f;
                    aenv *= aCoef;
                }
                break;
            }
            case DM_TOM: {
                float f = (f0 + (fStart - f0) * penv) * pr;
                ph[0] = wrap1(ph[0] + f * isr);
                s = sinCycle(ph[0]) * aenv + svf[0].lp(rng.next()) * nenv * param[4] * 0.8f;
                penv *= pCoef; aenv *= aCoef; nenv *= nCoef;
                break;
            }
            case DM_RIM: {
                ph[0] = wrap1(ph[0] + std::min(0.49f, f0 * pr * isr));
                ph[1] = wrap1(ph[1] + 455.f * pr * isr);
                s = (sinCycle(ph[0]) * 0.6f + (ph[1] < 0.5f ? 0.25f : -0.25f)) * aenv;
                s += svf[0].bp(rng.next()) * nenv * param[3] * 1.5f;
                s = fastTanh(s * 1.5f);
                aenv *= aCoef; nenv *= nCoef;
                break;
            }
            case DM_COWBELL: {
                ph[0] = wrap1(ph[0] + 540.f * f0 * pr * isr);
                ph[1] = wrap1(ph[1] + 800.f * f0 * pr * isr);
                float x = (ph[0] < 0.5f ? 0.5f : -0.5f) + (ph[1] < 0.5f ? 0.5f : -0.5f);
                x = svf[0].bp(x);
                s = x * (0.6f * aenv + 0.4f * env2) * 1.4f;
                aenv *= aCoef; env2 *= e2Coef;
                break;
            }
            case DM_SHAKER: {
                float a;
                if (env2 < 1.f) { env2 += attackInc; a = env2; }
                else { aenv *= aCoef; a = aenv; }
                s = svf[0].hp(rng.next()) * a * 0.8f;
                break;
            }
            default: { // ZAP
                float f = f0 * exp2f(fStart * penv) * pr;
                ph[0] = wrap1(ph[0] + std::min(0.49f, f * isr));
                s = sinCycle(ph[0]) * aenv;
                penv *= pCoef; aenv *= aCoef;
                break;
            }
        }
        if (drive > 0.f) s = fastTanh(s * dGain) * dn;
        s *= gain * 0.5f;
        L[i] += s;
        R[i] += s;
    }
    bool done;
    if (model == DM_CLAP) done = burst >= bursts && aenv < 1e-4f;
    else if (model == DM_SHAKER) done = env2 >= 1.f && aenv < 1e-4f;
    else if (model == DM_COWBELL) done = aenv < 1e-4f && env2 < 1e-4f;
    else if (model == DM_SNARE) done = aenv < 1e-4f && nenv < 1e-4f;
    else done = aenv < 1e-4f;
    if (done) active = false;
}

// ------------------------------------------------------------------ VA
static inline float vaOsc(int w, float ph, float dt, float pw) {
    switch (w) {
        case 0: return sawBL(ph, dt);
        case 1: return pulseBL(ph, dt, pw);
        case 2: return triNaive(ph);
        default: return sinCycle(ph);
    }
}

void Voice::renderVA(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    const float sr = c.sr;
    int nU = 1 + std::max(0, std::min(3, ip[VA_UNI]));
    float spread = ip[VA_SPREAD] / 255.f;
    int w1 = ip[VA_OSC1], w2 = ip[VA_OSC2];
    float mix = ip[VA_MIX] / 255.f, sub = ip[VA_SUB] / 255.f;
    float pwBase = 0.5f - ip[VA_PW] / 255.f * 0.45f;
    int ftype = ip[VA_FTYPE];
    int cut = c.cutOv >= 0 ? c.cutOv : ip[VA_CUT];
    int res = c.resOv >= 0 ? c.resOv : ip[VA_RES];
    float fenvAmt = ip[VA_FENV] / 127.f;
    float keytrk = ip[VA_KEYTRK] / 255.f;
    env[0].set(envTime(ip[VA_AA]), envTime(ip[VA_AD]), ip[VA_AS] / 255.f, envTime(ip[VA_AR]), sr);
    env[1].set(envTime(ip[VA_FA]), envTime(ip[VA_FD]), ip[VA_FS] / 255.f, envTime(ip[VA_FR]), sr);
    float lfoRate = 0.05f * exp2f(ip[VA_LFORATE] / 255.f * 9.f);
    float lfoAmt = ip[VA_LFOAMT] / 255.f;
    int dest = ip[VA_LFODEST];
    float drv = ip[VA_DRIVE] / 255.f;
    float dg = 1.f + drv * 5.f, dn = 1.f / fastTanh(dg);

    float lfo = sinCycle(lfoPh);
    lfoPh += lfoRate * n / sr;
    lfoPh -= floorf(lfoPh);

    float baseNote = note + c.pitchMod + ip[I_FINE] * 0.01f;
    float pitchL = dest == 0 ? lfo * lfoAmt * 2.f : 0.f;
    float hz1 = noteToHz(baseNote + pitchL);
    float hz2 = noteToHz(baseNote + pitchL + ip[VA_SEMI] + ip[VA_DETUNE] * 0.01f);
    float pw = clampf(pwBase + (dest == 2 ? lfo * lfoAmt * 0.4f : 0.f) + c.timbre * 0.3f, 0.05f, 0.95f);
    float ampL = dest == 3 ? 1.f - lfoAmt * (0.5f + 0.5f * lfo) : 1.f;
    float panL = dest == 4 ? lfo * lfoAmt : 0.f;

    float inc1[4], inc2[4], gl[4], gr[4];
    for (int u = 0; u < nU; u++) {
        float d = nU > 1 ? (2.f * u / (nU - 1) - 1.f) : 0.f;
        float ratio = exp2f(d * spread * 0.5f / 12.f);
        inc1[u] = std::min(0.49f, hz1 * ratio / sr);
        inc2[u] = std::min(0.49f, hz2 * ratio / sr);
        float pan = d * spread;
        gl[u] = std::min(1.f, 1.f - pan);
        gr[u] = std::min(1.f, 1.f + pan);
    }
    float uniNorm = 1.f / sqrtf((float)nU);
    float subInc = inc1[0] * 0.5f;
    float cutBase = cutoffHz(cut) * exp2f(keytrk * (baseNote - 48.f) / 12.f) * (dest == 1 ? exp2f(lfo * lfoAmt * 3.f) : 1.f);
    float rq = res / 255.f;
    float outL = std::min(1.f, 1.f - panL), outR = std::min(1.f, 1.f + panL);
    float amp0 = gain * 0.42f * ampL;

    for (int i = 0; i < n; i++) {
        if ((i & 15) == 0) {
            float fc = cutBase * exp2f(fenvAmt * env[1].v * 6.f);
            for (int k = 0; k < 4; k++) svf[k].set(fc, ftype == 1 ? rq * 0.9f : rq, sr);
        }
        env[1].next();
        float a = env[0].next();
        float sl = 0, sr_ = 0;
        for (int u = 0; u < nU; u++) {
            float o1 = vaOsc(w1, ph[u], inc1[u], pw);
            float o2 = w2 >= 4 ? rng.next() : vaOsc(w2, ph2[u], inc2[u], pw);
            ph[u] = wrap1(ph[u] + inc1[u]);
            ph2[u] = wrap1(ph2[u] + inc2[u]);
            float s = o1 * (1.f - mix) + o2 * mix;
            sl += s * gl[u];
            sr_ += s * gr[u];
        }
        sl *= uniNorm; sr_ *= uniNorm;
        if (sub > 0.f) {
            float so = pulseBL(ph[7], subInc, 0.5f) * sub;
            ph[7] = wrap1(ph[7] + subInc);
            sl += so; sr_ += so;
        }
        if (drv > 0.f) { sl = fastTanh(sl * dg) * dn; sr_ = fastTanh(sr_ * dg) * dn; }
        switch (ftype) {
            case 0: sl = svf[0].lp(sl); sr_ = svf[1].lp(sr_); break;
            case 1: sl = svf[2].lp(svf[0].lp(sl)); sr_ = svf[3].lp(svf[1].lp(sr_)); break;
            case 2: sl = svf[0].bp(sl) * 1.5f; sr_ = svf[1].bp(sr_) * 1.5f; break;
            default: sl = svf[0].hp(sl); sr_ = svf[1].hp(sr_); break;
        }
        float g = a * amp0;
        L[i] += sl * g * outL;
        R[i] += sr_ * g * outR;
    }
    if (env[0].idle()) active = false;
}

// ------------------------------------------------------------------ WAVE
void Voice::renderWT(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    const float sr = c.sr;
    int table = std::max(0, std::min(WT_TABLES - 1, ip[WT_TABLE]));
    float envAmt = ip[WT_ENVAMT] / 127.f;
    float lfoRate = 0.05f * exp2f(ip[WT_LFORATE] / 255.f * 8.f);
    float lfoAmt = ip[WT_LFOAMT] / 255.f * 0.5f;
    float spread = ip[WT_SPREAD] / 255.f;
    int cut = c.cutOv >= 0 ? c.cutOv : ip[WT_CUT];
    int res = c.resOv >= 0 ? c.resOv : ip[WT_RES];
    env[0].set(envTime(ip[WT_AA]), envTime(ip[WT_AD]), ip[WT_AS] / 255.f, envTime(ip[WT_AR]), sr);

    float lfo = sinCycle(lfoPh);
    lfoPh += lfoRate * n / sr;
    lfoPh -= floorf(lfoPh);
    float posA = ph2[7];
    float posB = clampf(ip[WT_POS] / 255.f + c.timbre * 0.5f + envAmt * modEnv + lfoAmt * lfo, 0.f, 1.f);
    if (age <= (uint32_t)n) posA = posB;
    ph2[7] = posB;
    modEnv *= powf(expCoef(envTime(ip[WT_ENVDEC]), sr), (float)n);

    float hz = noteToHz(note + c.pitchMod + ip[I_FINE] * 0.01f);
    float det = exp2f(spread * 0.25f / 12.f);
    float inc1 = std::min(0.49f, hz * det / sr), inc2 = std::min(0.49f, hz / det / sr);
    int level = wtLevel(hz * det * 1.02f, sr);
    bool filt = cut < 255;
    if (filt) {
        svf[0].set(cutoffHz(cut), res / 255.f, sr);
        svf[1].set(cutoffHz(cut), res / 255.f, sr);
    }
    float amp0 = gain * 0.45f;
    float invn = 1.f / n;
    for (int i = 0; i < n; i++) {
        float a = env[0].next();
        float p = posA + (posB - posA) * (i * invn);
        float l = wtRead(table, p, level, ph[0]);
        float r = spread > 0.f ? wtRead(table, p, level, ph[1]) : l;
        ph[0] = wrap1(ph[0] + inc1);
        ph[1] = wrap1(ph[1] + inc2);
        if (filt) { l = svf[0].lp(l); r = svf[1].lp(r); }
        L[i] += l * a * amp0;
        R[i] += r * a * amp0;
    }
    if (env[0].idle()) active = false;
}

// ------------------------------------------------------------------ PLUCK
void Voice::pluckOn(const int* ip, float sr) {
    if (!pbuf) { active = false; return; }
    float hz = noteToHz(note + ip[I_FINE] * 0.01f);
    float Ld = clampf(sr / hz, 2.f, PLUCK_BUF - 4.f);
    memset(pbuf, 0, sizeof(float) * PLUCK_BUF);
    int N = std::min(PLUCK_BUF - 1, (int)ceilf(Ld) + 1);
    float exc = ip[PL_EXCITE] / 255.f;
    float a = 0.03f + exc * 0.97f, z = 0;
    for (int i = 0; i < N; i++) { z += a * (rng.next() - z); pbuf[i] = z; }
    int pp = (int)(ip[PL_POS] / 255.f * 0.5f * Ld);
    if (pp > 0) for (int i = N - 1; i >= pp; i--) pbuf[i] -= pbuf[i - pp];
    float mx = 1e-6f;
    for (int i = 0; i < N; i++) mx = std::max(mx, fabsf(pbuf[i]));
    float sc = 0.9f / mx;
    for (int i = 0; i < N; i++) pbuf[i] *= sc;
    pw = N;
    plp = 0;
    svf[0].reset();
    svf[1].reset();
}

void Voice::renderPluck(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    if (!pbuf) { active = false; return; }
    const float sr = c.sr;
    float hz = noteToHz(note + c.pitchMod + ip[I_FINE] * 0.01f);
    float b = clampf(ip[PL_DAMP] / 255.f * 0.85f - c.timbre * 0.3f, 0.f, 0.95f);
    float Ld = clampf(sr / hz - b / (1.f - b), 2.f, PLUCK_BUF - 4.f);
    float dd = ip[PL_DECAY] / 255.f, rr = ip[PL_REL] / 255.f;
    float T60 = gate ? 0.08f + dd * dd * 10.f : 0.03f + rr * rr * 2.f;
    float g = powf(0.001f, 1.f / (hz * T60));
    float body = ip[PL_BODY] / 255.f;
    svf[0].set(280.f, 0.6f, sr);
    svf[1].set(1300.f, 0.4f, sr);
    const int MASK = PLUCK_BUF - 1;
    for (int i = 0; i < n; i++) {
        float rp = (float)pw - Ld;
        if (rp < 0) rp += PLUCK_BUF;
        int i0 = (int)rp;
        float fr = rp - i0;
        float y = pbuf[i0 & MASK] * (1.f - fr) + pbuf[(i0 + 1) & MASK] * fr;
        plp = (1.f - b) * y + b * plp;
        pbuf[pw] = plp * g;
        pw = (pw + 1) & MASK;
        float o = y;
        if (body > 0.f) o += body * (svf[0].bp(y) * 1.5f + svf[1].bp(y) * 0.8f);
        peak = std::max(fabsf(y), peak * 0.9997f);
        float s = o * gain * 0.6f;
        L[i] += s;
        R[i] += s;
    }
    if (peak < 1e-4f && age > (uint32_t)(sr * 0.05f)) active = false;
}

// ------------------------------------------------------------------ CHIP
void Voice::renderChip(const int* ip, float* L, float* R, int n, const VoiceCtx& c, float gain) {
    const float sr = c.sr;
    int w = std::max(0, std::min(7, ip[CH_WAVE]));
    env[0].set(envTime(ip[CH_AA]), envTime(ip[CH_AD]), ip[CH_AS] / 255.f, envTime(ip[CH_AR]), sr);
    sweepAcc = clampf(sweepAcc + ip[CH_SWEEP] * 0.75f * n / sr, -60.f, 60.f);
    float vib = sinCycle(lfoPh) * ip[CH_VIBD] / 255.f;
    lfoPh += (0.5f + ip[CH_VIBS] / 255.f * 12.f) * n / sr;
    lfoPh -= floorf(lfoPh);
    float hz = noteToHz(note + c.pitchMod + ip[I_FINE] * 0.01f + sweepAcc + vib);
    float inc = std::min(0.49f, hz / sr);
    static const float duties[4] = {0.125f, 0.25f, 0.5f, 0.75f};
    float duty = w < 4 ? clampf(duties[w] + c.timbre * 0.2f, 0.05f, 0.95f) : 0.5f;
    float noiseInc = hz * 8.f / sr;
    float amp0 = gain * 0.32f;
    for (int i = 0; i < n; i++) {
        float a = env[0].next();
        float q = floorf(a * 15.f + 0.5f) * (1.f / 15.f);
        float v;
        if (w < 4) v = ph[0] < duty ? 1.f : -1.f;
        else if (w == 4) { int st = (int)(ph[0] * 32.f); int t = st < 16 ? st : 31 - st; v = t / 7.5f - 1.f; }
        else if (w == 5) { v = (int)(ph[0] * 16.f) / 7.5f - 1.f; }
        else {
            noiseCnt += noiseInc;
            int guard = 0;
            while (noiseCnt >= 1.f && guard++ < 8) {
                noiseCnt -= 1.f;
                uint32_t bit = (lfsr ^ (lfsr >> 1)) & 1u;
                lfsr = (lfsr >> 1) | (bit << 14);
                if (w == 7) lfsr = (lfsr & ~(1u << 6)) | (bit << 6);
                noiseOut = (lfsr & 1u) ? 1.f : -1.f;
            }
            if (noiseCnt >= 1.f) noiseCnt = 0;
            v = noiseOut;
        }
        ph[0] = wrap1(ph[0] + inc);
        float s = v * q * amp0;
        L[i] += s;
        R[i] += s;
    }
    if (env[0].idle()) active = false;
}

} // namespace ht
