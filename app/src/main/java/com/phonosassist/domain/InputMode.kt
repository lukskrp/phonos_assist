package com.phonosassist.domain

/** How the user provides a prompt: voice recording (STT) or typed text (STT bypassed). */
enum class InputMode {
    VOICE,
    TEXT,
}
