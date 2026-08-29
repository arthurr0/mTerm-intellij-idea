package dev.mterm.changes.logs

import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

internal class ClaudeLogReader(file: Path) : AgentLogReader {

    private val tail = JsonlTail(file)
    private val entries = mutableListOf<Pair<Long, String>>()

    override fun touchedPaths(fromMs: Long, toMs: Long): Set<String> {
        tail.readNew { collect(it) }
        return entries.filter { it.first in fromMs..toMs }.mapTo(mutableSetOf()) { it.second }
    }

    private fun collect(entry: JsonObject) {
        val timestamp = JsonlTail.epochMillis(entry.string("timestamp")) ?: return
        val content = entry.obj("message")?.array("content") ?: return
        for (element in content) {
            val block = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (block.string("type") != "tool_use") continue
            if (block.string("name") !in EDIT_TOOLS) continue
            val path = block.obj("input")?.string("file_path") ?: continue
            entries += timestamp to path
        }
    }

    companion object {

        private val EDIT_TOOLS = setOf("Edit", "Write", "MultiEdit", "NotebookEdit")

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
