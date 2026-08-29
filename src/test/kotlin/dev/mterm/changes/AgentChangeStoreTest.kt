package dev.mterm.changes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChangeStoreTest {

    @Test
    fun `records survive a save and load round trip`() {
        val record = AgentSessionRecord(
            id = "session-1",
            agentId = "CLAUDE",
            agentName = "Claude Code",
            glyph = "*",
            colorRgb = 0xD97757,
            repoRoot = "/tmp/repo",
            startedAt = 1_700_000_000_000,
            live = true,
            turns = listOf(
                AgentTurn(
                    id = "turn-1",
                    ordinal = 1,
                    title = "Refactoring the parser",
                    startedAt = 1_700_000_001_000,
                    finishedAt = 1_700_000_002_000,
                    baseTree = "aaa",
                    resultTree = "bbb",
                    changes = listOf(
                        AgentChange("src/Main.kt", ChangeKind.MODIFIED, confirmed = true),
                        AgentChange("src/New.kt", ChangeKind.ADDED),
                        AgentChange("src/Gone.kt", ChangeKind.DELETED),
                        AgentChange("src/Renamed.kt", ChangeKind.RENAMED, originalPath = "src/Old.kt"),
                    ),
                    attribution = TurnAttribution.AGENT_LOG,
                ),
            ),
        )

        val store = AgentChangeStore()
        store.save(listOf(record))
        val loaded = store.load().single()

        assertEquals(record.copy(live = false), loaded)
    }

    @Test
    fun `a live session is restored as finished`() {
        val store = AgentChangeStore()
        store.save(listOf(record(live = true)))

        assertEquals(false, store.load().single().live)
    }

    @Test
    fun `saving replaces the previous history`() {
        val store = AgentChangeStore()
        store.save(listOf(record(id = "one")))
        store.save(listOf(record(id = "two")))

        assertEquals(listOf("two"), store.load().map { it.id })
    }

    @Test
    fun `unknown enum values fall back to safe defaults`() {
        val store = AgentChangeStore()
        val entry = AgentChangeStore.SessionEntry().apply {
            id = "broken"
            repoRoot = "/tmp/repo"
            turns = mutableListOf(
                AgentChangeStore.TurnEntry().apply {
                    id = "turn"
                    attribution = "SOMETHING_NEW"
                    changes = mutableListOf(
                        AgentChangeStore.ChangeEntry().apply {
                            path = "a.txt"
                            kind = "WHATEVER"
                        },
                    )
                },
            )
        }
        store.loadState(AgentChangeStore.State().apply { sessions = mutableListOf(entry) })

        val turn = store.load().single().turns.single()

        assertEquals(TurnAttribution.SNAPSHOT, turn.attribution)
        assertEquals(ChangeKind.MODIFIED, turn.changes.single().kind)
        assertTrue(store.load().single().repoRoot.isNotEmpty())
    }

    private fun record(id: String = "session", live: Boolean = false) = AgentSessionRecord(
        id = id,
        agentId = "CODEX",
        agentName = "Codex",
        glyph = "+",
        colorRgb = 0x19C37D,
        repoRoot = "/tmp/repo",
        startedAt = 1,
        live = live,
        turns = listOf(
            AgentTurn(
                id = "turn-$id",
                ordinal = 1,
                title = null,
                startedAt = 1,
                finishedAt = 2,
                baseTree = "aaa",
                resultTree = "bbb",
                changes = listOf(AgentChange("a.txt", ChangeKind.MODIFIED)),
                attribution = TurnAttribution.SNAPSHOT,
            ),
        ),
    )
}
