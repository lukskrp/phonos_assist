# PhonosAssist

A personal, hands-free voice assistant for Android, powered by a local LLM.

PhonosAssist records your voice, transcribes it on-device, sends the conversation
to an OpenAI-compatible LLM server you control, and speaks the reply back —
so nothing has to leave your own network.

## Features

- **Push-to-talk voice input** with a large, single-tap record button.
- **On-device speech-to-text** via the Android `SpeechRecognizer`, with optional
  multilingual recognition (English + Finnish).
- **Local LLM chat** over any OpenAI-compatible `/v1/chat/completions` endpoint.
- **Offline Piper text-to-speech** with bundled **Finnish** (`fi_FI-harri-medium`)
  and **English** (`en_US-lessac-medium`) voices, so replies are spoken correctly
  without relying on the device's TTS. Other languages fall back to the device
  TTS, and to English with a warning if no voice is available.
- **Chat history** stored locally in Room, with a drawer to load, delete, or
  clear conversations.
- **No cloud accounts, no telemetry.** Bring your own server.

## Requirements

- Android 8.0 (API 26) or newer.
- A reachable OpenAI-compatible LLM endpoint (e.g. `llama.cpp` server, Ollama,
  LM Studio, vLLM, or a gateway). The default assumes a server on your local
  network / Tailscale tailnet.

### Offline TTS (Piper)

Piper voices are bundled in the APK (which makes it fairly large). On first use,
`espeak-ng-data` and the selected voice are copied from assets into app storage.
The `espeak-ng` native library is built from source via CMake.

### Optional: Whisper multilingual STT (offline)

Enable **Use Sherpa-ONNX** in Settings to transcribe on-device with a
sherpa-onnx Whisper model instead of the platform recognizer. Models are not
bundled (they are tens of MB); put a Whisper export here:

```
Android/data/com.phonosassist/files/models/whisper/
```

Any `*encoder*.onnx`, `*decoder*.onnx`, and `*tokens*.txt` files in that folder
are picked up, so `tiny`/`base`/`small` etc. all work. For example, from a
sherpa-onnx release:

```
adb push sherpa-onnx-whisper-tiny/tiny-encoder.onnx \
  /sdcard/Android/data/com.phonosassist/files/models/whisper/
adb push sherpa-onnx-whisper-tiny/tiny-decoder.onnx \
  /sdcard/Android/data/com.phonosassist/files/models/whisper/
adb push sherpa-onnx-whisper-tiny/tiny-tokens.txt \
  /sdcard/Android/data/com.phonosassist/files/models/whisper/
```

The **I speak** language drives the transcription language. When the device's
platform recognizer doesn't support that language (common for Finnish), the app
automatically retries the capture with the offline Whisper model.

> **Note:** Whisper and Piper share one `libonnxruntime.so` (1.29.0). The vendored
> sherpa-onnx prebuilts were built against onnxruntime `1.27.1`, so their
> `VERS_1.27.1` symbol-version requirement (and its ELF hash) is patched to
> `VERS_1.29.0` (see `app/src/main/jniLibs`).

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Configure

Open **Settings** in the app and set:

- **LLM Host** / **LLM Port** — your server address.
- **LLM Model** — the model id your server exposes.
- **TTS Enabled**, **Finnish STT**, **Prefer offline STT**, **Use Sherpa-ONNX**.

The app talks to the server over HTTP by default and permits cleartext only for
known local/tailnet hosts (see
`app/src/main/res/xml/network_security_config.xml`). Debug builds allow
cleartext to any host for development.

## Contributing

Issues and pull requests are welcome. Please keep the app buildable with
`./gradlew assembleDebug` and add unit tests for pure logic where practical.

## License

Copyright (C) 2026 lukskrp

Licensed under the GNU General Public License v3.0 or later — see
[LICENSE](LICENSE).
