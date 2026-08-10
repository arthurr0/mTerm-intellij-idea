package dev.mterm.agents

import java.awt.Color

data class AgentProfile(
    val id: String,
    val displayName: String,
    val command: String?,
    val glyph: String,
    val color: Color,
    val workingDirectory: String? = null,
    val builtIn: Boolean = false,
    val enabled: Boolean = true,
) {
    val isShell: Boolean get() = command.isNullOrBlank()

    companion object {
        const val CLAUDE_ID = "CLAUDE"
        const val CODEX_ID = "CODEX"
        const val GROK_BUILD_ID = "GROK_BUILD"
        const val SHELL_ID = "SHELL"

        fun builtIns(): List<AgentProfile> = listOf(
            AgentProfile(CLAUDE_ID, "Claude Code", "claude", "✻", Color(0xD9, 0x77, 0x57), builtIn = true),
            AgentProfile(CODEX_ID, "Codex", "codex", "✦", Color(0x19, 0xC3, 0x7D), builtIn = true),
            AgentProfile(GROK_BUILD_ID, "Grok Build", "grok", "◆", Color(0x00, 0xB4, 0xD8), builtIn = true),
            AgentProfile(SHELL_ID, "System Terminal", null, "❯", Color(0xCF, 0xD3, 0xD8), builtIn = true),
        )
    }
}
