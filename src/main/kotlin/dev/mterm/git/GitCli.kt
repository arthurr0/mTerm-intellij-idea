package dev.mterm.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum class GitChangeKind { ADDED, MODIFIED, DELETED, RENAMED }

data class GitFileChange(
    val path: String,
    val kind: GitChangeKind,
    val originalPath: String? = null,
)

object GitCli {

    private data class Result(val exitCode: Int, val output: String, val error: String) {
        val ok: Boolean get() = exitCode == 0
    }

    fun repoRoot(directory: Path): Path? {
        val result = text(directory, listOf("rev-parse", "--show-toplevel"))
        if (!result.ok) return null
        val root = result.output.trim()
        return root.takeIf { it.isNotEmpty() }?.let { runCatching { Path.of(it) }.getOrNull() }
    }

    fun snapshot(repo: Path): String? {
        val indexDirectory = runCatching { Files.createTempDirectory("mterm-index") }.getOrNull() ?: return null
        val index = indexDirectory.resolve("index")
        val environment = mapOf("GIT_INDEX_FILE" to index.toString())
        try {
            if (!text(repo, listOf("read-tree", "HEAD"), environment).ok &&
                !text(repo, listOf("read-tree", "--empty"), environment).ok
            ) {
                return null
            }
            val staged = text(repo, listOf("add", "-A", "--", "."), environment, SNAPSHOT_TIMEOUT_MS)
            if (!staged.ok) {
                thisLogger().warn("mTerm: git add failed: ${staged.error.take(MAX_LOGGED_ERROR)}")
                return null
            }
            val tree = text(repo, listOf("write-tree"), environment, SNAPSHOT_TIMEOUT_MS)
            return tree.output.trim().takeIf { tree.ok && it.isNotEmpty() }
        } finally {
            runCatching {
                Files.deleteIfExists(index)
                Files.deleteIfExists(indexDirectory)
            }
        }
    }

    fun diff(repo: Path, from: String, to: String): List<GitFileChange> {
        val result = text(repo, listOf("diff", "--name-status", "-z", from, to))
        if (!result.ok) return emptyList()
        val tokens = result.output.split(NUL).filter { it.isNotEmpty() }
        val changes = mutableListOf<GitFileChange>()
        var index = 0
        while (index < tokens.size) {
            val letter = tokens[index].firstOrNull() ?: break
            if (letter == 'R' || letter == 'C') {
                val original = tokens.getOrNull(index + 1) ?: break
                val path = tokens.getOrNull(index + 2) ?: break
                changes += GitFileChange(path, GitChangeKind.RENAMED, original)
                index += 3
            } else {
                val path = tokens.getOrNull(index + 1) ?: break
                val kind = when (letter) {
                    'A' -> GitChangeKind.ADDED
                    'D' -> GitChangeKind.DELETED
                    else -> GitChangeKind.MODIFIED
                }
                changes += GitFileChange(path, kind)
                index += 2
            }
        }
        return changes
    }

    fun blob(repo: Path, tree: String, path: String): ByteArray? {
        val result = binary(repo, listOf("show", "$tree:$path"))
        return if (result.ok) result.output.toByteArray(StandardCharsets.ISO_8859_1) else null
    }

    fun patch(repo: Path, from: String, to: String, paths: List<String>): String? {
        val arguments = mutableListOf("diff", from, to)
        if (paths.isNotEmpty()) {
            arguments += "--"
            arguments += paths
        }
        val result = text(repo, arguments, timeoutMs = SNAPSHOT_TIMEOUT_MS)
        return result.output.takeIf { result.ok }
    }

    fun restore(repo: Path, tree: String, paths: List<String>): Boolean {
        if (paths.isEmpty()) return true
        val arguments = mutableListOf("restore", "--source=$tree", "--worktree", "--")
        arguments += paths
        val result = text(repo, arguments)
        if (!result.ok) thisLogger().warn("mTerm: git restore failed: ${result.error.take(MAX_LOGGED_ERROR)}")
        return result.ok
    }

    fun objectExists(repo: Path, sha: String): Boolean =
        text(repo, listOf("cat-file", "-e", "$sha^{tree}")).ok

    fun pinTree(repo: Path, ref: String, tree: String): Boolean =
        text(repo, listOf("update-ref", ref, tree)).ok

    fun refs(repo: Path, prefix: String): List<String> {
        val result = text(repo, listOf("for-each-ref", "--format=%(refname)", prefix))
        if (!result.ok) return emptyList()
        return result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun deleteRef(repo: Path, ref: String) {
        text(repo, listOf("update-ref", "-d", ref))
    }

    private fun text(
        workDirectory: Path?,
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): Result = execute(workDirectory, arguments, environment, timeoutMs, StandardCharsets.UTF_8)

    private fun binary(
        workDirectory: Path?,
        arguments: List<String>,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): Result = execute(workDirectory, arguments, emptyMap(), timeoutMs, StandardCharsets.ISO_8859_1)

    private fun execute(
        workDirectory: Path?,
        arguments: List<String>,
        environment: Map<String, String>,
        timeoutMs: Int,
        charset: Charset,
    ): Result {
        val commandLine = GeneralCommandLine(listOf(EXECUTABLE) + arguments).withCharset(charset)
        workDirectory?.let { commandLine.withWorkDirectory(it.toFile()) }
        if (environment.isNotEmpty()) commandLine.withEnvironment(environment)
        return try {
            val output = CapturingProcessHandler(commandLine).runProcess(timeoutMs, true)
            if (output.isTimeout) {
                thisLogger().warn("mTerm: git ${arguments.firstOrNull()} timed out")
                Result(-1, "", "timeout")
            } else {
                Result(output.exitCode, output.stdout, output.stderr)
            }
        } catch (e: Exception) {
            thisLogger().warn("mTerm: git ${arguments.firstOrNull()} failed", e)
            Result(-1, "", e.message.orEmpty())
        }
    }

    private const val NUL = '\u0000'
    private const val EXECUTABLE = "git"
    private const val DEFAULT_TIMEOUT_MS = 15_000
    private const val SNAPSHOT_TIMEOUT_MS = 60_000
    private const val MAX_LOGGED_ERROR = 300
}
