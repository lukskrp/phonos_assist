package com.phonosassist.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.phonosassist.voice.SpeechSynthesizer
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Offline Piper TTS implementing the shared [SpeechSynthesizer] API.
 *
 * Voices are bundled in assets (English + Finnish) and copied to `filesDir/piper`
 * on first use. Phonemization runs in espeak-ng (native), synthesis on
 * onnxruntime-android, and playback on a single serialized worker thread so
 * streamed sentences never overlap.
 */
class PiperSpeechSynthesizer(private val context: Context) : SpeechSynthesizer {

    override var onComplete: (() -> Unit)? = null

    override var languageTag: String? = null

    private val synthesizer = PiperSynthesizer()
    private val lock = Any()
    private var loadedCode: String? = null
    private var currentConfig: PiperVoiceConfig? = null

    private val activeTrack = AtomicReference<AudioTrack?>(null)
    private val pending = AtomicInteger(0)

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "piper-tts").apply { isDaemon = true }
    }

    private fun codeFor(tag: String?): String? {
        val language = tag?.let { Locale.forLanguageTag(it).language.lowercase(Locale.ROOT) } ?: return null
        return if (VOICE_BY_CODE.containsKey(language)) language else null
    }

    override fun supportsLanguage(tag: String?): Boolean = codeFor(tag) != null

    override fun speak(text: String): Boolean {
        val code = codeFor(languageTag) ?: return false
        if (text.isBlank()) return false
        stop()
        enqueueTask(text, code)
        return true
    }

    override fun enqueue(text: String): Boolean {
        val code = codeFor(languageTag) ?: return false
        if (text.isBlank()) return false
        enqueueTask(text, code)
        return true
    }

    private fun enqueueTask(text: String, code: String) {
        pending.incrementAndGet()
        worker.submit {
            try {
                val audio = synthesize(text, code)
                if (audio != null) play(audio.first, audio.second)
            } catch (e: Exception) {
                Log.e(TAG, "Piper synthesis failed", e)
            } finally {
                if (pending.decrementAndGet() == 0) onComplete?.invoke()
            }
        }
    }

    override fun stop() {
        activeTrack.get()?.let { track ->
            runCatching { track.stop() }
            runCatching { track.flush() }
        }
    }

    override fun release() {
        stop()
        synthesizer.close()
        worker.shutdownNow()
    }

    private fun synthesize(text: String, code: String): Pair<ShortArray, Int>? = synchronized(lock) {
        val cfg = loadVoice(code) ?: return@synchronized null
        val phonemes = EspeakSession.phonemize(context, cfg.espeakVoice, text)
        if (phonemes.isBlank()) {
            Log.w(TAG, "Phonemization blank for text='${text.take(50)}'")
            return@synchronized null
        }
        val audio = synthesizer.synthesize(phonemes) ?: return@synchronized null
        audio to cfg.sampleRate
    }

    private fun loadVoice(code: String): PiperVoiceConfig? = synchronized(lock) {
        EspeakSession.ensureInitialized(context)
        if (loadedCode != code) {
            val name = VOICE_BY_CODE[code] ?: return@synchronized null
            val (onnx, cfgFile) = voiceFiles(name) ?: return@synchronized null
            if (!onnx.exists()) {
                Log.e(TAG, "Voice ONNX missing: ${onnx.absolutePath}")
                return@synchronized null
            }
            val cfg = PiperVoiceConfig.parse(cfgFile.readText())
            synthesizer.load(onnx, cfg)
            currentConfig = cfg
            loadedCode = code
            Log.i(TAG, "Loaded voice: $code (${onnx.name})")
        }
        currentConfig
    }

    private fun voiceFiles(name: String): Pair<File, File>? {
        val assetOnnx = "piper/$name.onnx"
        val assetCfg = "piper/$name.onnx.json"
        if (!assetExists(assetOnnx) || !assetExists(assetCfg)) return null
        val modelDir = File(context.filesDir, "piper")
        val onnx = File(modelDir, "$name.onnx")
        val cfg = File(modelDir, "$name.onnx.json")
        if (!onnx.exists() || !cfg.exists()) {
            modelDir.mkdirs()
            runCatching { copyAsset(assetOnnx, onnx) }
            runCatching { copyAsset(assetCfg, cfg) }
        }
        return onnx to cfg
    }

    private fun assetExists(path: String): Boolean =
        runCatching { context.assets.open(path).use { } }.isSuccess

    private fun copyAsset(assetPath: String, dest: File) {
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun play(audio: ShortArray, sampleRate: Int) {
        activeTrack.get()?.let { runCatching { it.release() } }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(minBuf, audio.size * 2))
            .build()

        activeTrack.set(track)
        try {
            runCatching {
                track.write(audio, 0, audio.size)
                track.play()
            }
            val durationMs = audio.size * 1000L / sampleRate
            val deadline = SystemClock.uptimeMillis() + durationMs
            while (SystemClock.uptimeMillis() < deadline) {
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    break
                }
                val current = activeTrack.get()
                if (current == null || current !== track || current.playState != AudioTrack.PLAYSTATE_PLAYING) break
            }
        } finally {
            if (activeTrack.compareAndSet(track, null)) {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    companion object {
        private const val TAG = "PiperSpeech"

        /** Bundled voices by 2-letter code. */
        val VOICE_BY_CODE = mapOf(
            "en" to "en_US-lessac-medium",
            "fi" to "fi_FI-harri-medium",
        )
    }
}
