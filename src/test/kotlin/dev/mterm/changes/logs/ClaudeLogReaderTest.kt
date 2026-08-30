package dev.mterm.changes.logs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class ClaudeLogReaderTest {

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
    fun `edits inside the window are reported`() {
        val file = Files.createTempFile("claude", ".jsonl")
        try {
            Files.writeString(
                file,
                entry(1_000, "Write", "/work/a.kt") +
                    entry(2_000, "Bash", null) +
                    entry(3_000, "Read", "/work/ignored.kt") +
                    entry(4_000, "Edit", "/work/b.kt"),
            )

            val reader = ClaudeLogReader(file)

            assertEquals(setOf("/work/a.kt", "/work/b.kt"), reader.touchedPaths(0, 5_000))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `edits outside the window are filtered out`() {
        val file = Files.createTempFile("claude", ".jsonl")
        try {
            Files.writeString(file, entry(1_000, "Write", "/work/a.kt") + entry(9_000, "Edit", "/work/b.kt"))

            val reader = ClaudeLogReader(file)

            assertEquals(setOf("/work/b.kt"), reader.touchedPaths(8_000, 10_000))
            assertEquals(emptySet<String>(), reader.touchedPaths(3_000, 4_000))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `entries appended later are picked up`() {
        val file = Files.createTempFile("claude", ".jsonl")
        try {
            Files.writeString(file, entry(1_000, "Write", "/work/a.kt"))
            val reader = ClaudeLogReader(file)
            assertEquals(setOf("/work/a.kt"), reader.touchedPaths(0, 5_000))

            Files.writeString(file, entry(2_000, "Edit", "/work/c.kt"), StandardOpenOption.APPEND)

            assertEquals(setOf("/work/a.kt", "/work/c.kt"), reader.touchedPaths(0, 5_000))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a clear command is reported as a context reset`() {
        val file = Files.createTempFile("claude", ".jsonl")
        try {
            Files.writeString(file, entry(1_000, "Write", "/work/a.kt") + command(5_000, "/clear"))
            val reader = ClaudeLogReader(file)

            assertTrue(reader.contextClearedSince(0))
            assertFalse(reader.contextClearedSince(6_000))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a clear inside structured content is also recognised`() {
        val file = Files.createTempFile("claude", ".jsonl")
        try {
            Files.writeString(file, structuredCommand(5_000, "/clear"))

            assertTrue(ClaudeLogReader(file).contextClearedSince(0))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `ordinary prompts are not treated as a reset`() {
        val file = Files.createTempFile("claude", ".jsonl")
        try {
            Files.writeString(file, structuredCommand(5_000, "please clear the cache"))

            assertFalse(ClaudeLogReader(file).contextClearedSince(0))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `session files are located by project slug`() {
        val slug = "-work-project"
        val projects = home.resolve(".claude/projects/$slug")
        Files.createDirectories(projects)
        val session = projects.resolve("abc-123.jsonl")
        Files.writeString(session, entry(1_000, "Write", "/work/project/a.kt"))

        assertEquals(session, ClaudeLogReader.locate("abc-123", Path.of("/work/project")))
    }

    @Test
    fun `session files are found even when the slug does not match`() {
        val projects = home.resolve(".claude/projects/-some-other-name")
        Files.createDirectories(projects)
        val session = projects.resolve("abc-123.jsonl")
        Files.writeString(session, entry(1_000, "Write", "/work/project/a.kt"))

        assertEquals(session, ClaudeLogReader.locate("abc-123", Path.of("/work/project")))
    }

    @Test
    fun `a missing session is reported as absent`() {
        Files.createDirectories(home.resolve(".claude/projects/-work-project"))

        assertNull(ClaudeLogReader.locate("nope", Path.of("/work/project")))
    }

    @Test
    fun `no claude directory at all is tolerated`() {
        assertNull(ClaudeLogReader.locate("abc", Path.of("/work/project")))
        assertTrue(Files.notExists(home.resolve(".claude")))
    }

    private fun entry(timestamp: Long, tool: String, path: String?): String {
        val input = path?.let { "{\"file_path\":\"$it\"}" } ?: "{\"command\":\"ls\"}"
        val instant = Instant.ofEpochMilli(timestamp)
        return """{"type":"assistant","sessionId":"s","timestamp":"$instant","cwd":"/work",""" +
            """"message":{"role":"assistant","content":[{"type":"tool_use","name":"$tool","input":$input}]}}""" +
            "\n"
    }

    private fun command(timestamp: Long, name: String): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val content = "<command-name>$name</command-name><command-message>clear</command-message>"
        return """{"type":"user","sessionId":"s","timestamp":"$instant","cwd":"/work",""" +
            """"message":{"role":"user","content":"$content"}}""" + "\n"
    }

    private fun structuredCommand(timestamp: Long, text: String): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val body = if (text.startsWith("/")) "<command-name>$text</command-name>" else text
        return """{"type":"user","sessionId":"s","timestamp":"$instant","cwd":"/work",""" +
            """"message":{"role":"user","content":[{"type":"text","text":"$body"}]}}""" + "\n"
    }

    private fun delete(directory: Path) {
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
        }
    }
}
