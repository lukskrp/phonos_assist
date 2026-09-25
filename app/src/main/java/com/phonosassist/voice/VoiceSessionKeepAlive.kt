package com.phonosassist.voice

import android.content.Context
import com.phonosassist.service.AssistantForegroundService

/**
 * Keeps the process alive for the duration of a voice turn. Abstracted so the
 * voice module isn't tied to this app's foreground service; a host app can pass
 * a no-op or its own implementation.
 */
interface VoiceSessionKeepAlive {
    /** Invoked when the user dismisses/stops the session (e.g. notification). */
    var onStopRequested: (() -> Unit)?

    fun startSession()
    fun endSession()
}

/** Default keep-alive: a microphone-typed foreground service with a Stop action. */
class ForegroundSessionKeepAlive(private val context: Context) : VoiceSessionKeepAlive {

    private val app = context.applicationContext

    override var onStopRequested: (() -> Unit)? = null

    init {
        AssistantForegroundService.onStopRequested = { onStopRequested?.invoke() }
    }

    override fun startSession() = AssistantForegroundService.start(app)

    override fun endSession() = AssistantForegroundService.stop(app)
}
