package dev.mterm

import java.awt.Color

enum class AgentKind(
    val displayName: String,
    val command: String?,
    val glyph: String,
    val glyphColor: Color,
) {
    CLAUDE("Claude Code", "claude", "✻", Color(0xD9, 0x77, 0x57)),
    CODEX("Codex", "codex", "✦", Color(0x19, 0xC3, 0x7D)),
    SHELL("System Terminal", null, "❯", Color(0xCF, 0xD3, 0xD8)),
}
