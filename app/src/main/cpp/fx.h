// Mixer effects: track inserts, EQ, dynamics, send effects.
#pragma once
#include "dsp.h"
#include "params.h"
#include <vector>

namespace ht {

inline float rateHz(int v) { return 0.05f * exp2f(v / 255.f * 8.6f); } // 0.05 .. ~20 Hz

// ------------------------------------------------------------ Insert effect (one per track)
struct InsertFx {
    static constexpr int DL = 4096;
    int type = -1;
    float dl[2][DL];
    int w = 0;
    float lfo = 0;
    SVF f[2];
    float apz[2][6];
    float fbs[2] = {0, 0};
    float hold[2] = {0, 0};
    int holdCnt = 0;
    float ringPh = 0;
    OnePole tone[2];

    InsertFx() { reset(); }
    void reset() {
        memset(dl, 0, sizeof(dl));
        memset(apz, 0, sizeof(apz));
        fbs[0] = fbs[1] = hold[0] = hold[1] = 0;
        holdCnt = 0; w = 0; lfo = 0; ringPh = 0;
        f[0].reset(); f[1].reset();
        tone[0].z = tone[1].z = 0;
    }
    inline float readDl(int ch, float d) const {
        float rp = (float)w - d;
        while (rp < 0) rp += DL;
        int i0 = (int)rp;
        float fr = rp - i0;
        return dl[ch][i0 & (DL - 1)] * (1.f - fr) + dl[ch][(i0 + 1) & (DL - 1)] * fr;
    }

    void process(float* L, float* R, int n, int t, int p1, int p2, int p3, int mixv, float sr) {
        if (t != type) { reset(); type = t; }
        if (t <= INS_OFF || t >= NUM_INS || mixv == 0) return;
        float mix = mixv / 255.f, dry = 1.f - mix;
        float a = p1 / 255.f, b = p2 / 255.f, c = p3 / 255.f;
        switch (t) {
            case INS_DRIVE: {
                float g = 1.f + a * a * 40.f;
                tone[0].setLP(500.f * exp2f(b * 5.3f), sr);
                tone[1].a = tone[0].a;
                int mode = p3 < 85 ? 0 : (p3 < 170 ? 1 : 2);
                float norm = mode == 0 ? 1.f / fastTanh(std::min(g, 3.f)) : 1.f;
                for (int i = 0; i < n; i++) {
                    float x[2] = {L[i], R[i]};
                    for (int ch = 0; ch < 2; ch++) {
                        float v = x[ch] * g;
                        if (mode == 0) v = fastTanh(v) * norm;
                        else if (mode == 1) v = clampf(v, -1.f, 1.f);
                        else { for (int k = 0; k < 8 && fabsf(v) > 1.f; k++) v = (v > 0 ? 2.f : -2.f) - v; }
                        v = tone[ch].lp(v) * (0.7f + 0.3f / g);
                        x[ch] = x[ch] * dry + v * mix;
                    }
                    L[i] = x[0]; R[i] = x[1];
                }
                break;
            }
            case INS_CRUSH: {
                float bits = 16.f - a * 15.f;
                float q = exp2f(bits - 1.f);
                int ds = 1 + (int)(b * 63.f);
                for (int i = 0; i < n; i++) {
                    if (holdCnt <= 0) {
                        hold[0] = roundf(L[i] * q) / q;
                        hold[1] = roundf(R[i] * q) / q;
                        holdCnt = ds;
                    }
                    holdCnt--;
                    L[i] = L[i] * dry + hold[0] * mix;
                    R[i] = R[i] * dry + hold[1] * mix;
                }
                break;
            }
            case INS_CHORUS:
            case INS_FLANGER: {
                bool fl = t == INS_FLANGER;
                float base = (fl ? 0.001f : 0.012f) * sr;
                float depth = (fl ? 0.004f : 0.008f) * sr * b;
                float fb = fl ? c * 0.92f : c * 0.5f;
                float inc = rateHz(p1) / sr;
                for (int i = 0; i < n; i++) {
                    lfo += inc; if (lfo >= 1.f) lfo -= 1.f;
                    float l1 = sinCycle(lfo), l2 = sinCycle(lfo + 0.25f);
                    float dL = base + depth * (0.5f + 0.5f * l1);
                    float dR = base + depth * (0.5f + 0.5f * l2);
                    float yl = readDl(0, dL), yr = readDl(1, dR);
                    dl[0][w] = L[i] + yl * fb;
                    dl[1][w] = R[i] + yr * fb;
                    w = (w + 1) & (DL - 1);
                    L[i] = L[i] * (fl ? dry : 1.f - mix * 0.5f) + yl * mix;
                    R[i] = R[i] * (fl ? dry : 1.f - mix * 0.5f) + yr * mix;
                }
                break;
            }
            case INS_PHASER: {
                float inc = rateHz(p1) / sr;
                float fb = c * 0.85f;
                for (int i = 0; i < n; i++) {
                    lfo += inc; if (lfo >= 1.f) lfo -= 1.f;
                    float m[2] = {0.5f + 0.5f * sinCycle(lfo), 0.5f + 0.5f * sinCycle(lfo + 0.2f)};
                    float x[2] = {L[i], R[i]};
                    for (int ch = 0; ch < 2; ch++) {
                        float fc = 200.f * exp2f(m[ch] * b * 4.f + 0.3f);
                        float tg = tanf(PI * std::min(fc, sr * 0.45f) / sr);
                        float ac = (tg - 1.f) / (tg + 1.f);
                        float y = x[ch] + fbs[ch] * fb;
                        for (int s = 0; s < 6; s++) {
                            float o = ac * y + apz[ch][s];
                            apz[ch][s] = y - ac * o;
                            y = o;
                        }
                        fbs[ch] = y;
                        x[ch] = x[ch] * (1.f - mix * 0.5f) + y * mix * 0.7f;
                    }
                    L[i] = x[0]; R[i] = x[1];
                }
                break;
            }
            case INS_FILTER: {
                f[0].set(cutoffHz(p1), b, sr);
                f[1].set(cutoffHz(p1), b, sr);
                int mode = p3 < 85 ? 0 : (p3 < 170 ? 1 : 2);
                for (int i = 0; i < n; i++) {
                    f[0].tick(L[i]); f[1].tick(R[i]);
                    float yl = mode == 0 ? f[0].low : (mode == 1 ? f[0].band : f[0].high);
                    float yr = mode == 0 ? f[1].low : (mode == 1 ? f[1].band : f[1].high);
                    L[i] = L[i] * dry + yl * mix;
                    R[i] = R[i] * dry + yr * mix;
                }
                break;
            }
            case INS_TREMOLO: {
                float inc = rateHz(p1) / sr;
                bool pan = p3 >= 128;
                for (int i = 0; i < n; i++) {
                    lfo += inc; if (lfo >= 1.f) lfo -= 1.f;
                    float s = sinCycle(lfo);
                    float gl, gr;
                    if (pan) { gl = 1.f - b * (0.5f + 0.5f * s); gr = 1.f - b * (0.5f - 0.5f * s); }
                    else { gl = gr = 1.f - b * (0.5f + 0.5f * s); }
                    L[i] = L[i] * dry + L[i] * gl * mix;
                    R[i] = R[i] * dry + R[i] * gr * mix;
                }
                break;
            }
            case INS_RING: {
                float inc = 20.f * exp2f(a * 7.f) / sr;
                for (int i = 0; i < n; i++) {
                    ringPh += inc; if (ringPh >= 1.f) ringPh -= 1.f;
                    float m = sinCycle(ringPh);
                    L[i] = L[i] * dry + L[i] * m * mix;
                    R[i] = R[i] * dry + R[i] * m * mix;
                }
                break;
            }
            default: break;
        }
    }
};

// ------------------------------------------------------------ 3-band EQ
struct Eq3 {
    Biquad lo, mid, hi;
    int cache[7] = {-999, -999, -999, -999, -999, -999, -999};
    float sr = 0;
    bool active = false;
    void process(float* L, float* R, int n, const int* p /* lg lf mg mf mq hg hf */, float srate) {
        bool changed = srate != sr;
        for (int i = 0; i < 7; i++) if (p[i] != cache[i]) { changed = true; cache[i] = p[i]; }
        if (changed) {
            sr = srate;
            lo.lowShelf(20.f * powf(50.f, p[1] / 255.f), (float)p[0], sr);
            mid.peak(100.f * powf(100.f, p[3] / 255.f), 0.3f * powf(26.f, p[4] / 255.f), (float)p[2], sr);
            hi.highShelf(1000.f * powf(16.f, p[6] / 255.f), (float)p[5], sr);
            bool was = active;
            active = p[0] != 0 || p[2] != 0 || p[5] != 0;
            if (active && !was) { lo.reset(); mid.reset(); hi.reset(); }
        }
        if (!active) return;
        for (int i = 0; i < n; i++) {
            float l = L[i], r = R[i];
            if (p[0]) { l = lo.proc(l, 0); r = lo.proc(r, 1); }
            if (p[2]) { l = mid.proc(l, 0); r = mid.proc(r, 1); }
            if (p[5]) { l = hi.proc(l, 0); r = hi.proc(r, 1); }
            L[i] = l; R[i] = r;
        }
    }
};

static const float kRatioVals[10] = {1.f, 1.5f, 2.f, 3.f, 4.f, 6.f, 8.f, 12.f, 20.f, 100.f};

// ------------------------------------------------------------ Compressor (feed forward, soft knee, optional external key)
struct Compressor {
    float env = 0;   // gain reduction in dB
    float grLin = 1; // for meters
    void process(float* L, float* R, int n, const float* key, float thrDb, int ratioIdx, int atk, int rel, float makeupDb, float sr) {
        ratioIdx = std::max(0, std::min(9, ratioIdx));
        float ratio = kRatioVals[ratioIdx];
        if (ratioIdx == 0 && makeupDb == 0.f) { env = 0; grLin = 1; return; }
        float aC = expCoef(0.0001f + atk / 255.f * atk / 255.f * 0.1f, sr);
        float rC = expCoef(0.01f + rel / 255.f * 1.0f, sr);
        float slope = 1.f - 1.f / ratio;
        float mk = makeupDb;
        for (int i = 0; i < n; i++) {
            float x = key ? fabsf(key[i]) : std::max(fabsf(L[i]), fabsf(R[i]));
            float over = gainToDb(x) - thrDb;
            float gr;
            if (over <= -3.f) gr = 0.f;
            else if (over < 3.f) gr = slope * (over + 3.f) * (over + 3.f) / 12.f;
            else gr = slope * over;
            env = gr > env ? gr + (env - gr) * aC : gr + (env - gr) * rC;
            float g = dbToGain(mk - env);
            L[i] *= g; R[i] *= g;
        }
        grLin = dbToGain(-env);
    }
};

// ------------------------------------------------------------ Sidechain ducker
// Onset-triggered "pump": each hit on the key retriggers a duck envelope (attack -> release),
// so long kick tails do not keep the destination pinned down.
struct Ducker {
    float fast = 0, slow = 0, e = 0, target = 0, gain = 1;
    bool armed = true, attacking = false;
    void process(float* L, float* R, int n, const float* key, int depth, int atk, int rel, float sr) {
        float d = depth / 255.f;
        float cf = expCoef(0.003f, sr);
        float cs = 1.f - expCoef(0.04f, sr);
        float aC = 1.f - expCoef(0.0005f + atk / 255.f * 0.05f, sr);
        float rC = expCoef(0.01f + rel / 255.f * rel / 255.f * 1.2f, sr);
        for (int i = 0; i < n; i++) {
            float k = fabsf(key[i]);
            fast = std::max(k, fast * cf);
            slow += (fast - slow) * cs;
            if (armed && fast > 0.02f && fast > slow * 1.5f + 0.01f) {
                armed = false;
                attacking = true;
                target = std::max(e, std::min(1.f, fast * 2.5f));
            } else if (!armed && fast < slow * 1.1f) {
                armed = true;
            }
            if (attacking) {
                e += (target - e) * aC;
                if (e >= target * 0.98f) attacking = false;
            } else {
                e *= rC;
            }
            gain = 1.f - d * e;
            L[i] *= gain; R[i] *= gain;
        }
    }
};

// ------------------------------------------------------------ Look-ahead peak limiter
struct Limiter {
    static constexpr int LA = 64;
    float buf[2][LA] = {{0}};
    int w = 0;
    float g = 1;
    void process(float* L, float* R, int n, float ceilDb, int rel, float sr) {
        float ceil = dbToGain(ceilDb);
        float aC = 1.f - expf(-3.f / LA);
        float rC = 1.f - expCoef(0.01f + rel / 255.f * 0.5f, sr);
        for (int i = 0; i < n; i++) {
            float pk = std::max(fabsf(L[i]), fabsf(R[i]));
            float target = pk > ceil ? ceil / pk : 1.f;
            g += (target - g) * (target < g ? aC : rC);
            float dl = buf[0][w], dr = buf[1][w];
            buf[0][w] = L[i]; buf[1][w] = R[i];
            w = (w + 1) % LA;
            L[i] = clampf(dl * g, -ceil, ceil);
            R[i] = clampf(dr * g, -ceil, ceil);
        }
    }
};

// ------------------------------------------------------------ Stereo tempo-synced delay
static const float kDelayBeats[14] = {0.0625f, 0.125f, 1.f / 6.f, 0.25f, 0.375f, 1.f / 3.f, 0.5f, 0.75f, 2.f / 3.f, 1.f, 1.5f, 2.f, 3.f, 4.f};

struct StereoDelay {
    std::vector<float> b[2];
    int size = 0, w = 0;
    float curD = -1;
    OnePole lp[2], hp[2];
    void alloc(float sr) {
        size = (int)(sr * 4.f) + 4;
        b[0].assign(size, 0.f);
        b[1].assign(size, 0.f);
        w = 0; curD = -1;
    }
    void clear() { std::fill(b[0].begin(), b[0].end(), 0.f); std::fill(b[1].begin(), b[1].end(), 0.f); }
    void process(const float* inL, const float* inR, float* outL, float* outR, int n, const int* g, float tempo, float sr) {
        if (size == 0) return;
        int ti = std::max(0, std::min(13, g[G_DLY_TIME]));
        float target = std::min((float)(size - 4), kDelayBeats[ti] * 60.f / tempo * sr);
        if (curD < 0) curD = target;
        float fb = g[G_DLY_FB] / 255.f * 0.95f;
        float pp = g[G_DLY_PP] / 255.f;
        lp[0].setLP(1000.f * powf(18.f, g[G_DLY_HC] / 255.f), sr); lp[1].a = lp[0].a;
        hp[0].setLP(20.f * powf(50.f, g[G_DLY_LC] / 255.f), sr); hp[1].a = hp[0].a;
        float slew = 1.f - expCoef(0.08f, sr);
        for (int i = 0; i < n; i++) {
            curD += (target - curD) * slew;
            float rp = (float)w - curD;
            if (rp < 0) rp += size;
            int i0 = (int)rp;
            float fr = rp - i0;
            int i1 = i0 + 1 >= size ? 0 : i0 + 1;
            float yl = b[0][i0] * (1.f - fr) + b[0][i1] * fr;
            float yr = b[1][i0] * (1.f - fr) + b[1][i1] * fr;
            float fl = hp[0].hp(lp[0].lp(yl)) * fb;
            float fr2 = hp[1].hp(lp[1].lp(yr)) * fb;
            float mono = (inL[i] + inR[i]) * 0.5f;
            // ping-pong blend: pp=0 straight stereo, pp=1 full cross feed with mono input on left
            float wl = (inL[i] * (1.f - pp) + mono * pp) + fl * (1.f - pp) + fr2 * pp;
            float wr = inR[i] * (1.f - pp) + fr2 * (1.f - pp) + fl * pp;
            b[0][w] = wl;
            b[1][w] = wr;
            if (++w >= size) w = 0;
            outL[i] = yl;
            outR[i] = yr;
        }
    }
};

// ------------------------------------------------------------ Freeverb style reverb
struct Comb {
    std::vector<float> buf; int idx = 0; float store = 0;
    inline float proc(float x, float fb, float d1, float d2) {
        float o = buf[idx];
        store = o * d2 + store * d1;
        buf[idx] = x + store * fb;
        if (++idx >= (int)buf.size()) idx = 0;
        return o;
    }
};
struct Allpass {
    std::vector<float> buf; int idx = 0;
    inline float proc(float x) {
        float b = buf[idx];
        float o = -x + b;
        buf[idx] = x + b * 0.5f;
        if (++idx >= (int)buf.size()) idx = 0;
        return o;
    }
};

struct Reverb {
    Comb cL[8], cR[8];
    Allpass aL[4], aR[4];
    std::vector<float> pre;
    int preW = 0;
    OnePole hp;
    void alloc(float sr) {
        static const int ct[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static const int at[4] = {556, 441, 341, 225};
        float s = sr / 44100.f;
        for (int i = 0; i < 8; i++) {
            cL[i].buf.assign((size_t)(ct[i] * s), 0.f); cL[i].idx = 0; cL[i].store = 0;
            cR[i].buf.assign((size_t)((ct[i] + 23) * s), 0.f); cR[i].idx = 0; cR[i].store = 0;
        }
        for (int i = 0; i < 4; i++) {
            aL[i].buf.assign((size_t)(at[i] * s), 0.f); aL[i].idx = 0;
            aR[i].buf.assign((size_t)((at[i] + 23) * s), 0.f); aR[i].idx = 0;
        }
        pre.assign((size_t)(sr * 0.26f) + 2, 0.f);
        preW = 0;
    }
    void clear() {
        for (auto& c : cL) { std::fill(c.buf.begin(), c.buf.end(), 0.f); c.store = 0; }
        for (auto& c : cR) { std::fill(c.buf.begin(), c.buf.end(), 0.f); c.store = 0; }
        for (auto& a : aL) std::fill(a.buf.begin(), a.buf.end(), 0.f);
        for (auto& a : aR) std::fill(a.buf.begin(), a.buf.end(), 0.f);
        std::fill(pre.begin(), pre.end(), 0.f);
    }
    void process(const float* inL, const float* inR, float* outL, float* outR, int n, const int* g, float sr) {
        if (pre.empty()) return;
        float fb = 0.7f + g[G_REV_SIZE] / 255.f * 0.28f;
        float damp = g[G_REV_DAMP] / 255.f * 0.6f;
        float d1 = damp, d2 = 1.f - damp;
        float wid = g[G_REV_WID] / 255.f;
        float w1 = 0.5f + wid * 0.5f, w2 = (1.f - wid) * 0.5f;
        int pd = (int)(g[G_REV_PRE] / 255.f * 0.25f * sr);
        int psz = (int)pre.size();
        if (pd >= psz) pd = psz - 1;
        hp.setLP(20.f * powf(40.f, g[G_REV_LC] / 255.f), sr);
        for (int i = 0; i < n; i++) {
            float x = (inL[i] + inR[i]) * 0.5f;
            pre[preW] = x;
            int rp = preW - pd; if (rp < 0) rp += psz;
            x = hp.hp(pre[rp]) * 0.03f;
            if (++preW >= psz) preW = 0;
            float l = 0, r = 0;
            for (int k = 0; k < 8; k++) { l += cL[k].proc(x, fb, d1, d2); r += cR[k].proc(x, fb, d1, d2); }
            for (int k = 0; k < 4; k++) { l = aL[k].proc(l); r = aR[k].proc(r); }
            outL[i] = l * w1 + r * w2;
            outR[i] = r * w1 + l * w2;
        }
    }
};

} // namespace ht
