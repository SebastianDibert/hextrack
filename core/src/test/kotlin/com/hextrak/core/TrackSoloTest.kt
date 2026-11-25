package com.hextrak.core

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/** Regression: every track must sound when soloed, including tracks that enter later in the song. */
class TrackSoloTest {
    private val out = File(System.getProperty("hextrak.out") ?: "build/host")
    private fun rms(f: File): Double {
        val raf = RandomAccessFile(f, "r"); val b = ByteArray((raf.length() - 44).toInt()); raf.seek(44); raf.readFully(b); raf.close()
        var s = 0.0; val n = b.size / 2
        for (i in 0 until n) { val v = ((b[i * 2 + 1].toInt() shl 8) or (b[i * 2].toInt() and 0xFF)).toShort() / 32768.0; s += v * v }
        return sqrt(s / n)
    }

    @Test
    fun everyDemoTrackSoundsSolo() {
        if (!HostTest.loadNative(File(System.getProperty("hextrak.native") ?: "build/host/libhextrak_host.so"))) return
        val app = Tracker(NativeAudio, MemPlatform())
        val sb = StringBuilder("demo solo rms: ")
        for (t in 0 until K.TRACKS) {
            app.loadDemo()
            app.song.tracks[t][K.T_SOLO] = 1
            app.pushAll()
            val f = File(out, "solo_t${t + 1}.wav")
            val secs = NativeAudio.renderWav(f.absolutePath, 60)
            val r = rms(f)
            sb.append("T${t + 1}=%.4f(%ds) ".format(r, secs))
            assertTrue(r > 0.005, "track ${t + 1} silent when soloed (rms=$r)")
        }
        println(sb)
    }
}
