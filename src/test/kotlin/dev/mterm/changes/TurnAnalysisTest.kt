package dev.mterm.changes

import dev.mterm.git.GitChangeKind
import dev.mterm.git.GitFileChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class TurnAnalysisTest {

    private val repo: Path = Path.of("/work/project")

    @Test
    fun `a lone agent without logs keeps every change`() {
        val result = analyse(diff = listOf(modified("a.kt"), added("b.kt")))

        assertEquals(listOf("a.kt", "b.kt"), result.changes.map { it.path })
        assertTrue(result.changes.none { it.confirmed })
        assertEquals(TurnAttribution.SNAPSHOT, result.attribution)
    }

    @Test
    fun `changes backed by the agent log are marked confirmed`() {
        val result = analyse(
            diff = listOf(modified("a.kt")),
            ownPaths = setOf("/work/project/a.kt"),
        )

        assertTrue(result.changes.single().confirmed)
        assertEquals(TurnAttribution.AGENT_LOG, result.attribution)
    }

    @Test
    fun `files another agent claims are dropped`() {
        val result = analyse(
            diff = listOf(modified("mine.kt"), modified("theirs.kt")),
            ownPaths = setOf("/work/project/mine.kt"),
            otherPaths = setOf("/work/project/theirs.kt"),
            overlapping = true,
        )

        assertEquals(listOf("mine.kt"), result.changes.map { it.path })
        assertEquals(TurnAttribution.AGENT_LOG, result.attribution)
    }

    @Test
    fun `a file both agents touched stays with the one holding a log entry`() {
        val result = analyse(
            diff = listOf(modified("shared.kt")),
            ownPaths = setOf("/work/project/shared.kt"),
            otherPaths = setOf("/work/project/shared.kt"),
            overlapping = true,
        )

        assertEquals(listOf("shared.kt"), result.changes.map { it.path })
        assertTrue(result.changes.single().confirmed)
    }

    @Test
    fun `edits made outside the log survive but mark the turn as shared`() {
        val result = analyse(
            diff = listOf(modified("logged.kt"), modified("via-bash.kt")),
            ownPaths = setOf("/work/project/logged.kt"),
            overlapping = true,
        )

        assertEquals(listOf("logged.kt", "via-bash.kt"), result.changes.map { it.path })
        assertTrue(result.changes.first().confirmed)
        assertFalse(result.changes.last().confirmed)
        assertEquals(TurnAttribution.SHARED, result.attribution)
    }

    @Test
    fun `overlapping turns without logs are reported as shared`() {
        val result = analyse(diff = listOf(modified("a.kt")), overlapping = true)

        assertEquals(TurnAttribution.SHARED, result.attribution)
    }

    @Test
    fun `git statuses are mapped onto the model`() {
        val result = analyse(
            diff = listOf(
                modified("m.kt"),
                added("a.kt"),
                GitFileChange("d.kt", GitChangeKind.DELETED),
                GitFileChange("new.kt", GitChangeKind.RENAMED, originalPath = "old.kt"),
            ),
        )

        assertEquals(
            listOf(ChangeKind.MODIFIED, ChangeKind.ADDED, ChangeKind.DELETED, ChangeKind.RENAMED),
            result.changes.map { it.kind },
        )
        assertEquals("old.kt", result.changes.last().originalPath)
    }

    @Test
    fun `nested paths are matched against absolute log entries`() {
        val result = analyse(
            diff = listOf(modified("src/main/kotlin/App.kt")),
            ownPaths = setOf("/work/project/src/main/kotlin/App.kt"),
        )

        assertTrue(result.changes.single().confirmed)
    }

    @Test
    fun `a turn where every file belongs to another agent ends up empty`() {
        val result = analyse(
            diff = listOf(modified("theirs.kt")),
            otherPaths = setOf("/work/project/theirs.kt"),
            overlapping = true,
        )

        assertTrue(result.changes.isEmpty())
        assertEquals(TurnAttribution.SHARED, result.attribution)
    }

    private fun analyse(
        diff: List<GitFileChange>,
        ownPaths: Set<String> = emptySet(),
        otherPaths: Set<String> = emptySet(),
        overlapping: Boolean = false,
    ) = TurnAnalysis.analyse(repo, diff, ownPaths, otherPaths, overlapping)

    private fun modified(path: String) = GitFileChange(path, GitChangeKind.MODIFIED)

    private fun added(path: String) = GitFileChange(path, GitChangeKind.ADDED)
}
