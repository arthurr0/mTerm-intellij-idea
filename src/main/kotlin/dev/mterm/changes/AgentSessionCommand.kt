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

    fun isContextReset(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("/")) return false
        val name = trimmed.removePrefix("/").substringBefore(' ')
        if (name.length < MIN_RESET_LENGTH) return false
        return RESET_COMMANDS.any { it.startsWith(name) }
    }

    private const val CLAUDE_BINARY = "claude"
    private const val MIN_RESET_LENGTH = 3

    private val RESET_COMMANDS = listOf("clear", "new")
    private val CONFLICTING_FLAGS = setOf("--session-id", "--resume", "-r", "--continue", "-c", "--fork-session")
    private val WHITESPACE = Regex("\\s+")
}
