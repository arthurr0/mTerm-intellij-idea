package dev.mterm.changes

import dev.mterm.git.GitChangeKind
import dev.mterm.git.GitFileChange
import java.nio.file.Path

internal object TurnAnalysis {

    data class Result(val changes: List<AgentChange>, val attribution: TurnAttribution)

    fun analyse(
        repoRoot: Path,
        diff: List<GitFileChange>,
        ownPaths: Set<String>,
        otherPaths: Set<String>,
        overlapping: Boolean,
    ): Result {
        val changes = diff.mapNotNull { change ->
            val absolute = repoRoot.resolve(change.path).toString()
            val confirmed = absolute in ownPaths
            if (!confirmed && absolute in otherPaths) return@mapNotNull null
            AgentChange(
                path = change.path,
                kind = change.kind.toChangeKind(),
                originalPath = change.originalPath,
                confirmed = confirmed,
            )
        }
        val attribution = when {
            changes.isNotEmpty() && ownPaths.isNotEmpty() && changes.all { it.confirmed } -> TurnAttribution.AGENT_LOG
            overlapping -> TurnAttribution.SHARED
            else -> TurnAttribution.SNAPSHOT
        }
        return Result(changes, attribution)
    }

    private fun GitChangeKind.toChangeKind(): ChangeKind = when (this) {
        GitChangeKind.ADDED -> ChangeKind.ADDED
        GitChangeKind.DELETED -> ChangeKind.DELETED
        GitChangeKind.RENAMED -> ChangeKind.RENAMED
        GitChangeKind.MODIFIED -> ChangeKind.MODIFIED
    }
}
