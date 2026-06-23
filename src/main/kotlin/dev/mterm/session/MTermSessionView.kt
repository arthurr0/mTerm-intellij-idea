package dev.mterm.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class MTermSessionView(
    project: Project,
    file: MTermSessionFile,
    parent: Disposable,
) {
    private val widget: TerminalWidget =
        MTermSessionLauncher.launch(project, parent, file.agent, file.workingDirectory)

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(widget.component, BorderLayout.CENTER)
    }

    fun preferredFocusComponent(): JComponent? = widget.preferredFocusableComponent
}
