#pragma once
#include "params.h"
#include "voice.h"
#include "fx.h"
#include <atomic>
#include <mutex>

namespace ht {

constexpr int MAX_BLOCK = 64;

struct PhraseStep {
    int16_t note = -1, inst = -1, vol = -1;
    int16_t fx[3] = {0, 0, 0};
    int16_t fxv[3] = {0, 0, 0};
};
struct Phrase { PhraseStep s[STEPS]; };
struct ChainStep { int16_t phrase = -1; int16_t transp = 0; };
struct Chain { ChainStep s[STEPS]; };

struct Cmd { int type, a, b, c, d; };
enum { CMD_PLAY = 1, CMD_STOP, CMD_AUDITION, CMD_AUD_OFF, CMD_PANIC };

struct TrackState {
    // position
    bool active = false, wrapped = false;
    int songRow = 0, chain = -1, chainStep = 0, phrase = -1, phraseStep = 0, transp = 0, hopTo = -1;
    // note state
    int instr = 0, lastNote = -1, curVoice = -1, displayNote = -1;
    int noteTicks = 0, lengthTicks = 0;
    float vel = 1.f;
    // fx state
    bool arpOn = false; int arpX = 0, arpY = 0, arpCnt = 0; float arpOffset = 0;
    float pitchSlide = 0, slidePerTick = 0;
    float vibDepth = 0, vibRate = 0, vibPh = 0;
    int retrig = 0, killAt = -1, delayAt = -1;
    bool hasPending = false; PhraseStep pending; bool pendingSlide = false, pendingAcc = false; int pendingSlideV = 0;
    int cutOv = -1, resOv = -1; float timbre = 0;
    int panOv = -1, dsnOv = -1, rsnOv = -1;
    float velSlide = 0;
    // voices
    Voice voices[VOICES_PER_TRACK];
    // dsp chain
    InsertFx ins;
    Eq3 eq;
    Compressor comp;
    Ducker duck;
    Limiter lim;
    float gl = 0, gr = 0; // smoothed output gains
    float bufL[MAX_BLOCK], bufR[MAX_BLOCK], key[MAX_BLOCK];
    float peakL = 0, peakR = 0;
};

class Engine {
public:
    Engine();
    void setSampleRate(float sr);
    float sampleRate() const { return sr; }
    // realtime render; out is interleaved stereo float
    void render(float* out, int frames);
    // offline render of the whole song to a 16-bit WAV file; returns seconds rendered or -1
    int renderWav(const char* path, int maxSeconds);

    void push(const Cmd& c);

    // song data (written by UI thread, read by audio thread)
    int16_t song[SONG_ROWS][NUM_TRACKS];
    Chain chains[NUM_CHAINS];
    Phrase phrases[NUM_PHRASES];
    int iparams[NUM_INSTR][NUM_IPARAMS];
    int tparams[NUM_TRACKS][NUM_TPARAMS];
    int gparams[NUM_GPARAMS];

    // exported state
    std::atomic<int> state[ST_SIZE];
    std::atomic<float> meters[MT_SIZE];
    float scope[SCOPE_SIZE * 2];
    std::atomic<int> scopeW{0};
    std::atomic<float> cpuLoad{0};

private:
    void processCommands();
    void processBlock(float* out, int n);
    void doTick();
    double tickLen() const;
    void startPlay(int mode, int track, int idx, int row);
    void stopPlay();
    void advance(int t);
    void loadChainStep(int t);
    void nextSongRow(int t);
    void startStep(int t);
    void tickFx(int t);
    void trigger(int t, const PhraseStep& st, bool slide, bool acc, int slideV);
    void releaseTrack(int t);
    int allocVoice(int t);
    void resetDsp();
    void exportState();

    float sr = 48000;
    TrackState tracks[NUM_TRACKS];
    float pluckMem[NUM_TRACKS][VOICES_PER_TRACK][PLUCK_BUF];

    // sequencer
    bool playing = false, firstStep = true;
    int playMode = PLAY_SONG, tick = 0;
    long stepCount = 0;
    double samplesToTick = 0;
    float tempo = 120.f;
    int lastGTempo = -1;

    // fx busses
    StereoDelay delay;
    Reverb reverb;
    Eq3 masterEq;
    Compressor masterComp;
    Limiter masterLim;
    float dlyInL[MAX_BLOCK], dlyInR[MAX_BLOCK], revInL[MAX_BLOCK], revInR[MAX_BLOCK];
    float mixL[MAX_BLOCK], mixR[MAX_BLOCK], tmpL[MAX_BLOCK], tmpR[MAX_BLOCK], sc[MAX_BLOCK];
    float masterPeakL = 0, masterPeakR = 0;
    float masterGain = 0;
    float dcX[2] = {0, 0}, dcY[2] = {0, 0};

    // command queue (SPSC)
    static constexpr int QSIZE = 256;
    Cmd queue[QSIZE];
    std::atomic<int> qHead{0}, qTail{0};

    Rng rng;
    std::mutex renderMutex;
    bool offline = false;
};

Engine* engineInstance();

} // namespace ht
