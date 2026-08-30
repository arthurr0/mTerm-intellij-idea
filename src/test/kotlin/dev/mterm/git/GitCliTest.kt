package dev.mterm.git

import com.intellij.testFramework.ApplicationRule
import dev.mterm.TestGit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GitCliTest {

    @get:Rule
    val application = ApplicationRule()

    private lateinit var repo: Path

    @Before
    fun setUp() {
        repo = TestGit.initRepository()
        TestGit.write(repo, "kept.txt", "one\n")
        TestGit.write(repo, "removed.txt", "bye\n")
        TestGit.commitAll(repo, "initial")
    }

    @After
    fun tearDown() {
        TestGit.delete(repo)
    }

    @Test
    fun `repo root is found from a subdirectory`() {
        val nested = repo.resolve("src/main")
        Files.createDirectories(nested)
        assertEquals(repo.toRealPath(), GitCli.repoRoot(nested)?.toRealPath())
    }

    @Test
    fun `repo root is null outside a repository`() {
        val outside = Files.createTempDirectory("mterm-not-a-repo")
        try {
            assertNull(GitCli.repoRoot(outside))
        } finally {
            TestGit.delete(outside)
        }
    }

    @Test
    fun `snapshot leaves the user index untouched`() {
        val index = repo.resolve(".git/index")
        val before = Files.readAllBytes(index)
        TestGit.write(repo, "fresh.txt", "new file\n")

        assertNotNull(GitCli.snapshot(repo))

        assertArrayEqualsBytes(before, Files.readAllBytes(index))
        assertEquals("?? fresh.txt\n", TestGit.run(repo, "status", "--porcelain"))
    }

    @Test
    fun `diff reports added modified and deleted files`() {
        val base = GitCli.snapshot(repo)!!
        TestGit.write(repo, "kept.txt", "two\n")
        TestGit.write(repo, "added.txt", "hello\n")
        Files.delete(repo.resolve("removed.txt"))
        val result = GitCli.snapshot(repo)!!

        val changes = GitCli.diff(repo, base, result).associateBy { it.path }

        assertEquals(3, changes.size)
        assertEquals(GitChangeKind.MODIFIED, changes.getValue("kept.txt").kind)
        assertEquals(GitChangeKind.ADDED, changes.getValue("added.txt").kind)
        assertEquals(GitChangeKind.DELETED, changes.getValue("removed.txt").kind)
    }

    @Test
    fun `diff ignores files excluded by gitignore`() {
        TestGit.write(repo, ".gitignore", "ignored/\n")
        TestGit.commitAll(repo, "ignore rules")
        val base = GitCli.snapshot(repo)!!
        TestGit.write(repo, "ignored/secret.txt", "nope\n")
        val result = GitCli.snapshot(repo)!!

        assertEquals(base, result)
        assertTrue(GitCli.diff(repo, base, result).isEmpty())
    }

    @Test
    fun `diff reports paths with spaces`() {
        val base = GitCli.snapshot(repo)!!
        TestGit.write(repo, "some dir/a file.txt", "spaced\n")
        val result = GitCli.snapshot(repo)!!

        val changes = GitCli.diff(repo, base, result)

        assertEquals(listOf("some dir/a file.txt"), changes.map { it.path })
    }

    @Test
    fun `blob returns the content stored in the tree`() {
        val base = GitCli.snapshot(repo)!!
        TestGit.write(repo, "kept.txt", "two\n")

        assertEquals("one\n", GitCli.blob(repo, base, "kept.txt")?.decodeToString())
        assertNull(GitCli.blob(repo, base, "missing.txt"))
    }

    @Test
    fun `blob keeps binary content intact`() {
        val bytes = byteArrayOf(0, 1, 2, 3, 127, -1, -128, 65)
        Files.write(repo.resolve("binary.bin"), bytes)
        val tree = GitCli.snapshot(repo)!!

        assertArrayEqualsBytes(bytes, GitCli.blob(repo, tree, "binary.bin")!!)
    }

    @Test
    fun `patch describes the change`() {
        val base = GitCli.snapshot(repo)!!
        TestGit.write(repo, "kept.txt", "two\n")
        val result = GitCli.snapshot(repo)!!

        val patch = GitCli.patch(repo, base, result, listOf("kept.txt"))!!

        assertTrue(patch.contains("diff --git"))
        assertTrue(patch.contains("kept.txt"))
        assertTrue(patch.contains("+two"))
    }

    @Test
    fun `restore brings the file back to the snapshot state`() {
        val base = GitCli.snapshot(repo)!!
        TestGit.write(repo, "kept.txt", "two\n")

        assertTrue(GitCli.restore(repo, base, listOf("kept.txt")))

        assertEquals("one\n", TestGit.read(repo, "kept.txt"))
    }

    @Test
    fun `pinned trees survive aggressive garbage collection`() {
        TestGit.write(repo, "fresh.txt", "kept alive\n")
        val pinned = GitCli.snapshot(repo)!!
        TestGit.write(repo, "fresh.txt", "not pinned\n")
        val loose = GitCli.snapshot(repo)!!

        assertTrue(GitCli.pinTree(repo, "refs/mterm/1700000000000-abc", pinned))
        TestGit.run(repo, "gc", "--prune=now", "--quiet")

        assertTrue(GitCli.objectExists(repo, pinned))
        assertFalse(GitCli.objectExists(repo, loose))
    }

    @Test
    fun `refs can be listed and deleted`() {
        val tree = GitCli.snapshot(repo)!!
        GitCli.pinTree(repo, "refs/mterm/1700000000000-one", tree)
        GitCli.pinTree(repo, "refs/mterm/1700000000001-two", tree)

        assertEquals(2, GitCli.refs(repo, "refs/mterm/").size)

        GitCli.deleteRef(repo, "refs/mterm/1700000000000-one")

        assertEquals(listOf("refs/mterm/1700000000001-two"), GitCli.refs(repo, "refs/mterm/"))
    }

    @Test
    fun `pending paths list everything that is not in HEAD`() {
        TestGit.write(repo, "kept.txt", "changed\n")
        TestGit.write(repo, "untracked.txt", "brand new\n")
        TestGit.write(repo, "staged.txt", "staged\n")
        TestGit.run(repo, "add", "staged.txt")
        Files.delete(repo.resolve("removed.txt"))

        val pending = GitCli.pendingPaths(repo)!!

        assertEquals(setOf("kept.txt", "untracked.txt", "staged.txt", "removed.txt"), pending)
    }

    @Test
    fun `a clean repository has nothing pending`() {
        assertEquals(emptySet<String>(), GitCli.pendingPaths(repo))
    }

    @Test
    fun `committed work disappears from pending paths`() {
        TestGit.write(repo, "kept.txt", "changed\n")
        assertEquals(setOf("kept.txt"), GitCli.pendingPaths(repo))

        TestGit.commitAll(repo, "agent work")

        assertEquals(emptySet<String>(), GitCli.pendingPaths(repo))
    }

    @Test
    fun `ignored files are not pending`() {
        TestGit.write(repo, ".gitignore", "build/\n")
        TestGit.commitAll(repo, "ignore rules")
        TestGit.write(repo, "build/output.bin", "junk\n")

        assertEquals(emptySet<String>(), GitCli.pendingPaths(repo))
    }

    @Test
    fun `a renamed file reports both paths as pending`() {
        TestGit.run(repo, "mv", "kept.txt", "moved.txt")

        val pending = GitCli.pendingPaths(repo)!!

        assertTrue(pending.contains("moved.txt"))
        assertTrue(pending.contains("kept.txt"))
    }

    @Test
    fun `pending paths are unknown outside a repository`() {
        val outside = Files.createTempDirectory("mterm-not-a-repo")
        try {
            assertNull(GitCli.pendingPaths(outside))
        } finally {
            TestGit.delete(outside)
        }
    }

    @Test
    fun `snapshot works in a repository without commits`() {
        val fresh = TestGit.initRepository()
        try {
            TestGit.write(fresh, "first.txt", "hello\n")

            val tree = GitCli.snapshot(fresh)

            assertNotNull(tree)
            assertEquals("hello\n", GitCli.blob(fresh, tree!!, "first.txt")?.decodeToString())
        } finally {
            TestGit.delete(fresh)
        }
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
