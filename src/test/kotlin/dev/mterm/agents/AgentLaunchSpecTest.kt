package dev.mterm.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentLaunchSpecTest {

    @Test
    fun `known binaries are recognised by their first token`() {
        assertEquals("claude", AgentLaunchSpec.forCommand("claude")?.binary)
        assertEquals("claude", AgentLaunchSpec.forCommand("  /usr/local/bin/claude --continue ")?.binary)
        assertEquals("codex", AgentLaunchSpec.forCommand("codex --full-auto")?.binary)
        assertEquals("grok", AgentLaunchSpec.forCommand("grok")?.binary)
    }

    @Test
    fun `shells and unknown commands have no launch spec`() {
        assertNull(AgentLaunchSpec.forCommand(null))
        assertNull(AgentLaunchSpec.forCommand("   "))
        assertNull(AgentLaunchSpec.forCommand("claudette"))
        assertNull(AgentLaunchSpec.forCommand("aider --model gpt"))
    }

    @Test
    fun `claude gets model and effort flags`() {
        val spec = AgentLaunchSpec.forCommand("claude")!!
        assertEquals("claude", spec.apply("claude", LaunchOptions.NONE))
        assertEquals("claude --model opus", spec.apply("claude", LaunchOptions(model = "opus")))
        assertEquals("claude --effort high", spec.apply("claude", LaunchOptions(effort = "high")))
        assertEquals(
            "claude --continue --model opus --effort xhigh",
            spec.apply("claude --continue", LaunchOptions(model = "opus", effort = "xhigh")),
        )
    }

    @Test
    fun `codex passes effort through the config override`() {
        val spec = AgentLaunchSpec.forCommand("codex")!!
        assertEquals(
            "codex --model gpt-5.5 -c model_reasoning_effort=high",
            spec.apply("codex", LaunchOptions(model = "gpt-5.5", effort = "high")),
        )
    }

    @Test
    fun `grok uses the reasoning effort flag`() {
        val spec = AgentLaunchSpec.forCommand("grok")!!
        assertEquals(
            "grok --model grok-4.5 --reasoning-effort low",
            spec.apply("grok", LaunchOptions(model = "grok-4.5", effort = "low")),
        )
    }

    @Test
    fun `values with shell metacharacters are single quoted`() {
        val spec = AgentLaunchSpec.forCommand("claude")!!
        assertEquals(
            "claude --model 'claude-fable-5-1[1m]'",
            spec.apply("claude", LaunchOptions(model = "claude-fable-5-1[1m]")),
        )
        assertEquals("claude --model 'a'\\''b'", spec.apply("claude", LaunchOptions(model = "a'b")))
    }

    @Test
    fun `launch options normalise blanks and build a label`() {
        assertEquals(LaunchOptions.NONE, LaunchOptions.of("  ", null))
        assertEquals(LaunchOptions(model = "opus"), LaunchOptions.of(" opus ", ""))
        assertNull(LaunchOptions.NONE.label())
        assertEquals("opus · high", LaunchOptions(model = "opus", effort = "high").label())
        assertEquals("high", LaunchOptions(effort = "high").label())
        assertNotNull(AgentLaunchSpec.forProfile(AgentProfile.builtIns().first { it.id == AgentProfile.CODEX_ID }))
        assertNull(AgentLaunchSpec.forProfile(AgentProfile.builtIns().first { it.id == AgentProfile.SHELL_ID }))
    }
}
