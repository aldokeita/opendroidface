// Where the MCP server is allowed to listen.
//
// The server hands out `run_privileged_command`, which is a shell on this phone
// through Shizuku or root, and `execute_action`, which drives the Accessibility
// service. Reaching it means owning the device. It therefore listens on loopback
// only, unless the owner has explicitly said otherwise in the app — never as a
// side effect of any other setting, and never by default.
//
// The bearer token that guards it is upstream's (McpConfigStore.accessToken):
// 122 random bits, kept in a Keystore envelope, compared in constant time. That
// is what makes exposure survivable at all; this file only decides the interface.

package com.opendroid.ai.core.bridge

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/** Loopback: reachable only from this phone, or through `adb forward`. */
const val MCP_LOOPBACK = "127.0.0.1"

/** Every interface, which in practice means every device on the same Wi-Fi. */
const val MCP_ALL_INTERFACES = "0.0.0.0"

const val MCP_PORT = 8765

fun mcpBindAddress(networkExposed: Boolean): String =
    if (networkExposed) MCP_ALL_INTERFACES else MCP_LOOPBACK

fun mcpEndpointUrl(host: String, port: Int = MCP_PORT): String = "http://$host:$port/mcp"

/**
 * A token, shown with only its ends legible.
 *
 * Used wherever the token appears next to something that might be screenshotted
 * or screen-shared. The full value is behind a deliberate tap.
 */
fun maskToken(token: String): String = when {
    token.isEmpty() -> ""
    token.length <= 10 -> "•".repeat(token.length)
    else -> token.take(4) + "…" + token.takeLast(4)
}

/**
 * The phone's own addresses on the networks it is joined to, for showing the
 * user which URL to point a desktop client at.
 *
 * IPv4 only, and no loopback: this list exists to answer "what do I type on the
 * laptop", and neither ::1 nor a link-local address answers that.
 */
fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
        .map { it.hostAddress.orEmpty() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}.getOrDefault(emptyList())

@Singleton
class McpNetworkPolicy @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Default false, and read as false whenever the preference is missing or
    // unreadable. Every failure path here has to land on "loopback".
    private val _networkExposed = MutableStateFlow(prefs.getBoolean(KEY_EXPOSED, false))

    val networkExposed: StateFlow<Boolean> = _networkExposed.asStateFlow()

    fun bindAddress(): String = mcpBindAddress(_networkExposed.value)

    /**
     * Changing this needs the server restarted: the bind address is fixed when
     * the socket opens, so a live server keeps listening where it was.
     */
    fun setNetworkExposed(exposed: Boolean) {
        if (_networkExposed.value == exposed) return
        _networkExposed.value = exposed
        prefs.edit { putBoolean(KEY_EXPOSED, exposed) }
    }

    private companion object {
        const val PREFS = "opendroid_mcp_network"
        const val KEY_EXPOSED = "network_exposed"
    }
}
