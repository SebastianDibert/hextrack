// Small DSP toolkit: oscillators, filters, envelopes, utilities.
#pragma once
#include <cmath>
#include <cstdint>
#include <cstring>
#include <algorithm>

namespace ht {

constexpr float PI = 3.14159265358979f;
constexpr float TWOPI = 6.28318530717959f;

inline float clampf(float x, float a, float b) { return x < a ? a : (x > b ? b : x); }
inline float dbToGain(float db) { return powf(10.f, db * 0.05f); }
inline float gainToDb(float g) { return 20.f * log10f(g < 1e-9f ? 1e-9f : g); }
inline float fastTanh(float x) {
    if (x < -3.f) return -1.f;
    if (x > 3.f) return 1.f;
    float x2 = x * x;
    return x * (27.f + x2) / (27.f + 9.f * x2);
}
// Note numbering: 0 = C-0, 57 = A-4 (440 Hz)
inline float noteToHz(float n) { return 440.f * exp2f((n - 57.f) * (1.f / 12.f)); }
// 0..255 -> 0.5 ms .. 10 s, exponential
inline float envTime(int v) { return 0.0005f * powf(20000.f, v / 255.f); }
// 0..255 -> 20 Hz .. 20.5 kHz, exponential
inline float cutoffHz(int v) { return 20.f * exp2f(v / 255.f * 10.f); }
inline float expCoef(float seconds, float sr) { return expf(-1.f / std::max(1e-5f, seconds * sr)); }
inline float lvlCurve(int v) { float x = v / 255.f; return x * x; }

struct Rng {
    uint32_t s = 0x9E3779B9u;
    void seed(uint32_t v) { s = v ? v : 0x1234567u; }
    inline float next() { // -1..1
        s ^= s << 13; s ^= s >> 17; s ^= s << 5;
        return (int32_t)s * (1.0f / 2147483648.f);
    }
    inline float uni() { return next() * 0.5f + 0.5f; }
};

struct SineTab {
    float t[4097];
    SineTab() { for (int i = 0; i <= 4096; i++) t[i] = sinf(TWOPI * i / 4096.f); }
};
extern const SineTab gSin;
// sine of phase expressed in cycles (any range)
inline float sinCycle(float ph) {
    ph -= floorf(ph);
    float f = ph * 4096.f;
    int i = (int)f;
    float fr = f - i;
    const float* t = gSin.t;
    return t[i] + (t[i + 1] - t[i]) * fr;
}

inline float polyblep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.f; }
    if (t > 1.f - dt) { t = (t - 1.f) / dt; return t * t + t + t + 1.f; }
    return 0.f;
}
inline float sawBL(float ph, float dt) { return 2.f * ph - 1.f - polyblep(ph, dt); }
inline float pulseBL(float ph, float dt, float pw) {
    float v = ph < pw ? 1.f : -1.f;
    v += polyblep(ph, dt);
    float p2 = ph - pw; if (p2 < 0.f) p2 += 1.f;
    v -= polyblep(p2, dt);
    return v;
}
inline float triNaive(float ph) { return 4.f * fabsf(ph - 0.5f) - 1.f; }

// Topology-preserving state variable filter (Cytomic / Simper)
struct SVF {
    float ic1 = 0, ic2 = 0, a1 = 1, a2 = 0, a3 = 0, k = 2;
    float low = 0, band = 0, high = 0;
    void set(float fc, float res, float sr) {
        fc = clampf(fc, 10.f, sr * 0.49f);
        float g = tanf(PI * fc / sr);
        k = 2.f - 1.96f * clampf(res, 0.f, 1.f);
        a1 = 1.f / (1.f + g * (g + k));
        a2 = g * a1;
        a3 = g * a2;
    }
    inline void tick(float x) {
        float v3 = x - ic2;
        float v1 = a1 * ic1 + a2 * v3;
        float v2 = ic2 + a2 * ic1 + a3 * v3;
        ic1 = 2.f * v1 - ic1;
        ic2 = 2.f * v2 - ic2;
        low = v2; band = v1; high = x - k * v1 - v2;
    }
    inline float lp(float x) { tick(x); return low; }
    inline float bp(float x) { tick(x); return band; }
    inline float hp(float x) { tick(x); return high; }
    void reset() { ic1 = ic2 = 0; }
};

struct OnePole {
    float z = 0, a = 1;
    void setLP(float fc, float sr) { a = 1.f - expf(-TWOPI * clampf(fc, 1.f, sr * 0.49f) / sr); }
    inline float lp(float x) { z += a * (x - z); return z; }
    inline float hp(float x) { z += a * (x - z); return x - z; }
};

// RBJ biquad, stereo state
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1[2] = {0, 0}, z2[2] = {0, 0};
    void norm(float B0, float B1, float B2, float A0, float A1, float A2) {
        b0 = B0 / A0; b1 = B1 / A0; b2 = B2 / A0; a1 = A1 / A0; a2 = A2 / A0;
    }
    void peak(float f, float q, float db, float sr) {
        float A = powf(10.f, db / 40.f), w = TWOPI * f / sr, al = sinf(w) / (2.f * q), c = cosf(w);
        norm(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A);
    }
    void lowShelf(float f, float db, float sr) {
        float A = powf(10.f, db / 40.f), w = TWOPI * f / sr, c = cosf(w), s = sinf(w);
        float al = s / 2.f * sqrtf(2.f), sa = 2.f * sqrtf(A) * al;
        norm(A * ((A + 1) - (A - 1) * c + sa), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sa),
             (A + 1) + (A - 1) * c + sa, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sa);
    }
    void highShelf(float f, float db, float sr) {
        float A = powf(10.f, db / 40.f), w = TWOPI * f / sr, c = cosf(w), s = sinf(w);
        float al = s / 2.f * sqrtf(2.f), sa = 2.f * sqrtf(A) * al;
        norm(A * ((A + 1) + (A - 1) * c + sa), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sa),
             (A + 1) - (A - 1) * c + sa, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sa);
    }
    inline float proc(float x, int ch) {
        float y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0; }
};

struct ADSR {
    enum { IDLE = 0, ATT, DEC, SUS, REL };
    int st = IDLE;
    float v = 0, ai = 1, dc = 0, s = 1, rc = 0;
    void set(float a, float d, float sus, float r, float sr) {
        ai = 1.f / std::max(1.f, a * sr);
        dc = expf(-4.6f / std::max(1.f, d * sr));
        s = sus;
        rc = expf(-4.6f / std::max(1.f, r * sr));
    }
    void on() { st = ATT; }
    void off() { if (st != IDLE) st = REL; }
    void kill() { st = IDLE; v = 0; }
    bool idle() const { return st == IDLE; }
    inline float next() {
        switch (st) {
            case ATT: v += ai; if (v >= 1.f) { v = 1.f; st = DEC; } break;
            case DEC: v = s + (v - s) * dc; if (v - s < 1e-4f) { v = s; st = s < 1e-4f ? IDLE : SUS; } break;
            case SUS: v = s; break;
            case REL: v *= rc; if (v < 1e-5f) { v = 0; st = IDLE; } break;
            default: break;
        }
        return v;
    }
};

// Simple FFT (radix-2, in-place, complex interleaved re/im arrays)
void fft(float* re, float* im, int n, bool inverse);

} // namespace ht
