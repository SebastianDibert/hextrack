package com.hextrak.core

interface AudioBackend {
    fun setSongCell(row: Int, track: Int, chain: Int)
    fun setChainStep(c: Int, s: Int, phrase: Int, transp: Int)
    fun setPhraseStep(p: Int, s: Int, note: Int, inst: Int, vol: Int, f1: Int, v1: Int, f2: Int, v2: Int, f3: Int, v3: Int)
    fun setInstrParam(i: Int, p: Int, v: Int)
    fun setTrackParam(t: Int, p: Int, v: Int)
    fun setGlobalParam(p: Int, v: Int)
    fun play(mode: Int, track: Int, idx: Int, row: Int)
    fun stop()
    fun audition(track: Int, inst: Int, note: Int, vel: Int)
    fun auditionOff(track: Int)
    fun panic()
    fun getState(out: IntArray)
    fun getMeters(out: FloatArray)
    fun getScope(out: FloatArray)
    fun bufferFrames(): Int = 0
    fun sampleRate(): Int = 48000
}

/** JNI binding to the native engine (libhextrak.so on Android, libhextrak_host.so for host tests). */
object NativeAudio : AudioBackend {
    private var sr = 48000

    fun init(realtime: Boolean, sampleRate: Int = 0): Int { sr = nInit(realtime, sampleRate); return sr }
    fun setRunning(run: Boolean) = nSetRunning(run)
    fun renderWav(path: String, maxSeconds: Int): Int = nRenderWav(path, maxSeconds)
    /** Advance the engine without an output stream (tests). */
    fun process(frames: Int) = nProcess(frames)

    override fun setSongCell(row: Int, track: Int, chain: Int) = nSetSongCell(row, track, chain)
    override fun setChainStep(c: Int, s: Int, phrase: Int, transp: Int) = nSetChainStep(c, s, phrase, transp)
    override fun setPhraseStep(p: Int, s: Int, note: Int, inst: Int, vol: Int, f1: Int, v1: Int, f2: Int, v2: Int, f3: Int, v3: Int) =
        nSetPhraseStep(p, s, note, inst, vol, f1, v1, f2, v2, f3, v3)
    override fun setInstrParam(i: Int, p: Int, v: Int) = nSetInstrParam(i, p, v)
    override fun setTrackParam(t: Int, p: Int, v: Int) = nSetTrackParam(t, p, v)
    override fun setGlobalParam(p: Int, v: Int) = nSetGlobalParam(p, v)
    override fun play(mode: Int, track: Int, idx: Int, row: Int) = nPlay(mode, track, idx, row)
    override fun stop() = nStop()
    override fun audition(track: Int, inst: Int, note: Int, vel: Int) = nAudition(track, inst, note, vel)
    override fun auditionOff(track: Int) = nAuditionOff(track)
    override fun panic() = nPanic()
    override fun getState(out: IntArray) = nGetState(out)
    override fun getMeters(out: FloatArray) = nGetMeters(out)
    override fun getScope(out: FloatArray) = nGetScope(out)
    override fun bufferFrames(): Int = nBufferFrames()
    override fun sampleRate(): Int = sr

    @JvmStatic private external fun nInit(realtime: Boolean, sampleRate: Int): Int
    @JvmStatic private external fun nSetRunning(run: Boolean)
    @JvmStatic private external fun nBufferFrames(): Int
    @JvmStatic private external fun nSetSongCell(row: Int, track: Int, chain: Int)
    @JvmStatic private external fun nSetChainStep(c: Int, s: Int, phrase: Int, transp: Int)
    @JvmStatic private external fun nSetPhraseStep(p: Int, s: Int, note: Int, inst: Int, vol: Int, f1: Int, v1: Int, f2: Int, v2: Int, f3: Int, v3: Int)
    @JvmStatic private external fun nSetInstrParam(i: Int, p: Int, v: Int)
    @JvmStatic private external fun nSetTrackParam(t: Int, p: Int, v: Int)
    @JvmStatic private external fun nSetGlobalParam(p: Int, v: Int)
    @JvmStatic private external fun nPlay(mode: Int, track: Int, idx: Int, row: Int)
    @JvmStatic private external fun nStop()
    @JvmStatic private external fun nAudition(track: Int, inst: Int, note: Int, vel: Int)
    @JvmStatic private external fun nAuditionOff(track: Int)
    @JvmStatic private external fun nPanic()
    @JvmStatic private external fun nGetState(out: IntArray)
    @JvmStatic private external fun nGetMeters(out: FloatArray)
    @JvmStatic private external fun nGetScope(out: FloatArray)
    @JvmStatic private external fun nRenderWav(path: String, maxSeconds: Int): Int
    @JvmStatic private external fun nProcess(frames: Int)
}

/** Silent backend for UI tests. */
class NullAudio : AudioBackend {
    override fun setSongCell(row: Int, track: Int, chain: Int) {}
    override fun setChainStep(c: Int, s: Int, phrase: Int, transp: Int) {}
    override fun setPhraseStep(p: Int, s: Int, note: Int, inst: Int, vol: Int, f1: Int, v1: Int, f2: Int, v2: Int, f3: Int, v3: Int) {}
    override fun setInstrParam(i: Int, p: Int, v: Int) {}
    override fun setTrackParam(t: Int, p: Int, v: Int) {}
    override fun setGlobalParam(p: Int, v: Int) {}
    override fun play(mode: Int, track: Int, idx: Int, row: Int) {}
    override fun stop() {}
    override fun audition(track: Int, inst: Int, note: Int, vel: Int) {}
    override fun auditionOff(track: Int) {}
    override fun panic() {}
    override fun getState(out: IntArray) {}
    override fun getMeters(out: FloatArray) {}
    override fun getScope(out: FloatArray) {}
}

/** Platform services (file storage, rendering, exit). */
interface Platform {
    fun listProjects(): List<String>
    fun saveProject(name: String, data: String): Boolean
    fun loadProject(name: String): String?
    fun deleteProject(name: String): Boolean
    /** Render the song to a WAV in the background; call onDone with a status message on the UI thread. */
    fun renderWav(name: String, onDone: (String) -> Unit)
    fun exit()
}
