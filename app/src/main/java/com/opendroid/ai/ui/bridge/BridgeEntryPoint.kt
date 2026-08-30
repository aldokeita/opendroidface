// Hilt lookup for the desktop-bridge screen.
//
// Same reason as ui/face/FaceEntryPoint: this screen is shown from ChatScreen,
// which is not a Hilt-injected composable, and an entry point reaches the
// singletons from our own code without changing how upstream wires anything.

package com.opendroid.ai.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.opendroid.ai.core.bridge.McpNetworkPolicy
import com.opendroid.ai.core.service.McpConfigStore
import com.opendroid.ai.core.service.McpServer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BridgeEntryPoint {
    fun mcpConfigStore(): McpConfigStore
    fun mcpNetworkPolicy(): McpNetworkPolicy
    fun mcpServer(): McpServer
}

private fun entryPoint(context: Context): BridgeEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, BridgeEntryPoint::class.java)

@Composable
fun rememberMcpConfigStore(): McpConfigStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).mcpConfigStore() }
}

@Composable
fun rememberMcpNetworkPolicy(): McpNetworkPolicy {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).mcpNetworkPolicy() }
}

@Composable
fun rememberMcpServer(): McpServer {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).mcpServer() }
}
