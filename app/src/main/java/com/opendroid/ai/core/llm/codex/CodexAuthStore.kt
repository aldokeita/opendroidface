// Where the ChatGPT session lives on the device.
//
// The refresh token here is long-lived and spends the owner's plan, so it sits
// behind the same boundary as every other secret in this app: an AES-GCM
// envelope whose key never leaves the Android Keystore. It is never logged and
// never leaves the process except as an Authorization header over TLS.

package com.opendroid.ai.core.llm.codex

import android.content.Context
import com.opendroid.ai.core.security.AndroidKeyStoreAeadCipher
import com.opendroid.ai.core.security.KeystoreSecretRecords
import com.opendroid.ai.core.security.SecretRecordResult
import com.opendroid.ai.core.security.SharedPreferencesSecretRecordStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CodexSession(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String = "",
    val planType: String = "",
    val email: String = "",
    /** Wall-clock millis; refreshed a little before this. */
    val expiresAtMillis: Long = 0L
) {
    fun isExpired(nowMillis: Long, marginMillis: Long = REFRESH_MARGIN_MILLIS): Boolean =
        expiresAtMillis <= nowMillis + marginMillis

    companion object {
        const val REFRESH_MARGIN_MILLIS = 120_000L
    }
}

@Singleton
class CodexAuthStore private constructor(
    private val records: KeystoreSecretRecords
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        records = KeystoreSecretRecords(
            storage = SharedPreferencesSecretRecordStorage(
                context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            ),
            cipher = AndroidKeyStoreAeadCipher(KEY_ALIAS)
        )
    )

    @Synchronized
    fun read(): CodexSession? {
        val raw = when (val result = records.read(SESSION_KEY, SESSION_AAD)) {
            is SecretRecordResult.Success -> result.value?.let { String(it, StandardCharsets.UTF_8) }
            // Key material gone (app data cleared, Keystore reset): the session
            // is unrecoverable, which is the same outcome as never having one.
            SecretRecordResult.Unrecoverable -> null
            SecretRecordResult.StorageUnavailable -> null
        } ?: return null
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<CodexSession>(raw)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun write(session: CodexSession) {
        val encoded = json.encodeToString(CodexSession.serializer(), session)
        when (records.write(SESSION_KEY, SESSION_AAD, encoded.toByteArray(StandardCharsets.UTF_8))) {
            is SecretRecordResult.Success -> Unit
            SecretRecordResult.Unrecoverable ->
                error("Codex session key material is unrecoverable")
            SecretRecordResult.StorageUnavailable ->
                error("Codex session storage is unavailable")
        }
    }

    @Synchronized
    fun clear() {
        runCatching { records.write(SESSION_KEY, SESSION_AAD, ByteArray(0)) }
    }

    companion object {
        const val PREFERENCES = "opendroid_codex"
        const val KEY_ALIAS = "opendroid.codex_session.aes_gcm.v1"
        private const val SESSION_KEY = "session"
        private const val SESSION_AAD = "codex-session"
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** JVM test seam that keeps the production constructor Keystore-only. */
        internal fun createForTest(records: KeystoreSecretRecords): CodexAuthStore =
            CodexAuthStore(records)
    }
}
