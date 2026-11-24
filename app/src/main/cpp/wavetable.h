#pragma once
namespace ht {

constexpr int WT_TABLES = 8;
constexpr int WT_FRAMES = 8;
constexpr int WT_LEVELS = 10;
constexpr int WT_SIZE = 2048;

void initWavetables();
const float* wtFrame(int table, int frame, int level);

// Pick mip level so that no harmonic exceeds nyquist. Level L keeps 512>>L harmonics.
inline int wtLevel(float hz, float sr) {
    float maxH = sr * 0.5f / (hz < 1.f ? 1.f : hz);
    int L = 0;
    while (L < WT_LEVELS - 1 && (float)(512 >> L) > maxH) L++;
    return L;
}

// Read wavetable with frame morphing (pos 0..1) at phase ph (0..1).
inline float wtRead(int table, float pos, int level, float ph) {
    float fp = pos * (WT_FRAMES - 1);
    int f0 = (int)fp;
    if (f0 >= WT_FRAMES - 1) f0 = WT_FRAMES - 2;
    if (f0 < 0) f0 = 0;
    float ff = fp - f0;
    const float* a = wtFrame(table, f0, level);
    const float* b = wtFrame(table, f0 + 1, level);
    float x = ph * WT_SIZE;
    int i = (int)x;
    float fr = x - i;
    i &= (WT_SIZE - 1);
    float va = a[i] + (a[i + 1] - a[i]) * fr;
    float vb = b[i] + (b[i + 1] - b[i]) * fr;
    return va + (vb - va) * ff;
}

} // namespace ht
