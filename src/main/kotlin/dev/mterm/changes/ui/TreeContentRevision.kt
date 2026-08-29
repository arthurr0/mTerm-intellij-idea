package dev.mterm.changes.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import dev.mterm.changes.AgentSessionRecord
import dev.mterm.changes.AgentTurn
import dev.mterm.changes.ChangeKind
import dev.mterm.git.GitCli
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal class TreeContentRevision(
    private val repo: Path,
    private val tree: String,
    private val relativePath: String,
    private val filePath: FilePath,
) : ByteBackedContentRevision {

    override fun getContentAsBytes(): ByteArray? = GitCli.blob(repo, tree, relativePath)

    override fun getContent(): String? = contentAsBytes?.toString(StandardCharsets.UTF_8)

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(tree.take(SHORT_SHA))

    private companion object {
        const val SHORT_SHA = 8
    }
}

internal object AgentChanges {

    fun forTurn(record: AgentSessionRecord, turn: AgentTurn): List<Change> {
        val repo = Path.of(record.repoRoot)
        return turn.changes.map { change ->
            build(repo, change.path, change.originalPath, change.kind, turn.baseTree, turn.resultTree)
        }
    }

    fun forSession(record: AgentSessionRecord): List<Change> {
        val repo = Path.of(record.repoRoot)
        val first = LinkedHashMap<String, AgentTurn>()
        val last = LinkedHashMap<String, AgentTurn>()
        val kinds = LinkedHashMap<String, ChangeKind>()
        val originals = LinkedHashMap<String, String?>()
        for (turn in record.turns) {
            for (change in turn.changes) {
                first.putIfAbsent(change.path, turn)
                last[change.path] = turn
                kinds[change.path] = change.kind
                originals[change.path] = change.originalPath
            }
        }
        return first.keys.map { path ->
            build(
                repo = repo,
                path = path,
                originalPath = originals[path],
                kind = kinds.getValue(path),
                baseTree = first.getValue(path).baseTree,
                resultTree = last.getValue(path).resultTree,
            )
        }
    }

    private fun build(
        repo: Path,
        path: String,
        originalPath: String?,
        kind: ChangeKind,
        baseTree: String,
        resultTree: String,
    ): Change {
        val filePath: FilePath = LocalFilePath(repo.resolve(path).toString(), false)
        val beforePath = originalPath ?: path
        val before = if (kind == ChangeKind.ADDED) {
            null
        } else {
            TreeContentRevision(repo, baseTree, beforePath, LocalFilePath(repo.resolve(beforePath).toString(), false))
        }
        val after = if (kind == ChangeKind.DELETED) null else TreeContentRevision(repo, resultTree, path, filePath)
        val status = when (kind) {
            ChangeKind.ADDED -> FileStatus.ADDED
            ChangeKind.DELETED -> FileStatus.DELETED
            else -> FileStatus.MODIFIED
        }
        return Change(before, after, status)
    }
}
