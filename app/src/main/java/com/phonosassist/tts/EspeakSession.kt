package com.phonosassist.tts

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Shared, serialized access to the bundled espeak-ng native library
 * (`phonosassist_tts`). espeak-ng is not thread-safe, so every phonemization is
 * guarded by a single monitor shared with Piper TTS synthesis.
 *
 * The espeak-ng-data directory is copied from assets to app files on first use
 * (native espeak loads from real paths) and espeak is initialized exactly once.
 */
object EspeakSession {

    private val lock = Any()

    @Volatile
    private var initialized = false

    /** Copy espeak-ng-data from assets (once) and initialize the library. */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val dataDir = File(context.filesDir, "espeak-ng-data")
            if (!File(dataDir, "phondata").exists()) {
                Log.i(TAG, "Copying espeak-ng-data from assets…")
                copyAssetDir(context, "espeak-ng-data", dataDir)
            }
            val rc = PiperPhonemizer.nativeInit(dataDir.absolutePath)
            check(rc >= 0) { "espeak-ng init failed ($rc) path=${dataDir.absolutePath}" }
            Log.i(TAG, "espeak-ng initialized (rc=$rc)")
            initialized = true
        }
    }

    /** Returns IPA phonemes for [text] in [voice]; serialized against all espeak use. */
    fun phonemize(context: Context, voice: String, text: String): String = synchronized(lock) {
        ensureInitialized(context)
        PiperPhonemizer.nativePhonemize(voice, text)
    }

    private fun copyAssetDir(context: Context, assetDir: String, destDir: File) {
        destDir.mkdirs()
        context.assets.list(assetDir)?.forEach { name ->
            val child = File(destDir, name)
            if (context.assets.list("$assetDir/$name")?.isNotEmpty() == true) {
                copyAssetDir(context, "$assetDir/$name", child)
            } else {
                copyAsset(context, "$assetDir/$name", child)
            }
        }
    }

    private fun copyAsset(context: Context, assetPath: String, dest: File) {
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private const val TAG = "EspeakSession"
}
