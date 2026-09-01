// Codex, talking straight to the ChatGPT backend from the phone.
//
// The credential is the OAuth session held by CodexAuthManager, not an API
// key, and the wire format is the Responses API rather than chat completions -
// so this cannot reuse the Custom OpenAI transport. Three things about that
// endpoint are not optional, and each one answers with an error if omitted:
//
//   * `originator` must be a value OpenAI's allowlist recognises, or 403
//   * `store` must be false, and `include` must carry the encrypted reasoning,
//     because with no server-side state there is nowhere else for it to live
//   * `instructions` must be non-empty
//
// Only the streaming form is used. The backend is SSE-first, and the agent
// reads tokens as they arrive anyway.

package com.opendroid.ai.core.llm.providers

import android.util.Log
import com.google.gson.Gson
import com.opendroid.ai.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.opendroid.ai.core.llm.LLMProvider
import com.opendroid.ai.core.llm.LLMRequest
import com.opendroid.ai.core.llm.LLMResponse
import com.opendroid.ai.core.llm.ResponseFormat
import com.opendroid.ai.core.llm.codex.CodexAuthManager
import com.opendroid.ai.core.llm.codex.CodexOAuth
import com.opendroid.ai.core.llm.error.LLMErrorMapper
import com.opendroid.ai.core.llm.error.ProviderErrorDetail
import com.opendroid.ai.core.llm.error.toSafeProviderException
import com.opendroid.ai.data.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodexProvider @Inject constructor(
    private val client: OkHttpClient,
    private val auth: CodexAuthManager
) : LLMProvider {

    override val name: String get() = PROVIDER_NAME

    override val availableModels: List<String> =
        listOf("gpt-5-codex", "gpt-5", "codex-mini-latest")

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun complete(request: LLMRequest): LLMResponse {
        val startedAt = System.currentTimeMillis()
        val model = modelOf(request)
        val text = StringBuilder()
        var tokens = 0

        stream(request, model) { event ->
            when (event) {
                is CodexEvent.Delta -> text.append(event.text)
                is CodexEvent.Completed -> tokens = event.totalTokens
            }
        }

        if (text.isBlank()) {
            throw LLMErrorMapper.malformed(name, model, transient = true)
        }
        return LLMResponse(
            content = text.toString(),
            tokensUsed = tokens,
            model = model,
            provider = name,
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }

    override fun streamComplete(request: LLMRequest): Flow<String> = flow {
        val model = modelOf(request)
        stream(request, model) { event ->
            if (event is CodexEvent.Delta) emit(event.text)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun isAvailable(): Boolean = auth.isSignedIn()

    /**
     * Opens the SSE stream and hands each interesting event to [onEvent].
     * Suspending inside the callback is what lets [streamComplete] emit
     * straight from here without buffering the whole answer first.
     */
    private suspend inline fun stream(
        request: LLMRequest,
        model: String,
        onEvent: (CodexEvent) -> Unit
    ) {
        val bearer = auth.bearer() ?: throw LLMErrorMapper.authMissing(name, model)
        val httpRequest = Request.Builder()
            .url(RESPONSES_ENDPOINT)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "text/event-stream")
            .header("originator", CodexOAuth.ORIGINATOR)
            .header("User-Agent", CodexOAuth.USER_AGENT)
            .header("session_id", UUID.randomUUID().toString())
            .apply {
                auth.accountId()?.let { header("ChatGPT-Account-Id", it) }
            }
            .post(gson.toJson(payload(request, model)).toRequestBody(mediaType))
            .build()

        // The gap between two SSE events can be long while the model reasons,
        // so the shared client's read timeout is widened for this call only.
        val streaming = client.newBuilder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        streaming.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw failure(response, request, bearer, model)
            readEvents(response, model, onEvent)
        }
    }

    private inline fun readEvents(
        response: Response,
        model: String,
        onEvent: (CodexEvent) -> Unit
    ) {
        val source = response.body.source()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith(DATA_PREFIX)) continue
            val data = line.removePrefix(DATA_PREFIX).trim()
            if (data.isEmpty() || data == "[DONE]") continue

            val event = try {
                JsonParser.parseString(data).asJsonObject
            } catch (_: Exception) {
                continue
            }
            when (event.string("type")) {
                "response.output_text.delta" ->
                    event.string("delta")?.let { onEvent(CodexEvent.Delta(it)) }

                "response.completed" -> onEvent(
                    CodexEvent.Completed(
                        event.obj("response")?.obj("usage")?.int("total_tokens") ?: 0
                    )
                )

                "response.failed", "error" -> throw LLMErrorMapper.malformed(
                    provider = name,
                    model = model,
                    transient = true
                )
            }
        }
    }

    private fun failure(
        response: Response,
        request: LLMRequest,
        bearer: String,
        model: String
    ): Throwable {
        if (BuildConfig.DEBUG) {
            // Debug builds only. The Codex backend answers a rejected client
            // identity and an out-of-reach model with the same status, and the
            // difference is only in the body - which the safe detail strips.
            val peeked = runCatching { response.peekBody(2048).string() }.getOrDefault("")
            Log.w(TAG, "Codex HTTP ${response.code}: ${peeked.take(1000)}")
        }
        return failureFor(response, request, bearer, model)
    }

    private fun failureFor(
        response: Response,
        request: LLMRequest,
        bearer: String,
        model: String
    ): Throwable = when (response.code) {
        // An expired or revoked grant. Signing in again is the only fix, which
        // is what AuthMissing tells the UI to say.
        401 -> LLMErrorMapper.authMissing(name, model)
        // 403 is NOT routed here: the backend uses it for a rejected client
        // identity and for a model the account may not call, and collapsing
        // those into "sign in again" sends people to a screen that will not
        // help. The safe detail carries the vendor's own code instead.
        else -> response.toSafeProviderException(
            provider = ProviderErrorDetail.Provider.CODEX,
            request = request,
            knownSecrets = listOf(bearer)
        )
    }

    private fun payload(request: LLMRequest, model: String): Map<String, Any> {
        val instructions = buildString {
            append(request.systemPrompt.ifBlank { DEFAULT_INSTRUCTIONS })
            // The Responses text-format field is not accepted on this endpoint,
            // so JSON is asked for the way every other constraint is: in words.
            if (request.responseFormat == ResponseFormat.JSON) {
                append("\n\nReply with a single JSON object and nothing else.")
            }
        }
        return mapOf(
            "model" to model,
            "instructions" to instructions,
            "input" to request.messages.map(::toInputItem),
            "store" to false,
            "stream" to true,
            "include" to listOf("reasoning.encrypted_content")
        )
        // Neither temperature nor max_output_tokens: this endpoint answers
        // "Unsupported parameter" for both. Length is the model's to decide.
    }

    private fun toInputItem(message: ChatMessage): Map<String, Any> {
        val fromUser = message.sender == ChatMessage.Sender.USER
        val textType = if (fromUser) "input_text" else "output_text"
        val content = mutableListOf<Map<String, Any>>(
            mapOf("type" to textType, "text" to message.text)
        )
        if (fromUser) {
            message.imageBase64?.let { image ->
                content += mapOf(
                    "type" to "input_image",
                    "image_url" to "data:image/jpeg;base64,$image"
                )
            }
        }
        return mapOf(
            "type" to "message",
            "role" to if (fromUser) "user" else "assistant",
            "content" to content
        )
    }

    private fun modelOf(request: LLMRequest): String =
        request.model?.takeIf { it.isNotBlank() && it != LEGACY_MODEL_SEED } ?: DEFAULT_MODEL

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt

    /** Only the events this provider acts on; everything else is skipped. */
    sealed interface CodexEvent {
        data class Delta(val text: String) : CodexEvent
        data class Completed(val totalTokens: Int) : CodexEvent
    }

    companion object {
        const val PROVIDER_NAME = "Codex"
        const val DEFAULT_MODEL = "gpt-5-codex"

        /** What the bridge build stored as the model; not a real model id. */
        private const val LEGACY_MODEL_SEED = "codex"

        private const val RESPONSES_ENDPOINT = "https://chatgpt.com/backend-api/codex/responses"
        private const val DATA_PREFIX = "data:"
        private const val READ_TIMEOUT_SECONDS = 180L
        private const val DEFAULT_INSTRUCTIONS = "You are a helpful assistant."
        private const val TAG = "CodexProvider"
    }
}
