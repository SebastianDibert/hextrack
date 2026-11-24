#include "dsp.h"
#include "wavetable.h"
#include <vector>
#include <atomic>
#include <mutex>

namespace ht {

const SineTab gSin;

void fft(float* re, float* im, int n, bool inverse) {
    // bit reversal
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) { std::swap(re[i], re[j]); std::swap(im[i], im[j]); }
    }
    for (int len = 2; len <= n; len <<= 1) {
        double ang = 2.0 * M_PI / len * (inverse ? 1 : -1);
        float wr = (float)cos(ang), wi = (float)sin(ang);
        for (int i = 0; i < n; i += len) {
            float cr = 1.f, ci = 0.f;
            for (int j = 0; j < len / 2; j++) {
                float ur = re[i + j], ui = im[i + j];
                float vr = re[i + j + len / 2] * cr - im[i + j + len / 2] * ci;
                float vi = re[i + j + len / 2] * ci + im[i + j + len / 2] * cr;
                re[i + j] = ur + vr; im[i + j] = ui + vi;
                re[i + j + len / 2] = ur - vr; im[i + j + len / 2] = ui - vi;
                float nr = cr * wr - ci * wi;
                ci = cr * wi + ci * wr;
                cr = nr;
            }
        }
    }
    if (inverse) {
        float s = 1.f / n;
        for (int i = 0; i < n; i++) { re[i] *= s; im[i] *= s; }
    }
}

// ---------------------------------------------------------------- wavetables
static float gWT[WT_TABLES][WT_FRAMES][WT_LEVELS][WT_SIZE + 1];
static std::once_flag gWTOnce;

const float* wtFrame(int table, int frame, int level) { return gWT[table][frame][level]; }

static float hash01(uint32_t x) {
    x ^= x >> 16; x *= 0x7feb352dU; x ^= x >> 15; x *= 0x846ca68bU; x ^= x >> 16;
    return (x & 0xFFFFFF) / 16777216.f;
}

static void additive(float* out, const float* amps, int nh) {
    for (int i = 0; i < WT_SIZE; i++) {
        float p = (float)i / WT_SIZE, s = 0;
        for (int h = 1; h <= nh; h++) if (amps[h] != 0.f) s += amps[h] * sinCycle(p * h);
        out[i] = s;
    }
}

static void makeShape(int t, float m, float* x) {
    float amps[129];
    std::fill(amps, amps + 129, 0.f);
    switch (t) {
        case 0: { // BASIC: sine -> tri -> saw -> square
            float seg = m * 3.f;
            int s0 = std::min(2, (int)seg);
            float fr = seg - s0;
            for (int i = 0; i < WT_SIZE; i++) {
                float p = (float)i / WT_SIZE;
                float sh[4] = {sinCycle(p), triNaive(fmodf(p + 0.25f, 1.f)), 2.f * fmodf(p + 0.5f, 1.f) - 1.f, p < 0.5f ? 1.f : -1.f};
                x[i] = sh[s0] + (sh[s0 + 1] - sh[s0]) * fr;
            }
            return;
        }
        case 1: { // PWM
            float pw = 0.5f - m * 0.46f;
            for (int i = 0; i < WT_SIZE; i++) x[i] = ((float)i / WT_SIZE) < pw ? 1.f : -1.f;
            return;
        }
        case 2: { // VOCAL formants A E I O U
            static const float F[5][3] = {{730, 1090, 2440}, {530, 1840, 2480}, {270, 2290, 3010}, {570, 840, 2410}, {300, 870, 2240}};
            float v = m * 4.f;
            int v0 = std::min(3, (int)v);
            float fr = v - v0;
            float f[3];
            for (int k = 0; k < 3; k++) f[k] = F[v0][k] + (F[v0 + 1][k] - F[v0][k]) * fr;
            const float bw[3] = {110, 140, 180}, w[3] = {1.f, 0.65f, 0.35f};
            for (int h = 1; h <= 40; h++) {
                float hf = h * 110.f, a = 0.08f / h;
                for (int k = 0; k < 3; k++) { float d = (hf - f[k]) / bw[k]; a += w[k] * expf(-d * d); }
                amps[h] = a;
            }
            additive(x, amps, 40);
            return;
        }
        case 3: { // DIGI: pseudo-random harmonic spectra, brighter with position
            int fi = (int)(m * 7.f + 0.5f);
            for (int h = 1; h <= 64; h++) {
                float r = hash01(h * 131 + fi * 977);
                amps[h] = (r > 0.45f ? r : 0.f) / powf((float)h, 1.6f - m * 1.1f);
            }
            amps[1] = std::max(amps[1], 0.6f);
            additive(x, amps, 64);
            return;
        }
        case 4: { // ORGAN drawbars (harmonics 1,2,3,4,5,6,8)
            static const int reg[8][7] = {{8, 0, 0, 0, 0, 0, 0}, {8, 8, 0, 0, 0, 0, 0}, {8, 8, 8, 0, 0, 0, 0}, {8, 0, 8, 0, 0, 0, 8},
                                          {8, 8, 8, 8, 0, 0, 0}, {8, 6, 8, 4, 0, 3, 6}, {8, 8, 8, 8, 8, 8, 8}, {4, 8, 8, 6, 4, 6, 8}};
            static const int harm[7] = {1, 2, 3, 4, 5, 6, 8};
            int fi = std::min(7, (int)(m * 7.f + 0.5f));
            for (int k = 0; k < 7; k++) amps[harm[k]] = reg[fi][k] / 8.f;
            additive(x, amps, 8);
            return;
        }
        case 5: { // SYNC saw
            float ratio = 1.f + m * 7.f;
            for (int i = 0; i < WT_SIZE; i++) {
                float p = (float)i / WT_SIZE;
                float s = p * ratio;
                x[i] = (2.f * (s - floorf(s)) - 1.f) * (1.f - p * 0.3f);
            }
            return;
        }
        case 6: { // BELL: sparse partials, emphasis moves upward
            static const int part[10] = {1, 2, 3, 5, 7, 9, 12, 15, 19, 24};
            for (int i = 0; i < 10; i++) amps[part[i]] = expf(-fabsf(i - m * 9.f) * 0.7f) * (i == 0 ? 1.2f : 1.f);
            additive(x, amps, 24);
            return;
        }
        default: { // FOLD: wavefolded sine
            float g = 1.f + m * 6.f;
            for (int i = 0; i < WT_SIZE; i++) {
                float v = sinCycle((float)i / WT_SIZE) * g;
                for (int k = 0; k < 16 && fabsf(v) > 1.f; k++) v = (v > 0 ? 2.f : -2.f) - v;
                x[i] = v;
            }
            return;
        }
    }
}

void initWavetables() {
    std::call_once(gWTOnce, [] {
        std::vector<float> x(WT_SIZE), re(WT_SIZE), im(WT_SIZE), r2(WT_SIZE), i2(WT_SIZE);
        for (int t = 0; t < WT_TABLES; t++) {
            for (int f = 0; f < WT_FRAMES; f++) {
                makeShape(t, (float)f / (WT_FRAMES - 1), x.data());
                for (int i = 0; i < WT_SIZE; i++) { re[i] = x[i]; im[i] = 0; }
                fft(re.data(), im.data(), WT_SIZE, false);
                re[0] = im[0] = 0; // remove DC
                float scale = 1.f;
                for (int L = 0; L < WT_LEVELS; L++) {
                    int maxH = 512 >> L;
                    for (int k = 0; k < WT_SIZE; k++) {
                        int h = k <= WT_SIZE / 2 ? k : WT_SIZE - k;
                        bool keep = h <= maxH;
                        r2[k] = keep ? re[k] : 0.f;
                        i2[k] = keep ? im[k] : 0.f;
                    }
                    fft(r2.data(), i2.data(), WT_SIZE, true);
                    if (L == 0) {
                        float pk = 1e-6f;
                        for (int i = 0; i < WT_SIZE; i++) pk = std::max(pk, fabsf(r2[i]));
                        scale = 1.f / pk;
                    }
                    float* dst = gWT[t][f][L];
                    for (int i = 0; i < WT_SIZE; i++) dst[i] = r2[i] * scale;
                    dst[WT_SIZE] = dst[0];
                }
            }
        }
    });
}

} // namespace ht
