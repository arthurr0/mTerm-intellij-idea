package dev.mterm.changes

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import dev.mterm.TestGit
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.git.GitCli
import dev.mterm.settings.MTermSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path

class AgentChangeTrackerTest {

    private lateinit var repo: Path
    private lateinit var tracker: AgentChangeTracker

    private val project: Project get() = projectRule.project

    @Before
    fun setUp() {
        repo = TestGit.initRepository()
        TestGit.write(repo, "src/Main.kt", "one\n")
        TestGit.commitAll(repo, "initial")
        AgentChangeStore.getInstance(project).loadState(AgentChangeStore.State())
        MTermSettings.getInstance().trackAgentChanges = true
        tracker = AgentChangeTracker(project)
    }

    @After
    fun tearDown() {
        tracker.dispose()
        MTermSettings.getInstance().trackAgentChanges = true
        TestGit.delete(repo)
    }

    @Test
    fun `a turn records what changed while the agent was busy`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        TestGit.write(repo, "src/New.kt", "fresh\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        val turn = tracker.sessions().single().turns.single()
        assertEquals(listOf("src/Main.kt", "src/New.kt"), turn.changes.map { it.path }.sorted())
        assertEquals(1, turn.ordinal)
        assertEquals(TurnAttribution.SNAPSHOT, turn.attribution)
        assertEquals("one\n", GitCli.blob(repo, turn.baseTree, "src/Main.kt")?.decodeToString())
        assertEquals("two\n", GitCli.blob(repo, turn.resultTree, "src/Main.kt")?.decodeToString())
    }

    @Test
    fun `edits made before the turn started are not attributed to the agent`() {
        val handle = openSession()
        TestGit.write(repo, "src/Main.kt", "edited by a human\n")

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Agent.kt", "agent work\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        assertEquals(listOf("src/Agent.kt"), tracker.sessions().single().turns.single().changes.map { it.path })
    }

    @Test
    fun `an idle turn without changes is not recorded`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        assertTrue(tracker.sessions().single().turns.isEmpty())
    }

    @Test
    fun `consecutive turns continue from the previous state`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "three\n")
        handle.onActivity(AgentActivity.IDLE)
        tracker.awaitIdle()

        val turns = tracker.sessions().single().turns
        assertEquals(listOf(1, 2), turns.map { it.ordinal })
        assertEquals("two\n", GitCli.blob(repo, turns[1].baseTree, "src/Main.kt")?.decodeToString())
        assertEquals("three\n", GitCli.blob(repo, turns[1].resultTree, "src/Main.kt")?.decodeToString())
    }

    @Test
    fun `the turn title comes from the first title the agent reported`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        handle.onTitle("* Refactoring the parser")
        handle.onTitle("* Running tests")
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        assertEquals("Refactoring the parser", tracker.sessions().single().turns.single().title)
    }

    @Test
    fun `snapshots of a recorded turn are pinned against garbage collection`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        val turn = tracker.sessions().single().turns.single()
        TestGit.run(repo, "gc", "--prune=now", "--quiet")

        assertEquals(2, GitCli.refs(repo, "refs/mterm/").size)
        assertTrue(GitCli.objectExists(repo, turn.baseTree))
        assertTrue(GitCli.objectExists(repo, turn.resultTree))
    }

    @Test
    fun `closing a session without turns forgets it`() {
        val handle = openSession()

        handle.close()
        tracker.awaitIdle()

        assertTrue(tracker.sessions().isEmpty())
    }

    @Test
    fun `a closed session keeps its turns and stops being live`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()
        handle.close()
        tracker.awaitIdle()

        val record = tracker.sessions().single()
        assertFalse(record.live)
        assertEquals(1, record.turns.size)
        assertEquals(listOf("src/Main.kt"), record.changedPaths)
    }

    @Test
    fun `an unfinished turn is closed when the session ends`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.close()
        tracker.awaitIdle()

        assertEquals(listOf("src/Main.kt"), tracker.sessions().single().turns.single().changes.map { it.path })
    }

    @Test
    fun `history is written to the store and read back`() {
        val handle = openSession()

        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()

        val stored = AgentChangeStore.getInstance(project).load()
        assertEquals(1, stored.size)
        assertEquals(listOf("src/Main.kt"), stored.single().turns.single().changes.map { it.path })

        val reopened = AgentChangeTracker(project)
        try {
            reopened.sessions()
            reopened.awaitIdle()
            assertEquals(listOf("src/Main.kt"), reopened.sessions().single().changedPaths)
        } finally {
            reopened.dispose()
        }
    }

    @Test
    fun `clearing the history drops finished sessions`() {
        val handle = openSession()
        handle.onActivity(AgentActivity.BUSY)
        tracker.awaitIdle()
        TestGit.write(repo, "src/Main.kt", "two\n")
        handle.onActivity(AgentActivity.ATTENTION)
        tracker.awaitIdle()
        handle.close()
        tracker.awaitIdle()

        tracker.clearHistory()
        tracker.awaitIdle()

        assertTrue(tracker.sessions().isEmpty())
        assertTrue(AgentChangeStore.getInstance(project).load().isEmpty())
    }

    @Test
    fun `no session is opened when tracking is switched off`() {
        MTermSettings.getInstance().trackAgentChanges = false

        assertNull(tracker.openSession(profile(), repo.toString()))
    }

    @Test
    fun `a directory outside a repository records nothing`() {
        val outside = Files.createTempDirectory("mterm-no-repo")
        try {
            val handle = tracker.openSession(profile(), outside.toString())
            assertNotNull(handle)

            handle!!.onActivity(AgentActivity.BUSY)
            handle.onActivity(AgentActivity.ATTENTION)
            tracker.awaitIdle()

            assertTrue(tracker.sessions().isEmpty())
        } finally {
            TestGit.delete(outside)
        }
    }

    @Test
    fun `a claude session gets a generated session id`() {
        val handle = tracker.openSession(profile(command = "claude"), repo.toString())!!

        assertTrue(handle.decorate("claude").orEmpty().startsWith("claude --session-id "))
    }

    private fun openSession(): AgentSessionHandle = tracker.openSession(profile(), repo.toString())!!

    private fun profile(command: String? = "agent-under-test") = AgentProfile(
        id = "TEST",
        displayName = "Test Agent",
        command = command,
        glyph = "*",
        color = Color(0x40, 0x80, 0xC0),
    )

    companion object {
        @JvmField
        @ClassRule
        val projectRule = ProjectRule()
    }
}
