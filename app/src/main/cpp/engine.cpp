#include "engine.h"
#include "wavetable.h"
#include <chrono>
#include <cstdio>
#if defined(__x86_64__)
#include <xmmintrin.h>
#endif

namespace ht {

static inline void enableFTZ() {
#if defined(__aarch64__)
    uint64_t fpcr;
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
    asm volatile("msr fpcr, %0" ::"r"(fpcr | (1ULL << 24)));
#elif defined(__x86_64__)
    _mm_setcsr(_mm_getcsr() | 0x8040);
#endif
}

static inline bool isBad(float x) {
    uint32_t b;
    memcpy(&b, &x, 4);
    return (b & 0x7f800000u) == 0x7f800000u;
}

Engine::Engine() {
    initWavetables();
    for (int r = 0; r < SONG_ROWS; r++)
        for (int t = 0; t < NUM_TRACKS; t++) song[r][t] = -1;
    memset(iparams, 0, sizeof(iparams));
    memset(tparams, 0, sizeof(tparams));
    memset(gparams, 0, sizeof(gparams));
    for (int i = 0; i < NUM_INSTR; i++) { iparams[i][I_TYPE] = ENG_VA; iparams[i][I_VOL] = 0xC0; }
    for (int t = 0; t < NUM_TRACKS; t++) {
        tparams[t][T_VOL] = 0xC0; tparams[t][T_PAN] = 0x80; tparams[t][T_WIDTH] = 0x80;
        tparams[t][T_INS_MIX] = 0xFF; tparams[t][T_SC_DEPTH] = 0xC0;
        for (int v = 0; v < VOICES_PER_TRACK; v++) {
            tracks[t].voices[v].pbuf = pluckMem[t][v];
            tracks[t].voices[v].rng.seed(0x1234567u + t * 7919u + v * 104729u);
        }
    }
    gparams[G_TEMPO] = 120; gparams[G_M_VOL] = 0xC0; gparams[G_DLY_TIME] = 7;
    for (int i = 0; i < ST_SIZE; i++) state[i] = 0;
    for (int i = 0; i < MT_SIZE; i++) meters[i] = 0.f;
    memset(scope, 0, sizeof(scope));
    memset(pluckMem, 0, sizeof(pluckMem));
    setSampleRate(48000.f);
}

void Engine::setSampleRate(float s) {
    std::lock_guard<std::mutex> lk(renderMutex);
    sr = s;
    delay.alloc(sr);
    reverb.alloc(sr);
}

void Engine::push(const Cmd& c) {
    int h = qHead.load(std::memory_order_relaxed);
    int nx = (h + 1) % QSIZE;
    if (nx == qTail.load(std::memory_order_acquire)) return; // full: drop
    queue[h] = c;
    qHead.store(nx, std::memory_order_release);
}

void Engine::processCommands() {
    int t = qTail.load(std::memory_order_relaxed);
    while (t != qHead.load(std::memory_order_acquire)) {
        const Cmd& c = queue[t];
        switch (c.type) {
            case CMD_PLAY: startPlay(c.a, c.b, c.c, c.d); break;
            case CMD_STOP: stopPlay(); break;
            case CMD_AUDITION: {
                if (c.a < 0 || c.a >= NUM_TRACKS) break;
                PhraseStep st;
                st.note = (int16_t)c.c; st.inst = (int16_t)c.b; st.vol = (int16_t)c.d;
                TrackState& T = tracks[c.a];
                int savedTransp = T.transp;
                T.transp = 0;
                trigger(c.a, st, false, false, 0);
                T.transp = savedTransp;
                if (!playing) T.lengthTicks = 0;
                break;
            }
            case CMD_AUD_OFF: if (c.a >= 0 && c.a < NUM_TRACKS) releaseTrack(c.a); break;
            case CMD_PANIC: resetDsp(); break;
            default: break;
        }
        t = (t + 1) % QSIZE;
    }
    qTail.store(t, std::memory_order_release);
}

double Engine::tickLen() const {
    double stepSamples = sr * 60.0 / (tempo * 4.0);
    double sw = clampf(gparams[G_SWING] / 100.f * 0.5f, 0.f, 0.45f);
    stepSamples *= (stepCount % 2 == 0) ? (1.0 + sw) : (1.0 - sw);
    return stepSamples / TICKS_PER_STEP;
}

// ------------------------------------------------------------------ sequencer
void Engine::startPlay(int mode, int track, int idx, int row) {
    for (int t = 0; t < NUM_TRACKS; t++) {
        TrackState& T = tracks[t];
        releaseTrack(t);
        T.active = false; T.wrapped = false; T.hopTo = -1; T.displayNote = -1;
        T.arpOn = false; T.arpOffset = 0; T.pitchSlide = 0; T.slidePerTick = 0; T.vibDepth = 0;
        T.retrig = 0; T.killAt = -1; T.delayAt = -1; T.hasPending = false;
        T.cutOv = T.resOv = -1; T.timbre = 0; T.panOv = T.dsnOv = T.rsnOv = -1; T.velSlide = 0; T.lengthTicks = 0;
        T.chain = -1; T.phrase = -1; T.transp = 0;
    }
    playMode = mode; tick = 0; stepCount = 0; firstStep = true; samplesToTick = 0;
    lastGTempo = gparams[G_TEMPO];
    tempo = clampf((float)lastGTempo, 20.f, 400.f);
    if (mode == PLAY_SONG) {
        row = std::max(0, std::min(SONG_ROWS - 1, row));
        for (int t = 0; t < NUM_TRACKS; t++) {
            TrackState& T = tracks[t];
            T.songRow = row;
            T.chain = song[row][t];
            if (T.chain >= 0) { T.active = true; T.chainStep = 0; loadChainStep(t); }
            else T.wrapped = true;
        }
    } else if (track >= 0 && track < NUM_TRACKS) {
        TrackState& T = tracks[track];
        if (mode == PLAY_CHAIN && idx >= 0 && idx < NUM_CHAINS) {
            T.chain = idx; T.chainStep = 0; T.active = true; loadChainStep(track);
        } else if (mode == PLAY_PHRASE && idx >= 0 && idx < NUM_PHRASES) {
            T.phrase = idx; T.phraseStep = 0; T.transp = 0; T.active = true;
        }
    }
    playing = true;
}

void Engine::stopPlay() {
    playing = false;
    for (int t = 0; t < NUM_TRACKS; t++) {
        TrackState& T = tracks[t];
        releaseTrack(t);
        T.active = false; T.displayNote = -1;
        T.cutOv = T.resOv = -1; T.timbre = 0; T.panOv = T.dsnOv = T.rsnOv = -1;
        T.arpOffset = 0; T.pitchSlide = 0; T.vibDepth = 0; T.hasPending = false; T.lengthTicks = 0;
    }
}

void Engine::releaseTrack(int t) {
    TrackState& T = tracks[t];
    if (T.curVoice >= 0 && T.voices[T.curVoice].active) T.voices[T.curVoice].noteOff();
}

void Engine::loadChainStep(int t) {
    TrackState& T = tracks[t];
    if (T.chain < 0 || T.chain >= NUM_CHAINS) { T.phrase = -1; T.transp = 0; }
    else {
        const ChainStep& cs = chains[T.chain].s[T.chainStep];
        T.phrase = cs.phrase;
        T.transp = cs.transp;
    }
    T.phraseStep = 0;
}

void Engine::nextSongRow(int t) {
    TrackState& T = tracks[t];
    int r = T.songRow + 1;
    if (r >= SONG_ROWS || song[r][t] < 0) {
        r = T.songRow;
        while (r > 0 && song[r - 1][t] >= 0) r--;
        T.wrapped = true;
    }
    T.songRow = r;
    T.chain = song[r][t];
    if (T.chain < 0) { T.active = false; T.wrapped = true; }
}

void Engine::advance(int t) {
    TrackState& T = tracks[t];
    if (T.hopTo >= 0) {
        int h = T.hopTo;
        T.hopTo = -1;
        if (h < STEPS) { T.phraseStep = h; return; }
        if (playMode == PLAY_PHRASE) { T.phraseStep = 0; return; }
    } else {
        T.phraseStep++;
        if (T.phraseStep < STEPS) return;
        if (playMode == PLAY_PHRASE) { T.phraseStep = 0; return; }
    }
    T.chainStep++;
    if (T.chainStep >= STEPS || T.chain < 0 || chains[T.chain].s[T.chainStep].phrase < 0) {
        if (playMode == PLAY_CHAIN) T.chainStep = 0;
        else {
            nextSongRow(t);
            if (!T.active) { releaseTrack(t); return; }
            T.chainStep = 0;
        }
    }
    loadChainStep(t);
}

int Engine::allocVoice(int t) {
    TrackState& T = tracks[t];
    int best = -1;
    uint32_t bestAge = 0;
    for (int v = 0; v < VOICES_PER_TRACK; v++) {
        if (!T.voices[v].active) return v;
        if (v != T.curVoice && T.voices[v].age >= bestAge) { bestAge = T.voices[v].age; best = v; }
    }
    if (best < 0) best = (T.curVoice + 1) % VOICES_PER_TRACK;
    T.voices[best].kill();
    return best;
}

void Engine::trigger(int t, const PhraseStep& st, bool slide, bool acc, int slideV) {
    TrackState& T = tracks[t];
    if (st.note == NOTE_OFF) { releaseTrack(t); T.displayNote = -1; return; }
    int ins = st.inst >= 0 ? st.inst : T.instr;
    ins = std::max(0, std::min(NUM_INSTR - 1, ins));
    T.instr = ins;
    const int* ip = iparams[ins];
    int eng = ip[I_TYPE];
    float vel = st.vol >= 0 ? st.vol / 255.f : 1.f;
    if (acc && eng != ENG_ACID) vel = std::min(1.f, vel * 1.25f);
    int n = st.note + T.transp + ip[I_TRANSP];
    n = std::max(0, std::min(127, n));
    T.pitchSlide = 0; T.arpCnt = 0; T.vibPh = 0;
    float slideTime = slideV > 0 ? slideV / 255.f * 0.5f : 0.06f;
    Voice* cur = (T.curVoice >= 0 && T.voices[T.curVoice].active) ? &T.voices[T.curVoice] : nullptr;
    if (eng == ENG_ACID && cur && cur->engine == ENG_ACID && cur->instr == ins) {
        if (slide && cur->gate) cur->slideTo((float)n, slideTime, acc, sr);
        else cur->noteOn(ip, (float)n, vel, acc, (float)T.lastNote, sr);
    } else if (slide && cur && cur->instr == ins && eng != ENG_DRUM) {
        cur->slideTo((float)n, slideTime, acc, sr);
    } else {
        if (cur) {
            if (eng == ENG_DRUM && cur->engine == ENG_DRUM && cur->model == DM_OPHAT) cur->choke(sr);
            else cur->noteOff();
        }
        int v = allocVoice(t);
        Voice& V = T.voices[v];
        V.instr = ins;
        V.noteOn(ip, (float)n, vel, acc, (float)T.lastNote, sr);
        V.lastPm = 0;
        T.curVoice = v;
    }
    T.lastNote = n;
    T.displayNote = st.note;
    T.vel = vel;
    T.lengthTicks = ip[I_LENGTH];
    T.noteTicks = 0;
    if (T.lengthTicks > 0 && T.phrase >= 0 && T.phraseStep < STEPS - 1) {
        const PhraseStep& nx = phrases[T.phrase].s[T.phraseStep + 1];
        for (int k = 0; k < 3; k++) if (nx.fx[k] == FX_SLD) T.lengthTicks = 0;
    }
}

void Engine::startStep(int t) {
    TrackState& T = tracks[t];
    PhraseStep st;
    if (T.phrase >= 0 && T.phrase < NUM_PHRASES) st = phrases[T.phrase].s[T.phraseStep];
    T.arpOn = false; T.arpOffset = 0; T.slidePerTick = 0; T.vibDepth = 0; T.retrig = 0;
    T.killAt = -1; T.delayAt = -1; T.hasPending = false; T.velSlide = 0;
    if (st.inst >= 0 && st.inst != T.instr) { T.cutOv = T.resOv = -1; T.timbre = 0; }
    bool slide = false, acc = false, skip = false;
    int slideV = 0;
    for (int k = 0; k < 3; k++) {
        int v = st.fxv[k] & 0xFF;
        switch (st.fx[k]) {
            case FX_ARP: T.arpOn = v != 0; T.arpX = v >> 4; T.arpY = v & 15; T.arpCnt = 0; break;
            case FX_SLD: slide = true; slideV = v; break;
            case FX_ACC: acc = true; break;
            case FX_PUP: T.slidePerTick = v / 64.f; break;
            case FX_PDN: T.slidePerTick = -v / 64.f; break;
            case FX_VIB: T.vibRate = (float)(v >> 4); T.vibDepth = (v & 15) / 8.f; break;
            case FX_RTG: T.retrig = v; break;
            case FX_KIL: T.killAt = std::min(v, TICKS_PER_STEP - 1); break;
            case FX_DLY: T.delayAt = std::min(v, TICKS_PER_STEP - 1); break;
            case FX_CHA: if (rng.uni() * 255.f > (float)v) skip = true; break;
            case FX_CUT: T.cutOv = v; break;
            case FX_RES: T.resOv = v; break;
            case FX_TIM: T.timbre = (v - 128) / 128.f; break;
            case FX_PAN: T.panOv = v; break;
            case FX_DSN: T.dsnOv = v; break;
            case FX_RSN: T.rsnOv = v; break;
            case FX_TMP: if (v >= 20) tempo = (float)v; break;
            case FX_VSL: T.velSlide = ((v >> 4) - (v & 15)) * 0.01f; break;
            case FX_HOP: T.hopTo = v; break;
            default: break;
        }
    }
    if (skip) return;
    if (st.note >= 0) {
        if (T.delayAt > 0) {
            T.pending = st; T.hasPending = true;
            T.pendingSlide = slide; T.pendingAcc = acc; T.pendingSlideV = slideV;
        } else {
            trigger(t, st, slide, acc, slideV);
        }
    } else {
        if (st.inst >= 0) T.instr = st.inst;
        if (st.vol >= 0 && T.curVoice >= 0) T.voices[T.curVoice].vel = st.vol / 255.f;
    }
}

void Engine::tickFx(int t) {
    TrackState& T = tracks[t];
    if (T.hasPending && tick == T.delayAt) {
        T.hasPending = false;
        trigger(t, T.pending, T.pendingSlide, T.pendingAcc, T.pendingSlideV);
    }
    if (T.killAt >= 0 && tick == T.killAt) releaseTrack(t);
    if (T.retrig > 0 && tick > 0 && tick % T.retrig == 0 && T.lastNote >= 0 && T.curVoice >= 0) {
        const int* ip = iparams[T.instr];
        T.voices[T.curVoice].noteOff();
        int v = allocVoice(t);
        Voice& V = T.voices[v];
        V.instr = T.instr;
        V.noteOn(ip, (float)T.lastNote, T.vel, false, -1.f, sr);
        V.lastPm = 0;
        T.curVoice = v;
    }
    if (T.arpOn) {
        int k = T.arpCnt % 3;
        T.arpOffset = (float)(k == 0 ? 0 : (k == 1 ? T.arpX : T.arpY));
        T.arpCnt++;
    } else T.arpOffset = 0;
    if (tick > 0) T.pitchSlide = clampf(T.pitchSlide + T.slidePerTick, -96.f, 96.f);
    if (T.velSlide != 0.f && T.curVoice >= 0) {
        Voice& V = T.voices[T.curVoice];
        V.vel = clampf(V.vel + T.velSlide, 0.f, 1.f);
    }
    if (T.lengthTicks > 0 && T.curVoice >= 0 && T.voices[T.curVoice].gate) {
        if (++T.noteTicks >= T.lengthTicks) releaseTrack(t);
    }
}

void Engine::doTick() {
    if (tick == 0) {
        if (gparams[G_TEMPO] != lastGTempo) {
            lastGTempo = gparams[G_TEMPO];
            tempo = clampf((float)lastGTempo, 20.f, 400.f);
        }
        if (!firstStep)
            for (int t = 0; t < NUM_TRACKS; t++) if (tracks[t].active) advance(t);
        firstStep = false;
        if (offline && playMode == PLAY_SONG) {
            bool all = true;
            for (int t = 0; t < NUM_TRACKS; t++) if (!tracks[t].wrapped && tracks[t].active) all = false;
            if (all) { stopPlay(); return; }
        }
        for (int t = 0; t < NUM_TRACKS; t++) if (tracks[t].active) startStep(t);
    }
    for (int t = 0; t < NUM_TRACKS; t++) tickFx(t);
    if (++tick >= TICKS_PER_STEP) { tick = 0; stepCount++; }
}

// ------------------------------------------------------------------ audio
void Engine::resetDsp() {
    for (int t = 0; t < NUM_TRACKS; t++) {
        TrackState& T = tracks[t];
        for (auto& v : T.voices) v.kill();
        T.ins.reset();
        T.eq.lo.reset(); T.eq.mid.reset(); T.eq.hi.reset();
        T.comp.env = 0; T.duck.env = 0; T.duck.gain = 1;
        T.lim = Limiter();
        T.peakL = T.peakR = 0;
    }
    delay.clear();
    reverb.clear();
    masterComp.env = 0;
    masterLim = Limiter();
    masterEq.lo.reset(); masterEq.hi.reset(); masterEq.mid.reset();
}

void Engine::processBlock(float* out, int n) {
    bool anySolo = false;
    for (int t = 0; t < NUM_TRACKS; t++) if (tparams[t][T_SOLO]) anySolo = true;
    float pkDecay = expf(-(float)n / (0.25f * sr));

    // 1) voices -> raw track buffers (also the sidechain keys)
    for (int t = 0; t < NUM_TRACKS; t++) {
        TrackState& T = tracks[t];
        memset(T.bufL, 0, sizeof(float) * n);
        memset(T.bufR, 0, sizeof(float) * n);
        VoiceCtx ctx;
        ctx.sr = sr; ctx.timbre = T.timbre; ctx.cutOv = T.cutOv; ctx.resOv = T.resOv;
        float vib = T.vibDepth > 0.f ? sinCycle(T.vibPh) * T.vibDepth : 0.f;
        T.vibPh += T.vibRate * n / sr;
        T.vibPh -= floorf(T.vibPh);
        float pm = T.arpOffset + T.pitchSlide + vib;
        for (int v = 0; v < VOICES_PER_TRACK; v++) {
            Voice& V = T.voices[v];
            if (!V.active) continue;
            if (v == T.curVoice) { ctx.pitchMod = pm; V.lastPm = pm; }
            else ctx.pitchMod = V.lastPm;
            int ins = std::max(0, std::min(NUM_INSTR - 1, V.instr));
            V.render(iparams[ins], T.bufL, T.bufR, n, ctx);
        }
        for (int i = 0; i < n; i++) T.key[i] = 0.5f * (T.bufL[i] + T.bufR[i]);
    }

    memset(mixL, 0, sizeof(float) * n); memset(mixR, 0, sizeof(float) * n);
    memset(dlyInL, 0, sizeof(float) * n); memset(dlyInR, 0, sizeof(float) * n);
    memset(revInL, 0, sizeof(float) * n); memset(revInR, 0, sizeof(float) * n);

    // 2) track channel strips
    for (int t = 0; t < NUM_TRACKS; t++) {
        TrackState& T = tracks[t];
        const int* tp = tparams[t];
        float* L = T.bufL;
        float* R = T.bufR;
        T.ins.process(L, R, n, tp[T_INS_TYPE], tp[T_INS_P1], tp[T_INS_P2], tp[T_INS_P3], tp[T_INS_MIX], sr);
        T.eq.process(L, R, n, tp + T_EQ_LG, sr);
        int mask = tp[T_SC_MASK] & 0xFF & ~(1 << t);
        const float* key = nullptr;
        if (mask) {
            memset(sc, 0, sizeof(float) * n);
            for (int s = 0; s < NUM_TRACKS; s++)
                if (mask & (1 << s)) for (int i = 0; i < n; i++) sc[i] += tracks[s].key[i];
            key = sc;
        }
        int mode = tp[T_SC_MODE];
        T.comp.process(L, R, n, (mode == 1 && key) ? key : nullptr, (float)tp[T_CMP_THR], tp[T_CMP_RAT],
                       tp[T_CMP_ATK], tp[T_CMP_REL], (float)tp[T_CMP_MAK], sr);
        if (mode == 0 && key) T.duck.process(L, R, n, key, tp[T_SC_DEPTH], tp[T_SC_ATK], tp[T_SC_REL], sr);
        else T.duck.gain = 1.f;
        if (tp[T_WIDTH] != 0x80) {
            float w = tp[T_WIDTH] / 128.f;
            for (int i = 0; i < n; i++) {
                float m = (L[i] + R[i]) * 0.5f, s = (L[i] - R[i]) * 0.5f * w;
                L[i] = m + s; R[i] = m - s;
            }
        }
        float v = tp[T_VOL] / 192.f;
        float vol = v * v;
        float p = clampf(((T.panOv >= 0 ? T.panOv : tp[T_PAN]) - 128) / 127.f, -1.f, 1.f);
        float tgl = vol * std::min(1.f, 1.f - p), tgr = vol * std::min(1.f, 1.f + p);
        if (tp[T_MUTE] || (anySolo && !tp[T_SOLO])) tgl = tgr = 0.f;
        float dgl = (tgl - T.gl) / n, dgr = (tgr - T.gr) / n;
        for (int i = 0; i < n; i++) {
            T.gl += dgl; T.gr += dgr;
            L[i] *= T.gl; R[i] *= T.gr;
        }
        T.gl = tgl; T.gr = tgr;
        T.lim.process(L, R, n, (float)std::min(0, tp[T_LIM_CEIL]), tp[T_LIM_REL], sr);
        float ds = (T.dsnOv >= 0 ? T.dsnOv : tp[T_SEND_DLY]) / 255.f;
        float rs = (T.rsnOv >= 0 ? T.rsnOv : tp[T_SEND_REV]) / 255.f;
        ds *= ds; rs *= rs;
        float pl = 0, pr = 0;
        for (int i = 0; i < n; i++) {
            mixL[i] += L[i]; mixR[i] += R[i];
            dlyInL[i] += L[i] * ds; dlyInR[i] += R[i] * ds;
            revInL[i] += L[i] * rs; revInR[i] += R[i] * rs;
            pl = std::max(pl, fabsf(L[i])); pr = std::max(pr, fabsf(R[i]));
        }
        T.peakL = std::max(pl, T.peakL * pkDecay);
        T.peakR = std::max(pr, T.peakR * pkDecay);
    }

    // 3) send effects
    const int* g = gparams;
    delay.process(dlyInL, dlyInR, tmpL, tmpR, n, g, tempo, sr);
    float dret = g[G_DLY_RET] / 255.f, d2r = g[G_DLY_REV] / 255.f;
    dret *= dret * 1.5f; d2r *= d2r;
    for (int i = 0; i < n; i++) {
        mixL[i] += tmpL[i] * dret; mixR[i] += tmpR[i] * dret;
        revInL[i] += tmpL[i] * d2r; revInR[i] += tmpR[i] * d2r;
    }
    reverb.process(revInL, revInR, tmpL, tmpR, n, g, sr);
    float rret = g[G_REV_RET] / 255.f;
    rret *= rret * 2.0f;
    for (int i = 0; i < n; i++) { mixL[i] += tmpL[i] * rret; mixR[i] += tmpR[i] * rret; }

    // 4) master bus (DC blocker first: kick transients etc. are asymmetric)
    for (int i = 0; i < n; i++) {
        float l = mixL[i], r = mixR[i];
        dcY[0] = l - dcX[0] + 0.9995f * dcY[0]; dcX[0] = l; mixL[i] = dcY[0];
        dcY[1] = r - dcX[1] + 0.9995f * dcY[1]; dcX[1] = r; mixR[i] = dcY[1];
    }
    int meq[7] = {g[G_M_LO], 0x70, 0, 0x80, 0x80, g[G_M_HI], 0xC0};
    masterEq.process(mixL, mixR, n, meq, sr);
    masterComp.process(mixL, mixR, n, nullptr, (float)g[G_M_THR], g[G_M_RAT], g[G_M_ATK], g[G_M_REL], (float)g[G_M_MAK], sr);
    float mv = g[G_M_VOL] / 192.f;
    mv *= mv;
    float dm = (mv - masterGain) / n;
    for (int i = 0; i < n; i++) { masterGain += dm; mixL[i] *= masterGain; mixR[i] *= masterGain; }
    masterGain = mv;
    masterLim.process(mixL, mixR, n, (float)std::min(0, g[G_M_LIM]), 0x40, sr);

    float pl = 0, pr = 0, sum = 0;
    int sw = scopeW.load(std::memory_order_relaxed);
    for (int i = 0; i < n; i++) {
        out[i * 2] = mixL[i];
        out[i * 2 + 1] = mixR[i];
        pl = std::max(pl, fabsf(mixL[i]));
        pr = std::max(pr, fabsf(mixR[i]));
        sum += mixL[i] + mixR[i];
        if ((i & 1) == 0) { scope[sw] = 0.5f * (mixL[i] + mixR[i]); sw = (sw + 1) % (SCOPE_SIZE * 2); }
    }
    scopeW.store(sw, std::memory_order_relaxed);
    if (isBad(sum)) { // safety: blow-up guard
        memset(out, 0, sizeof(float) * n * 2);
        resetDsp();
    }
    masterPeakL = std::max(pl, masterPeakL * pkDecay);
    masterPeakR = std::max(pr, masterPeakR * pkDecay);
}

void Engine::exportState() {
    for (int t = 0; t < NUM_TRACKS; t++) {
        const TrackState& T = tracks[t];
        int b = t * ST_PER_TRACK;
        bool sounding = T.curVoice >= 0 && T.voices[T.curVoice].active && T.voices[T.curVoice].gate;
        state[b + 0].store(T.active ? 1 : 0, std::memory_order_relaxed);
        state[b + 1].store(T.songRow, std::memory_order_relaxed);
        state[b + 2].store(T.chain, std::memory_order_relaxed);
        state[b + 3].store(T.chainStep, std::memory_order_relaxed);
        state[b + 4].store(T.phrase, std::memory_order_relaxed);
        state[b + 5].store(T.phraseStep, std::memory_order_relaxed);
        state[b + 6].store(sounding ? T.displayNote : -1, std::memory_order_relaxed);
        state[b + 7].store(T.instr, std::memory_order_relaxed);
        meters[t * 3 + 0].store(T.peakL, std::memory_order_relaxed);
        meters[t * 3 + 1].store(T.peakR, std::memory_order_relaxed);
        meters[t * 3 + 2].store(std::min(T.comp.grLin, T.duck.gain), std::memory_order_relaxed);
    }
    state[ST_GLOBAL + 0].store(playing ? 1 : 0, std::memory_order_relaxed);
    state[ST_GLOBAL + 1].store(playMode, std::memory_order_relaxed);
    state[ST_GLOBAL + 2].store((int)(tempo * 10.f), std::memory_order_relaxed);
    state[ST_GLOBAL + 3].store((int)(cpuLoad.load() * 1000.f), std::memory_order_relaxed);
    state[ST_GLOBAL + 4].store((int)(stepCount & 0x7fffffff), std::memory_order_relaxed);
    meters[NUM_TRACKS * 3 + 0].store(masterPeakL, std::memory_order_relaxed);
    meters[NUM_TRACKS * 3 + 1].store(masterPeakR, std::memory_order_relaxed);
    meters[NUM_TRACKS * 3 + 2].store(cpuLoad.load(), std::memory_order_relaxed);
}

void Engine::render(float* out, int frames) {
    std::unique_lock<std::mutex> lk(renderMutex, std::try_to_lock);
    if (!lk.owns_lock() || offline) {
        memset(out, 0, sizeof(float) * frames * 2);
        return;
    }
    enableFTZ();
    auto t0 = std::chrono::steady_clock::now();
    processCommands();
    int done = 0;
    while (done < frames) {
        if (playing) {
            int guard = 0;
            while (samplesToTick <= 0.0 && playing && guard++ < 64) { doTick(); samplesToTick += tickLen(); }
        }
        int n = std::min(frames - done, MAX_BLOCK);
        if (playing) {
            int until = (int)ceil(samplesToTick);
            if (until < n) n = std::max(1, until);
        }
        processBlock(out + done * 2, n);
        if (playing) samplesToTick -= n;
        done += n;
    }
    auto t1 = std::chrono::steady_clock::now();
    float el = std::chrono::duration<float>(t1 - t0).count();
    float load = el / (frames / sr);
    cpuLoad.store(cpuLoad.load() * 0.95f + load * 0.05f);
    exportState();
}

static void writeLE32(FILE* f, uint32_t v) { uint8_t b[4] = {(uint8_t)v, (uint8_t)(v >> 8), (uint8_t)(v >> 16), (uint8_t)(v >> 24)}; fwrite(b, 1, 4, f); }
static void writeLE16(FILE* f, uint16_t v) { uint8_t b[2] = {(uint8_t)v, (uint8_t)(v >> 8)}; fwrite(b, 1, 2, f); }

int Engine::renderWav(const char* path, int maxSeconds) {
    std::lock_guard<std::mutex> lk(renderMutex);
    enableFTZ();
    FILE* f = fopen(path, "wb");
    if (!f) return -1;
    offline = true;
    stopPlay();
    resetDsp();
    startPlay(PLAY_SONG, 0, 0, 0);
    uint32_t srate = (uint32_t)sr;
    fwrite("RIFF", 1, 4, f); writeLE32(f, 0); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); writeLE32(f, 16); writeLE16(f, 1); writeLE16(f, 2); writeLE32(f, srate);
    writeLE32(f, srate * 4); writeLE16(f, 4); writeLE16(f, 16);
    fwrite("data", 1, 4, f); writeLE32(f, 0);
    const int CH = 512;
    float buf[CH * 2];
    int16_t pcm[CH * 2];
    long total = 0, maxFrames = (long)maxSeconds * srate, tail = -1;
    bool anyActive = false;
    for (int t = 0; t < NUM_TRACKS; t++) anyActive |= tracks[t].active;
    if (!anyActive) tail = (long)(0.1f * sr);
    while (total < maxFrames) {
        int done = 0;
        while (done < CH) {
            if (playing) {
                int guard = 0;
                while (samplesToTick <= 0.0 && playing && guard++ < 64) { doTick(); samplesToTick += tickLen(); }
            }
            int n = std::min(CH - done, MAX_BLOCK);
            if (playing) { int until = (int)ceil(samplesToTick); if (until < n) n = std::max(1, until); }
            processBlock(buf + done * 2, n);
            if (playing) samplesToTick -= n;
            done += n;
        }
        for (int i = 0; i < CH * 2; i++) {
            float x = clampf(buf[i], -1.f, 1.f);
            pcm[i] = (int16_t)lrintf(x * 32767.f);
        }
        fwrite(pcm, sizeof(int16_t), CH * 2, f);
        total += CH;
        if (!playing && tail < 0) tail = (long)(2.5f * sr);
        if (tail >= 0) { tail -= CH; if (tail <= 0) break; }
    }
    uint32_t dataBytes = (uint32_t)(total * 4);
    fseek(f, 4, SEEK_SET); writeLE32(f, 36 + dataBytes);
    fseek(f, 40, SEEK_SET); writeLE32(f, dataBytes);
    fclose(f);
    stopPlay();
    resetDsp();
    offline = false;
    return (int)(total / srate);
}

} // namespace ht
