package dev.mterm.changes

enum class ChangeKind { ADDED, MODIFIED, DELETED, RENAMED }

enum class TurnAttribution { AGENT_LOG, SNAPSHOT, SHARED }

data class AgentChange(
    val path: String,
    val kind: ChangeKind,
    val originalPath: String? = null,
    val confirmed: Boolean = false,
)

data class AgentTurn(
    val id: String,
    val ordinal: Int,
    val title: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val baseTree: String,
    val resultTree: String,
    val changes: List<AgentChange>,
    val attribution: TurnAttribution,
)

data class AgentSessionRecord(
    val id: String,
    val agentId: String,
    val agentName: String,
    val glyph: String,
    val colorRgb: Int,
    val repoRoot: String,
    val startedAt: Long,
    val live: Boolean,
    val turns: List<AgentTurn>,
) {
    val changedPaths: List<String>
        get() = turns.flatMap { turn -> turn.changes.map { it.path } }.distinct()
}
