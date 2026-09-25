// Thin JNI bridge between Kotlin (com.phonosassist.tts.PiperPhonemizer) and
// espeak-ng. Only text-to-phoneme conversion is used; the Piper voice ONNX
// synthesis runs in Kotlin on onnxruntime-android.
//
// espeak-ng-data is bundled in app assets and copied to disk at runtime; the
// extracted directory path is passed to espeak_Initialize here.

#include <jni.h>
#include <android/log.h>

#include <espeak-ng/speak_lib.h>

#include <string>
#include <cstring>

#define LOG_TAG "PhonosAssistTTS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void select_voice(const std::string & voice) {
    if (voice.empty()) {
        return;
    }
    if (espeak_SetVoiceByName(voice.c_str()) == 0) {
        LOGI("espeak_SetVoiceByName(%s) ok", voice.c_str());
        return;
    }
    // By-name lookup can fail when no matching voice file is bundled (e.g. no
    // voices/fi). Fall back to selecting by the 2-letter language code so the
    // language's phoneme/syllable rules are used deterministically rather than
    // silently falling back to the default (English) voice.
    LOGW("espeak_SetVoiceByName(%s) failed; selecting by language", voice.c_str());
    std::string lang = voice.substr(0, 2);
    if (lang.size() == 2) {
        espeak_VOICE spec;
        std::memset(&spec, 0, sizeof(spec));
        spec.languages = lang.c_str();
        espeak_ERROR rc = espeak_SetVoiceByProperties(&spec);
        LOGI("espeak_SetVoiceByProperties(%s) rc=%d", lang.c_str(), (int)rc);
    }
}

static std::string utf8_from_jstring(JNIEnv * env, jstring js) {
    const char * chars = env->GetStringUTFChars(js, nullptr);
    std::string out = chars ? chars : "";
    env->ReleaseStringUTFChars(js, chars);
    return out;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_phonosassist_tts_PiperPhonemizer_nativeInit(
        JNIEnv * env, jobject /*thiz*/, jstring jdataPath) {
    const std::string dataPath = utf8_from_jstring(env, jdataPath);
    if (dataPath.empty()) {
        return -1;
    }
    // Synchronous audio output mode is required for phonemization; no audio is
    // actually played because we never call synthesize.
    int result = espeak_Initialize(
        AUDIO_OUTPUT_SYNCHRONOUS, /*buflength*/ 0, dataPath.c_str(), /*options*/ 0);
    if (result < 0) {
        LOGE("espeak_Initialize failed with %d", result);
    }
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_phonosassist_tts_PiperPhonemizer_nativePhonemize(
        JNIEnv * env, jobject /*thiz*/, jstring jvoice, jstring jtext) {
    const std::string voice = utf8_from_jstring(env, jvoice);
    const std::string text = utf8_from_jstring(env, jtext);
    if (text.empty()) {
        return env->NewStringUTF("");
    }

    if (!voice.empty()) {
        select_voice(voice);
    }

    // espeak_TextToPhonemes processes one clause at a time and advances the
    // input pointer; espeakPHONEMES_IPA (bit 1) matches the piper phoneme map.
    const char * textCursor = text.c_str();
    const void ** textptr = reinterpret_cast<const void **>(&textCursor);
    std::string allPhonemes;
    while (textptr != nullptr && *textptr != nullptr) {
        const char * clause = espeak_TextToPhonemes(textptr, espeakCHARS_AUTO, espeakPHONEMES_IPA);
        if (clause != nullptr && clause[0] != '\0') {
            allPhonemes += clause;
            allPhonemes += ' ';
        }
    }
    return env->NewStringUTF(allPhonemes.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_phonosassist_tts_PiperPhonemizer_nativeTerminate(JNIEnv * /*env*/, jobject /*thiz*/) {
    espeak_Terminate();
}
