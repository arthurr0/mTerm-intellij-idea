package dev.mterm.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionCommandTest {

    @Test
    fun `plain claude command accepts an injected session id`() {
        assertTrue(AgentSessionCommand.acceptsSessionId("claude"))
        assertTrue(AgentSessionCommand.acceptsSessionId("  claude  "))
        assertTrue(AgentSessionCommand.acceptsSessionId("claude --model opus"))
        assertTrue(AgentSessionCommand.acceptsSessionId("/usr/local/bin/claude --effort high"))
    }

    @Test
    fun `commands that already pick a session are left alone`() {
        assertFalse(AgentSessionCommand.acceptsSessionId("claude --resume"))
        assertFalse(AgentSessionCommand.acceptsSessionId("claude -c"))
        assertFalse(AgentSessionCommand.acceptsSessionId("claude --continue"))
        assertFalse(AgentSessionCommand.acceptsSessionId("claude --fork-session"))
        assertFalse(AgentSessionCommand.acceptsSessionId("claude --session-id 1234"))
        assertFalse(AgentSessionCommand.acceptsSessionId("claude --session-id=1234"))
    }

    @Test
    fun `other agents and shells are never decorated`() {
        assertFalse(AgentSessionCommand.acceptsSessionId("codex"))
        assertFalse(AgentSessionCommand.acceptsSessionId("grok"))
        assertFalse(AgentSessionCommand.acceptsSessionId(null))
        assertFalse(AgentSessionCommand.acceptsSessionId("   "))
        assertFalse(AgentSessionCommand.acceptsSessionId("claudette"))
    }

    @Test
    fun `decorate appends the flag only when both parts are present`() {
        assertEquals("claude --session-id abc", AgentSessionCommand.decorate("claude", "abc"))
        assertEquals("claude", AgentSessionCommand.decorate("claude", null))
        assertNull(AgentSessionCommand.decorate(null, "abc"))
    }
}
