package com.opendroid.ai.core.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpNetworkPolicyTest {

    @Test
    fun `the bridge listens on loopback unless it was exposed on purpose`() {
        // The server can run shell commands. Anything other than loopback here
        // has to be the result of someone deciding it, never of a default.
        assertEquals(MCP_LOOPBACK, mcpBindAddress(networkExposed = false))
        assertEquals(MCP_ALL_INTERFACES, mcpBindAddress(networkExposed = true))
    }

    @Test
    fun `the endpoint url is the one an MCP client can be pointed at`() {
        assertEquals("http://127.0.0.1:8765/mcp", mcpEndpointUrl("127.0.0.1"))
        assertEquals("http://192.168.1.20:8765/mcp", mcpEndpointUrl("192.168.1.20"))
    }

    @Test
    fun `a masked token shows enough to identify it and not enough to use it`() {
        val token = "0123456789abcdef0123456789abcdef"
        val masked = maskToken(token)
        assertEquals("0123…cdef", masked)
        assertFalse(masked.contains("456789abcdef01"))
        // Short enough to compare against a config file, far too short to be the
        // secret itself.
        assertTrue(masked.length < token.length / 2)
    }

    @Test
    fun `a short token is masked completely rather than mostly revealed`() {
        // take(4) + takeLast(4) on an eight-character token would print all of it.
        assertEquals("••••••••", maskToken("abcdefgh"))
        assertEquals("", maskToken(""))
    }

    @Test
    fun `listing addresses never fails, whatever the device reports`() {
        // Called on every recomposition of the bridge screen; a phone in flight
        // mode with no interfaces at all must return a list, not throw.
        localIpv4Addresses().forEach { address ->
            assertTrue(address, address.isNotBlank())
            assertFalse("loopback should not be offered as a LAN address", address.startsWith("127."))
        }
    }
}
