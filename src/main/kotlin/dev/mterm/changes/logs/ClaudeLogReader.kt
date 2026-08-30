package dev.mterm.changes.logs

import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

internal class ClaudeLogReader(file: Path) : AgentLogReader {

    private val tail = JsonlTail(file)
    private val entries = mutableListOf<Pair<Long, String>>()
    private val clears = mutableListOf<Long>()

    override fun touchedPaths(fromMs: Long, toMs: Long): Set<String> {
        tail.readNew { collect(it) }
        return entries.filter { it.first in fromMs..toMs }.mapTo(mutableSetOf()) { it.second }
    }

    override fun contextClearedSince(sinceMs: Long): Boolean {
        tail.readNew { collect(it) }
        return clears.any { it > sinceMs }
    }

    private fun collect(entry: JsonObject) {
        val timestamp = JsonlTail.epochMillis(entry.string("timestamp")) ?: return
        val message = entry.obj("message") ?: return
        message.string("content")?.let { text ->
            if (RESET_COMMANDS.any { text.contains(it) }) clears += timestamp
            return
        }
        val content = message.array("content") ?: return
        for (element in content) {
            val block = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            when (block.string("type")) {
                "text" -> block.string("text")?.let { text ->
                    if (RESET_COMMANDS.any { text.contains(it) }) clears += timestamp
                }

                "tool_use" -> {
                    if (block.string("name") !in EDIT_TOOLS) continue
                    val path = block.obj("input")?.string("file_path") ?: continue
                    entries += timestamp to path
                }
            }
        }
    }

    companion object {

        private val EDIT_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")
        private val RESET_COMMANDS = listOf("<command-name>/clear<", "<command-name>/new<")

        fun locate(sessionId: String, workingDirectory: Path): Path? {
            val root = Path.of(System.getProperty("user.home"), ".claude", "projects")
            if (!Files.isDirectory(root)) return null
            val slug = workingDirectory.toAbsolutePath().toString().replace(SEPARATORS, "-")
            val direct = root.resolve(slug).resolve("$sessionId.jsonl")
            if (Files.isRegularFile(direct)) return direct
            return runCatching {
                Files.list(root).use { stream ->
                    stream.map { it.resolve("$sessionId.jsonl") }
                        .filter { Files.isRegularFile(it) }
                        .findFirst()
                        .orElse(null)
                }
            }.getOrNull()
        }

        private val SEPARATORS = Regex("[^A-Za-z0-9]")
    }
}
