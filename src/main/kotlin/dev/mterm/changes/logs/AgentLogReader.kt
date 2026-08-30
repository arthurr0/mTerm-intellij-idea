package dev.mterm.changes.logs

import dev.mterm.agents.AgentProfile
import java.nio.file.Path

interface AgentLogReader {

    fun touchedPaths(fromMs: Long, toMs: Long): Set<String>

    fun contextClearedSince(sinceMs: Long): Boolean = false

    companion object {

        fun create(profile: AgentProfile, agentSessionId: String?, workingDirectory: Path): AgentLogReader? = when {
            looksLike(profile, "claude") && agentSessionId != null ->
                ClaudeLogReader.locate(agentSessionId, workingDirectory)?.let { ClaudeLogReader(it) }

            looksLike(profile, "codex") -> CodexLogReader(workingDirectory, System.currentTimeMillis())

            else -> null
        }

        private fun looksLike(profile: AgentProfile, executable: String): Boolean {
            val command = profile.command?.trim().orEmpty()
            if (command.isEmpty()) return false
            val binary = command.split(' ').first().substringAfterLast('/')
            return binary == executable
        }
    }
}
