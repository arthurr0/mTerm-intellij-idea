package dev.mterm.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.ui.UIUtil
import dev.mterm.agents.AgentActivity
import dev.mterm.notify.MTermNotifier
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class MTermSessionView(
    private val project: Project,
    private val file: MTermSessionFile,
    parent: Disposable,
) {
    private val widget: TerminalWidget = MTermSessionLauncher.launch(
        project = project,
        parent = parent,
        profile = file.profile,
        workingDirectory = file.workingDirectory,
        launchOptions = file.launchOptions,
        onActivityChange = ::onActivityChanged,
    )

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(widget.component, BorderLayout.CENTER)
    }

    fun preferredFocusComponent(): JComponent? = widget.preferredFocusableComponent

    private fun onActivityChanged(activity: AgentActivity) {
        if (activity != AgentActivity.ATTENTION) return
        if (UIUtil.isFocusAncestor(component)) return
        MTermNotifier.agentFinished(project, file.profile.displayName, project.name) {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
}
