// Signing in to Codex, entirely on the phone.
//
// The sequence is the ordinary OAuth authorization-code flow with PKCE:
//
//   1. bind the loopback listener first, so the redirect cannot arrive early
//   2. open the consent page in a Custom Tab
//   3. catch the redirect, check the state, exchange the code for tokens
//   4. keep the tokens in the Keystore-backed store and refresh them on demand
//
// No computer, no bridge, no shared secret to type in.

package com.opendroid.ai.core.llm.codex

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodexAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val store: CodexAuthStore
) {

    private val _state = MutableStateFlow<CodexAccountState>(CodexAccountState.Unknown)
    val state: StateFlow<CodexAccountState> = _state.asStateFlow()

    private val refreshLock = Mutex()

    /** Publishes what is already stored, without touching the network. */
    fun refreshState() {
        _state.value = store.read()?.let(::signedIn) ?: CodexAccountState.SignedOut
    }

    fun isSignedIn(): Boolean = store.read() != null

    /**
     * Runs the whole flow. Suspends until the person finishes in the browser,
     * gives up, or [SIGN_IN_TIMEOUT_MILLIS] passes.
     */
    suspend fun signIn(): CodexAccountState {
        _state.value = CodexAccountState.Working
        val outcome = runCatching { performSignIn() }
            .getOrElse { error ->
                CodexAccountState.Failed(error.message ?: "Sign-in could not be completed.")
            }
        _state.value = outcome
        return outcome
    }

    fun signOut() {
        store.clear()
        _state.value = CodexAccountState.SignedOut
    }

    /**
     * A usable access token, refreshed when it is close to expiry. Null when
     * there is no session, or when the refresh token has itself been revoked -
     * in which case the stored session is dropped, because a refresh token the
     * server rejects will not start working again.
     */
    suspend fun bearer(): String? = refreshLock.withLock {
        val session = store.read() ?: return null
        if (!session.isExpired(System.currentTimeMillis())) return session.accessToken

        val refreshed = runCatching { exchange(refreshBody(session.refreshToken), session) }
            .getOrElse { error ->
                if (error is CodexAuthRevoked) {
                    store.clear()
                    _state.value = CodexAccountState.SignedOut
                }
                return null
            }
        store.write(refreshed)
        _state.value = signedIn(refreshed)
        refreshed.accessToken
    }

    /** The account header the Codex backend requires alongside the bearer. */
    fun accountId(): String? = store.read()?.accountId?.takeIf { it.isNotBlank() }

    private suspend fun performSignIn(): CodexAccountState {
        val pkce = CodexOAuth.newPkce()
        CodexLoopbackReceiver.open().use { receiver ->
            openConsentPage(CodexOAuth.authorizeUrl(pkce))
            val callback = receiver.awaitCallback(pkce.state, SIGN_IN_TIMEOUT_MILLIS)
                ?: return CodexAccountState.Failed("Sign-in timed out. Try again.")
            val code = when (callback) {
                is CodexCallback.Code -> callback.value
                is CodexCallback.Failed -> return CodexAccountState.Failed(callback.message)
                CodexCallback.Ignored -> return CodexAccountState.Failed("No sign-in reply was received.")
            }
            val session = exchange(authorizationCodeBody(code, pkce.verifier), previous = null)
            store.write(session)
            return signedIn(session)
        }
    }

    private fun openConsentPage(url: String) {
        val uri = url.toUri()
        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            customTabs.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            // No browser supports Custom Tabs; a plain view intent still lands
            // on the same page and still redirects back to our listener.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun authorizationCodeBody(code: String, verifier: String): FormBody = FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("code", code)
        .add("redirect_uri", CodexOAuth.REDIRECT_URI)
        .add("client_id", CodexOAuth.CLIENT_ID)
        .add("code_verifier", verifier)
        .build()

    private fun refreshBody(refreshToken: String): FormBody = FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("refresh_token", refreshToken)
        .add("client_id", CodexOAuth.CLIENT_ID)
        .add("scope", CodexOAuth.SCOPE)
        .build()

    private suspend fun exchange(body: FormBody, previous: CodexSession?): CodexSession =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(CodexOAuth.TOKEN_ENDPOINT)
                .header("User-Agent", CodexOAuth.USER_AGENT)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // The body can carry the code or the refresh token back at
                    // us, so only the status is allowed out of here.
                    if (response.code == 400 || response.code == 401) {
                        throw CodexAuthRevoked("Sign-in was rejected (HTTP ${response.code}). Sign in again.")
                    }
                    throw IOException("Token exchange failed with HTTP ${response.code}.")
                }
                val payload = JsonParser.parseString(response.body.string()).asJsonObject
                val accessToken = payload.stringOrNull("access_token")
                    ?: throw IOException("Token exchange returned no access token.")
                val refreshToken = payload.stringOrNull("refresh_token")
                    ?: previous?.refreshToken
                    ?: throw IOException("Token exchange returned no refresh token.")
                val idToken = payload.stringOrNull("id_token").orEmpty()
                val expiresIn = payload.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong ?: 3600L

                CodexSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = CodexOAuth.accountId(idToken) ?: previous?.accountId.orEmpty(),
                    planType = CodexOAuth.planType(idToken) ?: previous?.planType.orEmpty(),
                    email = CodexOAuth.email(idToken) ?: previous?.email.orEmpty(),
                    expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
                )
            }
        }

    private fun signedIn(session: CodexSession) = CodexAccountState.SignedIn(
        email = session.email,
        planType = session.planType
    )

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private companion object {
        /** Long enough to find the password and pass 2FA, short enough to end. */
        const val SIGN_IN_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}

/** The server refused the grant; the stored session cannot be recovered. */
class CodexAuthRevoked(message: String) : IOException(message)

sealed interface CodexAccountState {
    /** Not read from storage yet. */
    object Unknown : CodexAccountState
    object Working : CodexAccountState
    object SignedOut : CodexAccountState
    data class SignedIn(val email: String, val planType: String) : CodexAccountState
    data class Failed(val message: String) : CodexAccountState
}
