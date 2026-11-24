// JNI bridge for com.hextrak.core.NativeAudio + AAudio output (Android only).
#include <jni.h>
#include "engine.h"
#include <thread>
#include <atomic>
#include <mutex>

#ifdef __ANDROID__
#include <aaudio/AAudio.h>
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "hextrak", __VA_ARGS__)
#else
#define LOGI(...) ((void)0)
#endif

using namespace ht;

static Engine* gEngine = nullptr;
static std::mutex gStreamMutex;

namespace ht {
Engine* engineInstance() { return gEngine; }
}

#ifdef __ANDROID__
static AAudioStream* gStream = nullptr;
static std::atomic<bool> gWantRunning{false};
static int32_t gBurst = 0;

static aaudio_data_callback_result_t dataCb(AAudioStream*, void*, void* audioData, int32_t numFrames) {
    if (gEngine) gEngine->render((float*)audioData, numFrames);
    else memset(audioData, 0, sizeof(float) * 2 * numFrames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static bool openStream();
static void closeStream();

static void errorCb(AAudioStream*, void*, aaudio_result_t err) {
    LOGI("stream error %d, restarting", (int)err);
    // Must not reopen from the callback thread.
    std::thread([] {
        std::lock_guard<std::mutex> lk(gStreamMutex);
        closeStream();
        if (gWantRunning) openStream();
    }).detach();
}

static bool tryOpen(aaudio_sharing_mode_t sharing) {
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return false;
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(b, sharing);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_GAME);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(b, dataCb, nullptr);
    AAudioStreamBuilder_setErrorCallback(b, errorCb, nullptr);
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &gStream);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK) { gStream = nullptr; return false; }
    return true;
}

static bool openStream() {
    if (gStream) return true;
    if (!tryOpen(AAUDIO_SHARING_MODE_EXCLUSIVE) && !tryOpen(AAUDIO_SHARING_MODE_SHARED)) return false;
    int32_t sr = AAudioStream_getSampleRate(gStream);
    gBurst = AAudioStream_getFramesPerBurst(gStream);
    if (gEngine && sr > 0 && (float)sr != gEngine->sampleRate()) gEngine->setSampleRate((float)sr);
    AAudioStream_setBufferSizeInFrames(gStream, gBurst * 2);
    AAudioStream_requestStart(gStream);
    LOGI("stream open sr=%d burst=%d", sr, gBurst);
    return true;
}

static void closeStream() {
    if (!gStream) return;
    AAudioStream_requestStop(gStream);
    AAudioStream_close(gStream);
    gStream = nullptr;
}
#endif

extern "C" {

#define JFN(name) Java_com_hextrak_core_NativeAudio_##name

JNIEXPORT jint JNICALL JFN(nInit)(JNIEnv*, jobject, jboolean realtime, jint sampleRate) {
    if (!gEngine) {
        gEngine = new Engine();
    }
    if (sampleRate > 0) gEngine->setSampleRate((float)sampleRate);
#ifdef __ANDROID__
    if (realtime) {
        std::lock_guard<std::mutex> lk(gStreamMutex);
        gWantRunning = true;
        openStream();
    }
#else
    (void)realtime;
#endif
    return (jint)gEngine->sampleRate();
}

JNIEXPORT void JNICALL JFN(nSetRunning)(JNIEnv*, jobject, jboolean run) {
#ifdef __ANDROID__
    std::lock_guard<std::mutex> lk(gStreamMutex);
    gWantRunning = run;
    if (run) openStream(); else closeStream();
#else
    (void)run;
#endif
}

JNIEXPORT jint JNICALL JFN(nBufferFrames)(JNIEnv*, jobject) {
#ifdef __ANDROID__
    std::lock_guard<std::mutex> lk(gStreamMutex);
    return gStream ? AAudioStream_getBufferSizeInFrames(gStream) : 0;
#else
    return 0;
#endif
}

JNIEXPORT void JNICALL JFN(nSetSongCell)(JNIEnv*, jobject, jint row, jint track, jint chain) {
    if (!gEngine || row < 0 || row >= SONG_ROWS || track < 0 || track >= NUM_TRACKS) return;
    gEngine->song[row][track] = (int16_t)chain;
}

JNIEXPORT void JNICALL JFN(nSetChainStep)(JNIEnv*, jobject, jint c, jint s, jint phrase, jint transp) {
    if (!gEngine || c < 0 || c >= NUM_CHAINS || s < 0 || s >= STEPS) return;
    gEngine->chains[c].s[s].phrase = (int16_t)phrase;
    gEngine->chains[c].s[s].transp = (int16_t)transp;
}

JNIEXPORT void JNICALL JFN(nSetPhraseStep)(JNIEnv*, jobject, jint p, jint s, jint note, jint inst, jint vol,
                                           jint f1, jint v1, jint f2, jint v2, jint f3, jint v3) {
    if (!gEngine || p < 0 || p >= NUM_PHRASES || s < 0 || s >= STEPS) return;
    PhraseStep& st = gEngine->phrases[p].s[s];
    st.note = (int16_t)note; st.inst = (int16_t)inst; st.vol = (int16_t)vol;
    st.fx[0] = (int16_t)f1; st.fxv[0] = (int16_t)v1;
    st.fx[1] = (int16_t)f2; st.fxv[1] = (int16_t)v2;
    st.fx[2] = (int16_t)f3; st.fxv[2] = (int16_t)v3;
}

JNIEXPORT void JNICALL JFN(nSetInstrParam)(JNIEnv*, jobject, jint i, jint p, jint v) {
    if (!gEngine || i < 0 || i >= NUM_INSTR || p < 0 || p >= NUM_IPARAMS) return;
    gEngine->iparams[i][p] = v;
}

JNIEXPORT void JNICALL JFN(nSetTrackParam)(JNIEnv*, jobject, jint t, jint p, jint v) {
    if (!gEngine || t < 0 || t >= NUM_TRACKS || p < 0 || p >= NUM_TPARAMS) return;
    gEngine->tparams[t][p] = v;
}

JNIEXPORT void JNICALL JFN(nSetGlobalParam)(JNIEnv*, jobject, jint p, jint v) {
    if (!gEngine || p < 0 || p >= NUM_GPARAMS) return;
    gEngine->gparams[p] = v;
}

JNIEXPORT void JNICALL JFN(nPlay)(JNIEnv*, jobject, jint mode, jint track, jint idx, jint row) {
    if (gEngine) gEngine->push({CMD_PLAY, mode, track, idx, row});
}

JNIEXPORT void JNICALL JFN(nStop)(JNIEnv*, jobject) {
    if (gEngine) gEngine->push({CMD_STOP, 0, 0, 0, 0});
}

JNIEXPORT void JNICALL JFN(nAudition)(JNIEnv*, jobject, jint track, jint inst, jint note, jint vel) {
    if (gEngine) gEngine->push({CMD_AUDITION, track, inst, note, vel});
}

JNIEXPORT void JNICALL JFN(nAuditionOff)(JNIEnv*, jobject, jint track) {
    if (gEngine) gEngine->push({CMD_AUD_OFF, track, 0, 0, 0});
}

JNIEXPORT void JNICALL JFN(nPanic)(JNIEnv*, jobject) {
    if (gEngine) gEngine->push({CMD_PANIC, 0, 0, 0, 0});
}

JNIEXPORT void JNICALL JFN(nGetState)(JNIEnv* env, jobject, jintArray out) {
    if (!gEngine) return;
    jint tmp[ST_SIZE];
    for (int i = 0; i < ST_SIZE; i++) tmp[i] = gEngine->state[i].load(std::memory_order_relaxed);
    jsize len = std::min((jsize)ST_SIZE, env->GetArrayLength(out));
    env->SetIntArrayRegion(out, 0, len, tmp);
}

JNIEXPORT void JNICALL JFN(nGetMeters)(JNIEnv* env, jobject, jfloatArray out) {
    if (!gEngine) return;
    jfloat tmp[MT_SIZE];
    for (int i = 0; i < MT_SIZE; i++) tmp[i] = gEngine->meters[i].load(std::memory_order_relaxed);
    jsize len = std::min((jsize)MT_SIZE, env->GetArrayLength(out));
    env->SetFloatArrayRegion(out, 0, len, tmp);
}

JNIEXPORT void JNICALL JFN(nGetScope)(JNIEnv* env, jobject, jfloatArray out) {
    if (!gEngine) return;
    jsize len = std::min((jsize)SCOPE_SIZE, env->GetArrayLength(out));
    jfloat tmp[SCOPE_SIZE];
    int w = gEngine->scopeW.load(std::memory_order_relaxed);
    const int N = SCOPE_SIZE * 2;
    // find a rising zero crossing in the older half for a stable display
    int start = (w + N - 2 * SCOPE_SIZE) % N;
    for (int k = 0; k < SCOPE_SIZE - 1; k++) {
        int i = (start + k) % N, j = (i + 1) % N;
        if (gEngine->scope[i] <= 0.f && gEngine->scope[j] > 0.f) { start = j; break; }
    }
    for (int k = 0; k < len; k++) tmp[k] = gEngine->scope[(start + k) % N];
    env->SetFloatArrayRegion(out, 0, len, tmp);
}

// Pull audio without an output stream (host tests / previews).
JNIEXPORT void JNICALL JFN(nProcess)(JNIEnv*, jobject, jint frames) {
    if (!gEngine) return;
    static float buf[1024 * 2];
    while (frames > 0) {
        int n = frames > 1024 ? 1024 : frames;
        gEngine->render(buf, n);
        frames -= n;
    }
}

JNIEXPORT jint JNICALL JFN(nRenderWav)(JNIEnv* env, jobject, jstring path, jint maxSeconds) {
    if (!gEngine) return -1;
    const char* p = env->GetStringUTFChars(path, nullptr);
    int r = gEngine->renderWav(p, maxSeconds);
    env->ReleaseStringUTFChars(path, p);
    return r;
}

} // extern "C"
