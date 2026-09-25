package com.phonosassist.tts

import com.google.gson.Gson

/** Parsed Piper voice config from the `.onnx.json` shipped with each voice. */
data class PiperVoiceConfig(
    val sampleRate: Int,
    val espeakVoice: String,
    val noiseScale: Float,
    val lengthScale: Float,
    val noiseW: Float,
    val numSpeakers: Int,
    val phonemeIdMap: Map<String, List<Long>>,
) {
    companion object {
        private val gson = Gson()

        fun parse(json: String): PiperVoiceConfig {
            val root = runCatching { gson.fromJson(json, Root::class.java) }.getOrNull() ?: Root()
            return PiperVoiceConfig(
                sampleRate = root.audio?.sample_rate ?: 22050,
                espeakVoice = root.espeak?.voice ?: "en-us",
                noiseScale = root.inference?.noise_scale ?: 0.667f,
                lengthScale = root.inference?.length_scale ?: 1.0f,
                noiseW = root.inference?.noise_w ?: 0.8f,
                numSpeakers = root.num_speakers ?: 1,
                phonemeIdMap = root.phoneme_id_map ?: emptyMap(),
            )
        }

        private data class Root(
            val audio: Audio? = null,
            val espeak: Espeak? = null,
            val inference: Inference? = null,
            val num_speakers: Int? = null,
            val phoneme_id_map: Map<String, List<Long>>? = null,
        )

        private data class Audio(val sample_rate: Int? = null)

        private data class Espeak(val voice: String? = null)

        private data class Inference(
            val noise_scale: Float? = null,
            val length_scale: Float? = null,
            val noise_w: Float? = null,
        )
    }
}
