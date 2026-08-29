package dev.mterm.changes.logs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

internal class CodexLogReader(
    private val workingDirectory: Path,
    private val startedAt: Long,
) : AgentLogReader {

    private var tail: JsonlTail? = null
    private val entries = mutableListOf<Pair<Long, String>>()

    override fun touchedPaths(fromMs: Long, toMs: Long): Set<String> {
        val reader = tail ?: locate()?.let { JsonlTail(it).also { created -> tail = created } } ?: return emptySet()
        reader.readNew { collect(it) }
        return entries.filter { it.first in fromMs..toMs }.mapTo(mutableSetOf()) { it.second }
    }

    private fun collect(entry: JsonObject) {
        val payload = entry.obj("payload") ?: return
        if (payload.string("type") != PATCH_EVENT) return
        val timestamp = JsonlTail.epochMillis(entry.string("timestamp")) ?: return
        val changes = payload.obj("changes") ?: return
        for (path in changes.keySet()) entries += timestamp to path
    }

    private fun locate(): Path? {
        val root = Path.of(System.getProperty("user.home"), ".codex", "sessions")
        if (!Files.isDirectory(root)) return null
        val target = workingDirectory.toAbsolutePath().toString()
        val candidates = runCatching {
            Files.walk(root, WALK_DEPTH).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().startsWith("rollout-") }
                    .filter { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) >= startedAt - CLOCK_SLACK_MS }
                    .collect(Collectors.toList())
            }
        }.getOrNull().orEmpty()

        return candidates
            .sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
            .firstOrNull { matchesWorkingDirectory(it, target) }
    }

    private fun matchesWorkingDirectory(file: Path, target: String): Boolean = runCatching {
        Files.newBufferedReader(file).use { reader ->
            val line = reader.readLine() ?: return false
            val element = JsonParser.parseString(line)
            if (!element.isJsonObject) return false
            val entry = element.asJsonObject
            if (entry.string("type") != "session_meta") return false
            entry.obj("payload")?.string("cwd") == target
        }
    }.getOrDefault(false)

    private companion object {
        const val PATCH_EVENT = "patch_apply_end"
        const val WALK_DEPTH = 5
        const val CLOCK_SLACK_MS = 60_000L
    }
}
