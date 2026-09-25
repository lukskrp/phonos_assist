package com.phonosassist.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SherpaOnnxService"

/**
 * Offline multilingual speech recognition via a sherpa-onnx Whisper model.
 *
 * Whisper is an *offline* (non-streaming) model: the whole utterance is captured,
 * then decoded once. Model files live in the app's external files dir so they are
 * easy to sideload without bundling tens of MB in the APK:
 *
 *   Android/data/com.phonosassist/files/models/whisper/
 *
 * Any `*encoder*.onnx`, `*decoder*.onnx`, and `*tokens*.txt` in that folder are
 * picked up (so `tiny-`/`base-`/`small-` etc. all work).
 */
class SherpaOnnxService(private val context: Context) {

    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    /** ISO-639-1 code passed to Whisper (e.g. "en", "fi"). */
    var language: String = "en"

    var onResultCallback: ((String) -> Unit)? = null
    var onErrorCallback: ((String) -> Unit)? = null

    private val sampleRateInHz = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val audioSource = MediaRecorder.AudioSource.MIC

    fun initialize(): Boolean {
        return try {
            val files = resolveModelFiles(context) ?: run {
                onErrorCallback?.invoke("Whisper model files not found")
                return false
            }

            val config = OfflineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = files.encoder,
                        decoder = files.decoder,
                        language = language,
                        task = "transcribe",
                    ),
                    tokens = files.tokens,
                    numThreads = 4,
                    debug = false,
                ),
                decodingMethod = "greedy_search",
            )

            recognizer = OfflineRecognizer(assetManager = null, config = config)
            Log.i(TAG, "Whisper recognizer initialized (lang=$language)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sherpa-onnx", e)
            onErrorCallback?.invoke("Failed to initialize Whisper: ${e.message}")
            false
        }
    }

    fun startRecording(): Boolean {
        if (!isRecording.compareAndSet(false, true)) {
            Log.w(TAG, "Already recording")
            return false
        }
        if (recognizer == null && !initialize()) {
            isRecording.set(false)
            return false
        }

        val minBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (minBytes == AudioRecord.ERROR || minBytes == AudioRecord.ERROR_BAD_VALUE) {
            isRecording.set(false)
            onErrorCallback?.invoke("Failed to get min buffer size")
            return false
        }

        return try {
            audioRecord = AudioRecord(
                audioSource,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                minBytes * 2,
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorCallback?.invoke("AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                isRecording.set(false)
                return false
            }

            audioRecord?.startRecording()
            recordingThread = Thread({ processAudioSamples() }, "Whisper-Recording").apply { start() }
            Log.i(TAG, "Started recording")
            true
        } catch (e: SecurityException) {
            isRecording.set(false)
            onErrorCallback?.invoke("Microphone permission not granted")
            false
        } catch (e: Exception) {
            isRecording.set(false)
            onErrorCallback?.invoke("Failed to start recording: ${e.message}")
            false
        }
    }

    fun stopRecording() {
        if (!isRecording.compareAndSet(true, false)) {
            Log.w(TAG, "Not recording or already stopping")
            return
        }
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }
        joinRecordingThread()
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio record", e)
        }
        audioRecord = null
        recordingThread = null
        Log.i(TAG, "Stopped recording")
    }

    /** Captures the whole utterance, then decodes once (Whisper is offline). */
    private fun processAudioSamples() {
        val stream: OfflineStream = recognizer?.createStream() ?: run {
            onErrorCallback?.invoke("Failed to create recognition stream")
            return
        }

        var samples = ShortArray(sampleRateInHz * 10)
        var count = 0
        val buffer = ShortArray(1024)

        try {
            while (isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    if (count + read > samples.size) {
                        samples = samples.copyOf((count + read) * 2)
                    }
                    buffer.copyInto(samples, count, 0, read)
                    count += read
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read error: $read")
                    break
                }
            }

            if (count == 0) {
                onErrorCallback?.invoke("No audio captured")
                return
            }

            val floats = FloatArray(count) { samples[it] / 32768.0f }
            stream.acceptWaveform(floats, sampleRate = sampleRateInHz)
            recognizer?.decode(stream)
            val text = recognizer?.getResult(stream)?.text?.trim().orEmpty()
            Log.i(TAG, "Recognition result: $text")
            if (text.isNotEmpty()) {
                onResultCallback?.invoke(text)
            } else {
                onErrorCallback?.invoke("No speech recognized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            onErrorCallback?.invoke("Error processing audio: ${e.message}")
        } finally {
            stream.release()
        }
    }

    fun destroy() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // ignore
        }
        joinRecordingThread()
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
        recordingThread = null
        recognizer?.release()
        recognizer = null
        Log.i(TAG, "SherpaOnnxService destroyed")
    }

    private fun joinRecordingThread() {
        val thread = recordingThread ?: return
        if (thread === Thread.currentThread()) return // called from within the capture
        try {
            thread.join(3_000)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted waiting for recording thread")
        }
    }

    data class ModelFiles(val encoder: String, val decoder: String, val tokens: String)

    companion object {
        private const val MODEL_SUBDIR = "models/whisper"

        fun modelDir(context: Context): File =
            context.getExternalFilesDir(null)?.resolve(MODEL_SUBDIR)
                ?: File(context.filesDir, MODEL_SUBDIR)

        fun resolveModelFiles(context: Context): ModelFiles? {
            val dir = modelDir(context)
            val files = dir.listFiles()?.filter { it.isFile } ?: return null
            val encoder = files.firstOrNull { it.name.contains("encoder", true) && it.name.endsWith(".onnx") }
            val decoder = files.firstOrNull { it.name.contains("decoder", true) && it.name.endsWith(".onnx") }
            val tokens = files.firstOrNull { it.name.contains("tokens", true) && it.name.endsWith(".txt") }
            return if (encoder != null && decoder != null && tokens != null) {
                ModelFiles(encoder.absolutePath, decoder.absolutePath, tokens.absolutePath)
            } else {
                null
            }
        }

        fun checkModelFiles(context: Context): Boolean = resolveModelFiles(context) != null

        /**
         * True when the sherpa-onnx native library can actually be loaded. The
         * bundled prebuilts are built against a specific onnxruntime ABI, so this
         * can fail on builds that ship a different onnxruntime.
         */
        fun isNativeAvailable(): Boolean = try {
            System.loadLibrary("sherpa-onnx-jni")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "sherpa-onnx native library unavailable: ${t.message}")
            false
        }
    }
}
