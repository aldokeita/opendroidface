package com.opendroid.ai.core.llm.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class CodexOAuthTest {

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    @Test
    fun `challenge is the base64url sha-256 of the verifier`() {
        val pkce = CodexOAuth.newPkce()
        val expected = encoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray(Charsets.US_ASCII))
        )

        assertEquals(expected, pkce.challenge)
        // base64url, so nothing that would need escaping in a query string.
        assertTrue(pkce.challenge, pkce.challenge.none { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun `each request gets its own verifier and state`() {
        val first = CodexOAuth.newPkce()
        val second = CodexOAuth.newPkce()

        assertNotEquals(first.verifier, second.verifier)
        assertNotEquals(first.state, second.state)
    }

    @Test
    fun `authorize url carries the parameters the Codex registration expects`() {
        val pkce = CodexOAuth.newPkce()
        val url = CodexOAuth.authorizeUrl(pkce)

        assertTrue(url, url.startsWith("${CodexOAuth.AUTHORIZE_ENDPOINT}?"))
        assertTrue(url, url.contains("client_id=${CodexOAuth.CLIENT_ID}"))
        assertTrue(url, url.contains("code_challenge=${pkce.challenge}"))
        assertTrue(url, url.contains("code_challenge_method=S256"))
        assertTrue(url, url.contains("state=${pkce.state}"))
        assertTrue(url, url.contains("id_token_add_organizations=true"))
        assertTrue(url, url.contains("codex_cli_simplified_flow=true"))
        assertTrue(url, url.contains("originator=${CodexOAuth.ORIGINATOR}"))
        // The redirect has to survive encoding intact; port 1455 is not ours to pick.
        assertTrue(url, url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback"))
    }

    @Test
    fun `callback with the matching state yields the code`() {
        val callback = CodexOAuth.parseCallback("/auth/callback?code=abc123&state=xyz", "xyz")

        assertEquals(CodexCallback.Code("abc123"), callback)
    }

    @Test
    fun `callback from a different request is refused`() {
        val callback = CodexOAuth.parseCallback("/auth/callback?code=abc123&state=other", "xyz")

        assertTrue(callback.toString(), callback is CodexCallback.Failed)
    }

    @Test
    fun `error in the callback is surfaced with its description`() {
        val callback = CodexOAuth.parseCallback(
            "/auth/callback?error=access_denied&error_description=User%20said%20no",
            "xyz"
        )

        assertEquals(CodexCallback.Failed("User said no"), callback)
    }

    @Test
    fun `requests that are not the redirect are ignored rather than failed`() {
        assertEquals(CodexCallback.Ignored, CodexOAuth.parseCallback("/favicon.ico", "xyz"))
        assertEquals(CodexCallback.Ignored, CodexOAuth.parseCallback("/auth/callback", "xyz"))
    }

    @Test
    fun `account claims are read from the nested namespace`() {
        val idToken = idToken(
            """
            {
              "email": "owner@example.com",
              "https://api.openai.com/auth": {
                "chatgpt_account_id": "acct_123",
                "chatgpt_plan_type": "plus"
              }
            }
            """.trimIndent()
        )

        assertEquals("acct_123", CodexOAuth.accountId(idToken))
        assertEquals("plus", CodexOAuth.planType(idToken))
        assertEquals("owner@example.com", CodexOAuth.email(idToken))
    }

    @Test
    fun `account claims are also read from the flattened form`() {
        val idToken = idToken(
            """{"https://api.openai.com/auth.chatgpt_account_id":"acct_456"}"""
        )

        assertEquals("acct_456", CodexOAuth.accountId(idToken))
    }

    @Test
    fun `an unreadable id token yields no claims rather than throwing`() {
        assertNull(CodexOAuth.accountId("not-a-jwt"))
        assertNull(CodexOAuth.planType(""))
        assertNull(CodexOAuth.email("a.b"))
    }

    private fun idToken(payloadJson: String): String {
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = encoder.encodeToString(payloadJson.toByteArray())
        return "$header.$payload.signature"
    }
}
