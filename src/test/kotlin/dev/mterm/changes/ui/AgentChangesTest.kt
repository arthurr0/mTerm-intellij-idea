package dev.mterm.changes.ui

import com.intellij.openapi.vcs.FileStatus
import com.intellij.testFramework.ApplicationRule
import dev.mterm.TestGit
import dev.mterm.changes.AgentChange
import dev.mterm.changes.AgentSessionRecord
import dev.mterm.changes.AgentTurn
import dev.mterm.changes.ChangeKind
import dev.mterm.changes.TurnAttribution
import dev.mterm.git.GitCli
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class AgentChangesTest {

    @get:Rule
    val application = ApplicationRule()

    private lateinit var repo: Path
    private lateinit var firstTree: String
    private lateinit var secondTree: String
    private lateinit var thirdTree: String

    @Before
    fun setUp() {
        repo = TestGit.initRepository()
        TestGit.write(repo, "src/Main.kt", "version one\n")
        TestGit.commitAll(repo, "initial")
        firstTree = GitCli.snapshot(repo)!!
        TestGit.write(repo, "src/Main.kt", "version two\n")
        secondTree = GitCli.snapshot(repo)!!
        TestGit.write(repo, "src/Main.kt", "version three\n")
        TestGit.write(repo, "src/Added.kt", "brand new\n")
        thirdTree = GitCli.snapshot(repo)!!
    }

    @After
    fun tearDown() {
        TestGit.delete(repo)
    }

    @Test
    fun `a turn compares the trees recorded for that turn`() {
        val change = AgentChanges.forTurn(record(), secondTurn())
            .first { it.afterRevision?.file?.name == "Main.kt" }

        assertEquals(FileStatus.MODIFIED, change.fileStatus)
        assertEquals("version two\n", change.beforeRevision?.content)
        assertEquals("version three\n", change.afterRevision?.content)
    }

    @Test
    fun `a session spans the first base and the last result`() {
        val changes = AgentChanges.forSession(record()).associateBy { it.afterRevision?.file?.name }

        assertEquals(2, changes.size)
        assertEquals("version one\n", changes.getValue("Main.kt").beforeRevision?.content)
        assertEquals("version three\n", changes.getValue("Main.kt").afterRevision?.content)
        assertNull(changes.getValue("Added.kt").beforeRevision)
        assertEquals("brand new\n", changes.getValue("Added.kt").afterRevision?.content)
    }

    @Test
    fun `deleted files keep the previous content and drop the new one`() {
        Files.delete(repo.resolve("src/Main.kt"))
        val afterDelete = GitCli.snapshot(repo)!!
        val turn = turn(
            ordinal = 1,
            base = thirdTree,
            result = afterDelete,
            changes = listOf(AgentChange("src/Main.kt", ChangeKind.DELETED)),
        )

        val change = AgentChanges.forTurn(record(listOf(turn)), turn).single()

        assertEquals(FileStatus.DELETED, change.fileStatus)
        assertEquals("version three\n", change.beforeRevision?.content)
        assertNull(change.afterRevision)
    }

    @Test
    fun `a renamed file reads its previous content from the old path`() {
        TestGit.write(repo, "src/Renamed.kt", "version three\n")
        Files.delete(repo.resolve("src/Main.kt"))
        val afterRename = GitCli.snapshot(repo)!!
        val turn = turn(
            ordinal = 1,
            base = thirdTree,
            result = afterRename,
            changes = listOf(AgentChange("src/Renamed.kt", ChangeKind.RENAMED, originalPath = "src/Main.kt")),
        )

        val change = AgentChanges.forTurn(record(listOf(turn)), turn).single()

        assertEquals("version three\n", change.beforeRevision?.content)
        assertEquals("src/Main.kt", change.beforeRevision?.file?.let { repo.relativize(Path.of(it.path)).toString() })
    }

    @Test
    fun `content of a pruned snapshot is reported as missing`() {
        val turn = turn(
            ordinal = 1,
            base = "0000000000000000000000000000000000000000",
            result = thirdTree,
            changes = listOf(AgentChange("src/Main.kt", ChangeKind.MODIFIED)),
        )

        val change = AgentChanges.forTurn(record(listOf(turn)), turn).single()

        assertNull(change.beforeRevision?.content)
        assertEquals("version three\n", change.afterRevision?.content)
    }

    private fun record(turns: List<AgentTurn> = listOf(firstTurn(), secondTurn())) = AgentSessionRecord(
        id = "session",
        agentId = "CLAUDE",
        agentName = "Claude Code",
        glyph = "*",
        colorRgb = 0xD97757,
        repoRoot = repo.toString(),
        startedAt = 1,
        live = false,
        turns = turns,
    )

    private fun firstTurn() = turn(
        ordinal = 1,
        base = firstTree,
        result = secondTree,
        changes = listOf(AgentChange("src/Main.kt", ChangeKind.MODIFIED, confirmed = true)),
    )

    private fun secondTurn() = turn(
        ordinal = 2,
        base = secondTree,
        result = thirdTree,
        changes = listOf(
            AgentChange("src/Main.kt", ChangeKind.MODIFIED, confirmed = true),
            AgentChange("src/Added.kt", ChangeKind.ADDED, confirmed = true),
        ),
    )

    private fun turn(ordinal: Int, base: String, result: String, changes: List<AgentChange>) = AgentTurn(
        id = "turn-$ordinal",
        ordinal = ordinal,
        title = null,
        startedAt = ordinal.toLong(),
        finishedAt = ordinal + 1L,
        baseTree = base,
        resultTree = result,
        changes = changes,
        attribution = TurnAttribution.AGENT_LOG,
    )
}
