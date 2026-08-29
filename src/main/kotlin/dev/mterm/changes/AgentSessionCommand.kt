package dev.mterm.changes

internal object AgentSessionCommand {

    fun acceptsSessionId(command: String?): Boolean {
        val trimmed = command?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        val tokens = trimmed.split(WHITESPACE)
        if (tokens.first().substringAfterLast('/') != CLAUDE_BINARY) return false
        return tokens.none { it.substringBefore('=') in CONFLICTING_FLAGS }
    }

    fun decorate(command: String?, sessionId: String?): String? {
        if (command.isNullOrBlank() || sessionId == null) return command
        return "$command --session-id $sessionId"
    }

    private const val CLAUDE_BINARY = "claude"

    private val CONFLICTING_FLAGS = setOf("--session-id", "--resume", "-r", "--continue", "-c", "--fork-session")
    private val WHITESPACE = Regex("\\s+")
}
