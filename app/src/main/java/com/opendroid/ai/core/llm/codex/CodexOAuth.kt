// The OAuth details Codex signs in with, and the pure parts of that exchange.
//
// Everything here is deliberately free of Android and of the network, so the
// half of this flow that is easy to get subtly wrong - PKCE derivation, the
// authorize query, the callback parse, the account id buried in the id_token -
// can be tested on the JVM. The socket lives in CodexLoopbackReceiver and the
// HTTP calls in CodexAuthManager.
//
// The client is OpenAI's own public Codex registration. It is a public PKCE
// client, so there is no secret to hold, but the redirect and the originator
// are fixed by that registration rather than by us: port 1455 is not a choice,
// and an originator outside OpenAI's allowlist is answered with 403.

package com.opendroid.ai.core.llm.codex

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object CodexOAuth {

    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val AUTHORIZE_ENDPOINT = "https://auth.openai.com/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://auth.openai.com/oauth/token"

    /** Fixed by the app registration; a different port is simply not accepted. */
    const val REDIRECT_PORT = 1455
    const val REDIRECT_PATH = "/auth/callback"
    const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT$REDIRECT_PATH"

    const val SCOPE = "openid profile email offline_access"

    /**
     * Checked against an allowlist server-side. Values outside
     * `codex_cli_rs` / `codex_vscode` / `codex_sdk_ts` / `Codex*` get 403.
     */
    const val ORIGINATOR = "codex_cli_rs"
    const val USER_AGENT = "codex_cli_rs/0.0.1 (Android)"

    /** Namespace the ChatGPT account claims sit under inside the id_token. */
    private const val CLAIM_NAMESPACE = "https://api.openai.com/auth"

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    fun newPkce(random: SecureRandom = SecureRandom()): CodexPkce {
        val verifier = encoder.encodeToString(ByteArray(64).also(random::nextBytes))
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return CodexPkce(
            verifier = verifier,
            challenge = encoder.encodeToString(digest),
            state = encoder.encodeToString(ByteArray(32).also(random::nextBytes))
        )
    }

    fun authorizeUrl(pkce: CodexPkce): String {
        val query = linkedMapOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "scope" to SCOPE,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "state" to pkce.state,
            // Three flags the Codex registration expects. Without them the
            // consent screen either refuses the client or returns a token that
            // carries no organization claims, and the account id is then absent.
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
            "originator" to ORIGINATOR
        )
        val encoded = query.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "$AUTHORIZE_ENDPOINT?$encoded"
    }

    /**
     * Reads the browser's redirect. [target] is the request target of the
     * loopback GET, e.g. `/auth/callback?code=…&state=…`.
     *
     * The state is compared here rather than by the caller so that a callback
     * arriving from anything other than the request we started is rejected in
     * one place.
     */
    fun parseCallback(target: String, expectedState: String): CodexCallback {
        val path = target.substringBefore('?')
        if (path != REDIRECT_PATH && path != "/") {
            return CodexCallback.Ignored
        }
        val params = target.substringAfter('?', "")
            .split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                decodeComponent(key) to decodeComponent(value)
            }

        params["error"]?.let { error ->
            val description = params["error_description"].orEmpty()
            return CodexCallback.Failed(if (description.isBlank()) error else description)
        }
        val code = params["code"].orEmpty()
        if (code.isBlank()) return CodexCallback.Ignored
        if (params["state"] != expectedState) {
            return CodexCallback.Failed("The sign-in reply did not match this request.")
        }
        return CodexCallback.Code(code)
    }

    /** The ChatGPT account the plan belongs to, read from the id_token claims. */
    fun accountId(idToken: String): String? = claims(idToken)?.let { payload ->
        payload.stringOrNull("$CLAIM_NAMESPACE.chatgpt_account_id")
            ?: payload.nested(CLAIM_NAMESPACE)?.stringOrNull("chatgpt_account_id")
    }

    /** `plus`, `pro`, `team`, … - shown so the owner can see which plan is in use. */
    fun planType(idToken: String): String? = claims(idToken)?.let { payload ->
        payload.stringOrNull("$CLAIM_NAMESPACE.chatgpt_plan_type")
            ?: payload.nested(CLAIM_NAMESPACE)?.stringOrNull("chatgpt_plan_type")
    }

    fun email(idToken: String): String? = claims(idToken)?.stringOrNull("email")

    /**
     * The payload of a JWT we received over TLS from the token endpoint, read
     * without verifying the signature. That is the same thing the Codex CLI
     * does, and it is sound here for the same reason: these claims are only
     * used to label the session in the UI and to fill one request header. No
     * authorization decision is made from them.
     */
    private fun claims(idToken: String): JsonObject? {
        val segments = idToken.split('.')
        if (segments.size < 2) return null
        return try {
            val payload = String(decoder.decode(segments[1]), Charsets.UTF_8)
            JsonParser.parseString(payload).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.nested(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun decodeComponent(raw: String): String =
        try {
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
}

data class CodexPkce(
    val verifier: String,
    val challenge: String,
    val state: String
)

sealed interface CodexCallback {
    /** A request on the loopback port that is not the redirect we are waiting for. */
    object Ignored : CodexCallback
    data class Code(val value: String) : CodexCallback
    data class Failed(val message: String) : CodexCallback
}
