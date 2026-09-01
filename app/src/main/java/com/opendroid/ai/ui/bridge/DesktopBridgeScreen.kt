// The screen that makes the MCP server usable — and, if the owner insists,
// reachable from the network.
//
// The server has been running on 127.0.0.1:8765 since the first release, guarded
// by a random token in a Keystore envelope. Nothing ever showed that token, so
// there was no honest way to connect a desktop client to it. This screen is that
// missing half: it shows the token, lets it be rotated, and explains the two
// ways to reach the phone.
//
// It is also the only place network exposure can be turned on. That switch hands
// anyone on the same Wi-Fi a shell on this phone if they hold the token, so it
// asks first, in those words.

package com.opendroid.ai.ui.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.core.bridge.MCP_PORT
import com.opendroid.ai.core.bridge.localIpv4Addresses
import com.opendroid.ai.core.bridge.maskToken
import com.opendroid.ai.core.bridge.mcpEndpointUrl
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DesktopBridgeScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalOpenDroidColors.current
    val configStore = rememberMcpConfigStore()
    val policy = rememberMcpNetworkPolicy()
    val server = rememberMcpServer()
    val exposed by policy.networkExposed.collectAsState()

    var token by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var confirmExposure by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }

    // Reading the token unwraps a Keystore envelope, and generates one the first
    // time. Off the main thread, since a slow Keystore would otherwise stall the
    // frame this screen appears on.
    LaunchedEffect(Unit) {
        token = withContext(Dispatchers.IO) { configStore.accessToken() }
    }
    // Rebuilt on every recomposition rather than remembered: joining another
    // network while this screen is open changes the answer.
    val addresses = if (exposed) localIpv4Addresses() else emptyList()

    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.bridge_title),
                        color = colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (exposed) "Listening on the network, port $MCP_PORT"
                        else "Listening on this phone only, port $MCP_PORT",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = colors.textSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))

            Section(title = "Over USB") {
                Text(
                    stringResource(R.string.bridge_forward_hint),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Mono("adb forward tcp:$MCP_PORT tcp:$MCP_PORT")
                Spacer(Modifier.height(6.dp))
                Mono(mcpEndpointUrl("127.0.0.1"))
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { copy(context, "adb forward tcp:$MCP_PORT tcp:$MCP_PORT") }) {
                    Text(stringResource(R.string.bridge_copy_command), color = colors.accentCyan)
                }
            }

            Spacer(Modifier.height(16.dp))

            Section(title = "Access token") {
                Text(
                    stringResource(R.string.bridge_token_hint),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                // Masked by default. This screen is exactly the one someone
                // screenshots to ask for help with.
                Mono(if (revealed) token else maskToken(token))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(if (revealed) "Hide" else "Reveal", color = colors.accentCyan)
                    }
                    TextButton(onClick = { copy(context, token, sensitive = true) }) {
                        Text(stringResource(R.string.common_copy), color = colors.accentCyan)
                    }
                    TextButton(onClick = { confirmRotate = true }) {
                        Text(stringResource(R.string.bridge_regenerate), color = colors.accentRed)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Section(title = "Over the network") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.bridge_reachable),
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                        )
                        Text(
                            stringResource(R.string.bridge_network_hint),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = exposed,
                        onCheckedChange = { wanted ->
                            if (wanted) {
                                confirmExposure = true
                            } else {
                                policy.setNetworkExposed(false)
                                server.restart()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accentRed),
                    )
                }

                if (exposed) {
                    Spacer(Modifier.height(12.dp))
                    if (addresses.isEmpty()) {
                        Text(
                            stringResource(R.string.bridge_no_address),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    } else {
                        Text(stringResource(R.string.bridge_point_client), color = colors.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        addresses.forEach { address ->
                            Mono(mcpEndpointUrl(address))
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmExposure) {
        AlertDialog(
            onDismissRequest = { confirmExposure = false },
            title = { Text(stringResource(R.string.bridge_expose_title)) },
            text = {
                Text(
                    stringResource(R.string.bridge_expose_body) + "\n\n" +
                        "Only do this on a network you trust, and regenerate the token if " +
                        "it has ever been shown to anyone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExposure = false
                    policy.setNetworkExposed(true)
                    // The bind address is fixed when the socket opens, so the
                    // switch means nothing until the server is re-opened.
                    server.restart()
                }) {
                    Text(stringResource(R.string.bridge_expose_confirm), color = colors.accentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExposure = false }) {
                    Text(stringResource(R.string.bridge_keep_local), color = colors.textSecondary)
                }
            },
        )
    }

    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = { Text(stringResource(R.string.bridge_regenerate_title)) },
            text = { Text(stringResource(R.string.bridge_regenerate_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRotate = false
                    token = configStore.rotateAccessToken()
                    revealed = false
                }) {
                    Text(stringResource(R.string.bridge_regenerate), color = colors.accentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRotate = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = LocalOpenDroidColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                color = colors.textSecondary,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Anything meant to be typed or pasted verbatim. */
@Composable
private fun Mono(text: String) {
    val colors = LocalOpenDroidColors.current
    Text(
        text = text,
        color = colors.textPrimary,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private fun copy(context: Context, text: String, sensitive: Boolean = false) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("OpenDroid", text)
    if (sensitive) {
        // Keeps the token out of the clipboard preview Android 13+ pops up over
        // whatever is on screen.
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}








