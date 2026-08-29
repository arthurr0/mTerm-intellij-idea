package dev.mterm.changes

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "MTermChanges", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AgentChangeStore : PersistentStateComponent<AgentChangeStore.State> {

    class State {
        var sessions: MutableList<SessionEntry> = mutableListOf()
    }

    class SessionEntry {
        var id: String = ""
        var agentId: String = ""
        var agentName: String = ""
        var glyph: String = ""
        var colorRgb: Int = 0
        var repoRoot: String = ""
        var startedAt: Long = 0
        var turns: MutableList<TurnEntry> = mutableListOf()
    }

    class TurnEntry {
        var id: String = ""
        var ordinal: Int = 0
        var title: String? = null
        var startedAt: Long = 0
        var finishedAt: Long = 0
        var baseTree: String = ""
        var resultTree: String = ""
        var attribution: String = TurnAttribution.SNAPSHOT.name
        var changes: MutableList<ChangeEntry> = mutableListOf()
    }

    class ChangeEntry {
        var path: String = ""
        var kind: String = ChangeKind.MODIFIED.name
        var originalPath: String? = null
        var confirmed: Boolean = false
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    fun load(): List<AgentSessionRecord> = state.sessions.map { it.toRecord() }

    fun save(records: List<AgentSessionRecord>) {
        state.sessions = records.map { it.toEntry() }.toMutableList()
    }

    companion object {
        fun getInstance(project: Project): AgentChangeStore = project.service()
    }
}

private fun AgentChangeStore.SessionEntry.toRecord(): AgentSessionRecord = AgentSessionRecord(
    id = id,
    agentId = agentId,
    agentName = agentName,
    glyph = glyph,
    colorRgb = colorRgb,
    repoRoot = repoRoot,
    startedAt = startedAt,
    live = false,
    turns = turns.map { it.toTurn() },
)

private fun AgentChangeStore.TurnEntry.toTurn(): AgentTurn = AgentTurn(
    id = id,
    ordinal = ordinal,
    title = title,
    startedAt = startedAt,
    finishedAt = finishedAt,
    baseTree = baseTree,
    resultTree = resultTree,
    changes = changes.map { it.toChange() },
    attribution = runCatching { TurnAttribution.valueOf(attribution) }.getOrDefault(TurnAttribution.SNAPSHOT),
)

private fun AgentChangeStore.ChangeEntry.toChange(): AgentChange = AgentChange(
    path = path,
    kind = runCatching { ChangeKind.valueOf(kind) }.getOrDefault(ChangeKind.MODIFIED),
    originalPath = originalPath,
    confirmed = confirmed,
)

private fun AgentSessionRecord.toEntry(): AgentChangeStore.SessionEntry = AgentChangeStore.SessionEntry().also { entry ->
    entry.id = id
    entry.agentId = agentId
    entry.agentName = agentName
    entry.glyph = glyph
    entry.colorRgb = colorRgb
    entry.repoRoot = repoRoot
    entry.startedAt = startedAt
    entry.turns = turns.map { it.toEntry() }.toMutableList()
}

private fun AgentTurn.toEntry(): AgentChangeStore.TurnEntry = AgentChangeStore.TurnEntry().also { entry ->
    entry.id = id
    entry.ordinal = ordinal
    entry.title = title
    entry.startedAt = startedAt
    entry.finishedAt = finishedAt
    entry.baseTree = baseTree
    entry.resultTree = resultTree
    entry.attribution = attribution.name
    entry.changes = changes.map { it.toEntry() }.toMutableList()
}

private fun AgentChange.toEntry(): AgentChangeStore.ChangeEntry = AgentChangeStore.ChangeEntry().also { entry ->
    entry.path = path
    entry.kind = kind.name
    entry.originalPath = originalPath
    entry.confirmed = confirmed
}
