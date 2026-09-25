package com.phonosassist.service

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.phonosassist.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

data class ChatMessage(
    val role: String,
    val content: String,
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 32_768,
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
)

/**
 * Minimal OpenAI-compatible client. Requests are streamed (SSE) so the reply
 * can be shown — and spoken — token by token instead of waiting for the whole
 * completion.
 */
object LlmService {
    private val gson = Gson()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // Read timeout is disabled for streaming; connect/write stay bounded.
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }

    private fun endpoint(baseUrl: String): String =
        baseUrl.trimEnd('/') + "/v1/chat/completions"

    /** Streams assistant text deltas from the server. */
    fun streamChat(baseUrl: String, request: ChatRequest): Flow<String> = callbackFlow {
        val httpRequest = Request.Builder()
            .url(endpoint(baseUrl))
            .post(
                gson.toJson(request.copy(stream = true))
                    .toRequestBody(jsonMedia),
            )
            .build()

        val call = client.newCall(httpRequest)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body?.string()?.take(300).orEmpty()
                        close(
                            IOException(
                                "HTTP ${response.code}" +
                                    if (detail.isNotBlank()) " - $detail" else "",
                            ),
                        )
                        return@use
                    }
                    val source = response.body?.source() ?: run {
                        close(IOException("empty response body"))
                        return@use
                    }
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.substring(5).trim()
                        if (payload == "[DONE]") break
                        extractDelta(payload)?.let { trySend(it) }
                    }
                    close()
                }
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    /** Non-streaming convenience wrapper (collects the full reply). */
    suspend fun complete(baseUrl: String, request: ChatRequest): String {
        val builder = StringBuilder()
        streamChat(baseUrl, request).collect { builder.append(it) }
        return builder.toString()
    }

    /** Pulls `choices[0].delta.content` (or `message.content`) from an SSE payload. */
    private fun extractDelta(payload: String): String? = runCatching {
        val root = JsonParser.parseString(payload).asJsonObject
        val choices = root.getAsJsonArray("choices") ?: return@runCatching null
        val choice = choices.firstOrNull()?.asJsonObject ?: return@runCatching null
        val content = choice.getAsJsonObject("delta")?.get("content")
            ?: choice.getAsJsonObject("message")?.get("content")
        content?.takeIf { !it.isJsonNull }?.asString
    }.getOrNull()
}
