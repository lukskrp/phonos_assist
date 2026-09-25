package com.phonosassist.tts

/** Thin JNI facade over espeak-ng (see `app/src/main/cpp/tts_jni.cpp`). */
object PiperPhonemizer {

    init {
        System.loadLibrary("phonosassist_tts")
    }

    /** Returns the sample rate on success, or a negative error code. */
    external fun nativeInit(dataPath: String): Int

    /** Returns the IPA phoneme string for [text] in [voice] (space separated). */
    external fun nativePhonemize(voice: String, text: String): String

    external fun nativeTerminate()
}
