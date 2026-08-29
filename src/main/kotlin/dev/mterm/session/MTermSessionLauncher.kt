package dev.mterm.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.terminal.AgentActivityMonitor
import dev.mterm.terminal.BellAwareTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions

object MTermSessionLauncher {

    fun launch(
        project: Project,
        parent: Disposable,
        profile: AgentProfile,
        workingDirectory: String?,
        onTitleChange: (String) -> Unit = {},
        onActivityChange: (AgentActivity) -> Unit = {},
    ): TerminalWidget {
        val monitor = AgentActivityMonitor(parent, profile, onActivityChange, onTitleChange)
        val runner = BellAwareTerminalRunner(project, monitor::onBell, monitor::onTitle)

        val options = ShellStartupOptions.Builder()
            .workingDirectory(workingDirectory ?: profile.workingDirectory ?: project.basePath)
            .build()
        val widget = runner.startShellTerminalWidget(parent, options, true)

        profile.command?.let { widget.sendCommandToExecute(it) }
        return widget
    }
}
