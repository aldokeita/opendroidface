// Catches the OAuth redirect on the phone itself.
//
// The Codex app registration redirects to http://localhost:1455/auth/callback,
// which on a desktop is caught by the CLI's own listener. Nothing about that is
// desktop-specific: the browser here is on the same device, so `localhost` is
// this phone, and the listener can be ours.
//
// Two sockets, not one. `localhost` may resolve to 127.0.0.1 or to ::1 - which
// one a given browser picks is not ours to decide - so both loopback addresses
// are bound and whichever the browser connects to wins. Neither is reachable
// from the network: the bind is to a loopback address, never to a wildcard.

package com.opendroid.ai.core.llm.codex

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class CodexLoopbackReceiver private constructor(
    private val sockets: List<ServerSocket>
) : Closeable {

    /**
     * Waits for the browser to come back. Returns null on timeout, so the
     * caller can tell "the person gave up" apart from "the reply was bad".
     */
    suspend fun awaitCallback(expectedState: String, timeoutMillis: Long): CodexCallback? =
        withTimeoutOrNull(timeoutMillis) {
            val results = Channel<CodexCallback>(capacity = sockets.size)
            coroutineScope {
                sockets.forEach { socket -> serve(socket, expectedState, results) }
                val first = results.receive()
                // Every accept() is blocking and cannot be interrupted by
                // cancellation, so the sockets are what release these jobs.
                close()
                first
            }
        }

    private fun CoroutineScope.serve(
        socket: ServerSocket,
        expectedState: String,
        results: Channel<CodexCallback>
    ) = launch(Dispatchers.IO) {
        while (!socket.isClosed) {
            val callback = try {
                socket.accept().use { connection -> handle(connection, expectedState) }
            } catch (_: IOException) {
                return@launch // closed by the winner, or by close()
            }
            // A browser also asks for /favicon.ico; those are not the redirect.
            if (callback !is CodexCallback.Ignored) {
                results.trySend(callback)
                return@launch
            }
        }
    }

    private fun handle(connection: Socket, expectedState: String): CodexCallback {
        val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine().orEmpty()
        // "GET /auth/callback?code=… HTTP/1.1"
        val target = requestLine.split(' ').getOrNull(1).orEmpty()
        val callback = CodexOAuth.parseCallback(target, expectedState)
        respond(connection, callback)
        return callback
    }

    private fun respond(connection: Socket, callback: CodexCallback) {
        val body = when (callback) {
            is CodexCallback.Code -> PAGE_DONE
            is CodexCallback.Failed -> PAGE_FAILED
            CodexCallback.Ignored -> ""
        }
        val status = if (callback is CodexCallback.Ignored) "404 Not Found" else "200 OK"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            connection.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
    }

    override fun close() {
        sockets.forEach { socket -> runCatching { socket.close() } }
    }

    companion object {
        private const val PAGE_DONE =
            "<!doctype html><meta name=viewport content=\"width=device-width,initial-scale=1\">" +
                "<body style=\"background:#080C10;color:#E8ECF1;font:16px system-ui;" +
                "display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">" +
                "<p>Signed in. You can close this tab and return to OpenDroid.</p>"

        private const val PAGE_FAILED =
            "<!doctype html><meta name=viewport content=\"width=device-width,initial-scale=1\">" +
                "<body style=\"background:#080C10;color:#E8ECF1;font:16px system-ui;" +
                "display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">" +
                "<p>Sign-in did not complete. Return to OpenDroid and try again.</p>"

        /**
         * @throws IOException if neither loopback address could take the port -
         * usually because another app already holds 1455, which the caller has
         * to surface rather than work around: the port is not negotiable.
         */
        suspend fun open(): CodexLoopbackReceiver = withContext(Dispatchers.IO) {
            val bound = listOf("127.0.0.1", "::1").mapNotNull { host ->
                runCatching {
                    ServerSocket(CodexOAuth.REDIRECT_PORT, 4, InetAddress.getByName(host))
                }.getOrNull()
            }
            if (bound.isEmpty()) {
                throw IOException("Port ${CodexOAuth.REDIRECT_PORT} is already in use on this device.")
            }
            CodexLoopbackReceiver(bound)
        }
    }
}
