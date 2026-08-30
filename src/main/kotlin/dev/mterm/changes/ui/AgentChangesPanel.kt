package dev.mterm.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.text.DateFormatUtil
import dev.mterm.changes.AgentChangeTracker
import dev.mterm.changes.AgentSessionRecord
import dev.mterm.changes.AgentTurn
import dev.mterm.changes.ChangeKind
import dev.mterm.changes.TurnAttribution
import dev.mterm.git.GitCli
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class AgentChangesPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : SimpleToolWindowPanel(true, true) {

    private class SessionNode(val record: AgentSessionRecord)

    private class TurnNode(val record: AgentSessionRecord, val turn: AgentTurn)

    private val root = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)
    private val browser = SimpleChangesBrowser(project, emptyList())

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = NodeRenderer()
        tree.emptyText.text = "No uncommitted agent changes"
        tree.addTreeSelectionListener { showSelection() }

        browser.hideViewerBorder()

        val splitter = OnePixelSplitter(true, SPLIT_PROPORTION).apply {
            firstComponent = ScrollPaneFactory.createScrollPane(tree, true)
            secondComponent = browser
        }
        setContent(splitter)
        toolbar = buildToolbar()

        project.messageBus.connect(parentDisposable).subscribe(
            AgentChangeTracker.TOPIC,
            object : AgentChangeTracker.Listener {
                override fun changesUpdated() = refresh()
            },
        )
        refresh()
    }

    fun selectSession(sessionId: String) {
        val node = root.children().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>()
            .firstOrNull { (it.userObject as? SessionNode)?.record?.id == sessionId }
            ?: return
        val target = node.lastLeaf.takeIf { it !== node } ?: node
        val path = TreePath(target.path)
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
    }

    private fun refresh() {
        val previous = selectedKey()
        root.removeAllChildren()
        for (record in AgentChangeTracker.getInstance(project).sessions()) {
            val sessionNode = DefaultMutableTreeNode(SessionNode(record))
            for (turn in record.turns.reversed()) {
                sessionNode.add(DefaultMutableTreeNode(TurnNode(record, turn)))
            }
            root.add(sessionNode)
        }
        treeModel.reload()
        for (index in 0 until tree.rowCount) tree.expandRow(index)
        restoreSelection(previous)
        showSelection()
    }

    private fun selectedKey(): String? = when (val payload = selectedPayload()) {
        is TurnNode -> payload.turn.id
        is SessionNode -> payload.record.id
        else -> null
    }

    private fun restoreSelection(key: String?) {
        if (key == null) {
            tree.selectionPath = firstTurnPath()
            return
        }
        val match = allNodes().firstOrNull { node ->
            when (val payload = node.userObject) {
                is TurnNode -> payload.turn.id == key
                is SessionNode -> payload.record.id == key
                else -> false
            }
        }
        tree.selectionPath = match?.let { TreePath(it.path) } ?: firstTurnPath()
    }

    private fun firstTurnPath(): TreePath? =
        allNodes().firstOrNull { it.userObject is TurnNode }?.let { TreePath(it.path) }

    private fun allNodes(): Sequence<DefaultMutableTreeNode> =
        root.depthFirstEnumeration().asSequence().filterIsInstance<DefaultMutableTreeNode>()

    private fun selectedPayload(): Any? =
        (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject

    private fun showSelection() {
        val changes = when (val payload = selectedPayload()) {
            is TurnNode -> AgentChanges.forTurn(payload.record, payload.turn)
            is SessionNode -> AgentChanges.forSession(payload.record)
            else -> emptyList()
        }
        browser.setChangesToDisplay(changes)
    }

    private fun buildToolbar(): javax.swing.JComponent {
        val group = DefaultActionGroup(
            action("Refresh", AllIcons.Actions.Refresh) { AgentChangeTracker.getInstance(project).refreshNow() },
            action("Revert Turn", AllIcons.Actions.Rollback) { revert() },
            action("Copy Patch", AllIcons.Actions.Copy) { copyPatch() },
            action("Clear History", AllIcons.Actions.GC) { clearHistory() },
        )
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    private fun action(text: String, icon: javax.swing.Icon, handler: () -> Unit): AnAction =
        object : AnAction(text, text, icon) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(e: AnActionEvent) = handler()
        }

    private fun copyPatch() {
        val payload = selectedPayload()
        val (record, from, to) = when (payload) {
            is TurnNode -> Triple(payload.record, payload.turn.baseTree, payload.turn.resultTree)
            is SessionNode -> {
                val turns = payload.record.turns
                val first = turns.firstOrNull() ?: return
                Triple(payload.record, first.baseTree, turns.last().resultTree)
            }

            else -> return
        }
        val paths = selectedPaths(record)
        ApplicationManager.getApplication().executeOnPooledThread {
            val patch = GitCli.patch(Path.of(record.repoRoot), from, to, paths)
            ApplicationManager.getApplication().invokeLater {
                if (patch.isNullOrBlank()) {
                    Messages.showWarningDialog(project, "This snapshot is no longer available in the repository.", TITLE)
                } else {
                    CopyPasteManager.getInstance().setContents(java.awt.datatransfer.StringSelection(patch))
                }
            }
        }
    }

    private fun revert() {
        val payload = selectedPayload() as? TurnNode ?: run {
            Messages.showInfoMessage(project, "Select a single turn to revert.", TITLE)
            return
        }
        val record = payload.record
        val turn = payload.turn
        val selected = selectedPaths(record).toSet()
        val targets = turn.changes.filter { selected.isEmpty() || it.path in selected }
        if (targets.isEmpty()) return

        val answer = Messages.showYesNoDialog(
            project,
            "Restore ${targets.size} file(s) to the state before turn ${turn.ordinal} of ${record.agentName}?",
            TITLE,
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return

        val repo = Path.of(record.repoRoot)
        val added = targets.filter { it.kind == ChangeKind.ADDED }.map { it.path }
        val restored = targets.filter { it.kind != ChangeKind.ADDED }
            .flatMap { listOfNotNull(it.originalPath ?: it.path) }

        ApplicationManager.getApplication().executeOnPooledThread {
            val ok = GitCli.restore(repo, turn.baseTree, restored)
            for (path in added) runCatching { Files.deleteIfExists(repo.resolve(path)) }
            ApplicationManager.getApplication().invokeLater {
                LocalFileSystem.getInstance().findFileByNioFile(repo)?.let {
                    VfsUtil.markDirtyAndRefresh(true, true, true, it)
                }
                if (!ok && restored.isNotEmpty()) {
                    Messages.showWarningDialog(project, "Some files could not be restored from the snapshot.", TITLE)
                }
            }
        }
    }

    private fun clearHistory() {
        val answer = Messages.showYesNoDialog(
            project,
            "Forget the recorded agent turns for this project? Files on disk are not touched.",
            TITLE,
            Messages.getQuestionIcon(),
        )
        if (answer == Messages.YES) AgentChangeTracker.getInstance(project).clearHistory()
    }

    private fun selectedPaths(record: AgentSessionRecord): List<String> {
        val repo = Path.of(record.repoRoot)
        return browser.selectedChanges.mapNotNull { change -> relativePath(repo, change) }
    }

    private fun relativePath(repo: Path, change: Change): String? {
        val file = change.afterRevision?.file ?: change.beforeRevision?.file ?: return null
        val path = runCatching { Path.of(file.path) }.getOrNull() ?: return null
        return runCatching { repo.relativize(path).toString() }.getOrNull()
    }

    private inner class NodeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val payload = (value as? DefaultMutableTreeNode)?.userObject) {
                is SessionNode -> renderSession(payload.record)
                is TurnNode -> renderTurn(payload.turn)
            }
        }

        private fun renderSession(record: AgentSessionRecord) {
            val accent = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color(record.colorRgb))
            append("${record.glyph} ", accent)
            append(record.agentName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            val files = record.changedPaths.size
            val details = buildList {
                add(if (record.turns.size == 1) "1 turn" else "${record.turns.size} turns")
                add(if (files == 1) "1 file" else "$files files")
                if (record.live) add("running")
            }
            append("  ${details.joinToString(" · ")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        private fun renderTurn(turn: AgentTurn) {
            append("Turn ${turn.ordinal}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            turn.title?.let { append("  $it", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES) }
            val files = turn.changes.size
            val details = buildList {
                add(if (files == 1) "1 file" else "$files files")
                add(DateFormatUtil.formatTime(turn.finishedAt))
                if (turn.attribution == TurnAttribution.SHARED) add("overlapping agents")
            }
            append("  ${details.joinToString(" · ")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    private companion object {
        const val TOOLBAR_PLACE = "mTermAgentChanges"
        const val TITLE = "Agent Changes"
        const val SPLIT_PROPORTION = 0.38f
    }
}
