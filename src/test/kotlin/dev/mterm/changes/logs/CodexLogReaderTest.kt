package dev.mterm.changes.logs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class CodexLogReaderTest {

    private lateinit var home: Path
    private var originalHome: String? = null

    @Before
    fun setUp() {
        home = Files.createTempDirectory("mterm-home")
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())
    }

    @After
    fun tearDown() {
        originalHome?.let { System.setProperty("user.home", it) }
        delete(home)
    }

    @Test
    fun `patched files of the matching session are reported`() {
        rollout("rollout-a.jsonl", "/work/project", listOf(2_000L to "/work/project/a.kt"))

        val reader = CodexLogReader(Path.of("/work/project"), 1_000)

        assertEquals(setOf("/work/project/a.kt"), reader.touchedPaths(0, 5_000))
    }

    @Test
    fun `a session started in another directory is ignored`() {
        rollout("rollout-other.jsonl", "/somewhere/else", listOf(2_000L to "/somewhere/else/x.kt"))

        val reader = CodexLogReader(Path.of("/work/project"), 1_000)

        assertEquals(emptySet<String>(), reader.touchedPaths(0, 5_000))
    }

    @Test
    fun `the newest matching rollout wins`() {
        val older = rollout("rollout-old.jsonl", "/work/project", listOf(2_000L to "/work/project/old.kt"))
        val newer = rollout("rollout-new.jsonl", "/work/project", listOf(3_000L to "/work/project/new.kt"))
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(10_000))
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(20_000))

        val reader = CodexLogReader(Path.of("/work/project"), 1_000)

        assertEquals(setOf("/work/project/new.kt"), reader.touchedPaths(0, 5_000))
    }

    @Test
    fun `changes outside the window are filtered out`() {
        rollout(
            "rollout-window.jsonl",
            "/work/project",
            listOf(2_000L to "/work/project/early.kt", 9_000L to "/work/project/late.kt"),
        )

        val reader = CodexLogReader(Path.of("/work/project"), 1_000)

        assertEquals(setOf("/work/project/late.kt"), reader.touchedPaths(8_000, 10_000))
    }

    @Test
    fun `no codex directory is tolerated`() {
        val reader = CodexLogReader(Path.of("/work/project"), 1_000)

        assertEquals(emptySet<String>(), reader.touchedPaths(0, 5_000))
    }

    private fun rollout(name: String, cwd: String, patches: List<Pair<Long, String>>): Path {
        val directory = home.resolve(".codex/sessions/2026/08/29")
        Files.createDirectories(directory)
        val file = directory.resolve(name)
        val meta = """{"timestamp":"${Instant.ofEpochMilli(1_000)}","type":"session_meta",""" +
            """"payload":{"session_id":"s","cwd":"$cwd","originator":"codex-tui"}}""" + "\n"
        val events = patches.joinToString("") { (timestamp, path) ->
            """{"timestamp":"${Instant.ofEpochMilli(timestamp)}","type":"event_msg",""" +
                """"payload":{"type":"patch_apply_end","success":true,"changes":{"$path":{"update":{}}}}}""" + "\n"
        }
        Files.writeString(file, meta + events)
        return file
    }

    private fun delete(directory: Path) {
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
        }
    }
}
