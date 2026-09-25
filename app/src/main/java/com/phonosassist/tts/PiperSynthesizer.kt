package com.phonosassist.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File
import java.nio.LongBuffer
import kotlin.math.abs

/**
 * Piper VITS ONNX synthesis (port of piper.cpp `synthesize`).
 * Input: IPA phoneme string -> phoneme ids (bos/pad/eos interspersed) -> audio.
 */
class PiperSynthesizer {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var config: PiperVoiceConfig? = null

    fun load(onnxFile: File, config: PiperVoiceConfig) {
        close()
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        session = env.createSession(onnxFile.absolutePath, options)
        this.config = config
    }

    fun isLoaded(): Boolean = session != null

    /** Synthesize PCM16 samples from an IPA phoneme string. */
    fun synthesize(phonemes: String): ShortArray? {
        val cfg = config ?: return null
        val sess = session ?: return null

        val ids = phonemesToIds(phonemes, cfg)
        if (ids.isEmpty()) {
            Log.w(TAG, "phonemesToIds produced no ids for phonemes='${phonemes.take(60)}'")
            return null
        }
        val seqLen = ids.size.toLong()

        val input = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, seqLen))
        val inputLengths = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(seqLen)), longArrayOf(1))
        // Cap the VITS noise scales for stable, consistent output.
        val stableNoiseScale = minOf(cfg.noiseScale, 0.333f)
        val stableNoiseW = minOf(cfg.noiseW, 0.4f)
        val scales = OnnxTensor.createTensor(
            env,
            java.nio.FloatBuffer.wrap(floatArrayOf(stableNoiseScale, cfg.lengthScale, stableNoiseW)),
            longArrayOf(3),
        )

        val inputs = linkedMapOf<String, OnnxTensor>(
            "input" to input,
            "input_lengths" to inputLengths,
            "scales" to scales,
        )
        if (cfg.numSpeakers > 1) {
            inputs["sid"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
        }

        try {
            val result = sess.run(inputs)
            try {
                var tensor: OnnxTensor? = try {
                    result["output"] as? OnnxTensor
                } catch (e: Exception) {
                    Log.e(TAG, "No 'output' entry: ${e.message}")
                    null
                }
                if (tensor == null) {
                    for (entry in result) {
                        if (tensor == null && entry.value is OnnxTensor) {
                            tensor = entry.value as OnnxTensor
                        }
                    }
                }
                if (tensor == null) {
                    Log.e(TAG, "No OnnxTensor output found")
                    return null
                }
                val buffer = tensor.floatBuffer ?: run {
                    Log.e(TAG, "floatBuffer is null for output tensor")
                    return null
                }
                val count = buffer.remaining()
                val floats = FloatArray(count)
                buffer.get(floats)

                var maxAudio = 0.01f
                for (i in 0 until count) {
                    val value = abs(floats[i])
                    if (value > maxAudio) maxAudio = value
                }
                val scale = 32767.0f / maxOf(0.01f, maxAudio)
                val samples = ShortArray(count)
                for (i in 0 until count) {
                    val v = (floats[i] * scale).coerceIn(-32768f, 32767f)
                    samples[i] = v.toInt().toShort()
                }
                return samples
            } finally {
                result.close()
            }
        } finally {
            input.close()
            inputLengths.close()
            scales.close()
            inputs["sid"]?.close()
        }
    }

    /** Port of piper-phonemize `phonemes_to_ids` (bos + interspersed pad + eos). */
    private fun phonemesToIds(phonemes: String, cfg: PiperVoiceConfig): LongArray {
        val map = cfg.phonemeIdMap
        val pad = map["_"]?.firstOrNull() ?: 0L
        val bos = map["^"]?.firstOrNull() ?: 1L
        val eos = map["$"]?.firstOrNull() ?: 2L

        val result = mutableListOf<Long>()
        result.add(bos)
        result.add(pad)

        var i = 0
        while (i < phonemes.length) {
            val cp = phonemes.codePointAt(i)
            i += Character.charCount(cp)
            val phoneme = String(Character.toChars(cp))
            val ids = map[phoneme]
            if (ids != null) {
                result.addAll(ids)
                result.add(pad)
            }
        }
        result.add(eos)
        return result.toLongArray()
    }

    fun close() {
        session?.close()
        session = null
        config = null
    }

    private companion object {
        const val TAG = "PiperSynthesizer"
    }
}
