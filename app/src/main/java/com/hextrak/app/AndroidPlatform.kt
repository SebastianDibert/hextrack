package com.hextrak.app

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.hextrak.core.NativeAudio
import com.hextrak.core.Platform
import java.io.File

class AndroidPlatform(private val act: Activity) : Platform {
    private val dir: File by lazy {
        val base = act.getExternalFilesDir(null) ?: act.filesDir
        File(base, "projects").apply { mkdirs() }
    }

    private fun clean(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _#!.-]"), "_").ifEmpty { "SONG" }

    override fun listProjects(): List<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".htk") }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

    override fun saveProject(name: String, data: String): Boolean = try {
        val f = File(dir, clean(name) + ".htk")
        val tmp = File(dir, clean(name) + ".tmp")
        tmp.writeText(data)
        tmp.renameTo(f) || run { f.writeText(data); tmp.delete(); true }
    } catch (e: Exception) { false }

    override fun loadProject(name: String): String? = try {
        File(dir, clean(name) + ".htk").takeIf { it.exists() }?.readText()
    } catch (e: Exception) { null }

    override fun deleteProject(name: String): Boolean = File(dir, clean(name) + ".htk").delete()

    override fun renderWav(name: String, onDone: (String) -> Unit) {
        Thread {
            val msg = try {
                val tmp = File(act.cacheDir, "render.wav")
                val secs = NativeAudio.renderWav(tmp.absolutePath, 20 * 60)
                if (secs < 0) "RENDER FAILED" else "SAVED ${export(tmp, clean(name))} (${secs}s)"
            } catch (e: Exception) {
                "RENDER FAILED: ${e.message}"
            }
            act.runOnUiThread { onDone(msg.take(40)) }
        }.apply { setName("hextrak-render") }.start()
    }

    /** Copies the rendered file into the shared Music folder; falls back to app storage. */
    private fun export(tmp: File, name: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val resolver = act.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.wav")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Hextrak")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    tmp.delete()
                    return "Music/Hextrak/$name.wav"
                }
            } catch (_: Exception) { }
        }
        val outDir = File(act.getExternalFilesDir(null) ?: act.filesDir, "renders").apply { mkdirs() }
        val f = File(outDir, "$name.wav")
        tmp.copyTo(f, overwrite = true)
        tmp.delete()
        return "renders/$name.wav"
    }

    override fun exit() { act.finishAndRemoveTask() }
}
