package dev.mterm.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.changes.AgentChangeTracker
import dev.mterm.changes.AgentSessionHandle
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
        onSessionOpened: (AgentSessionHandle) -> Unit = {},
    ): TerminalWidget {
        val directory = workingDirectory ?: profile.workingDirectory ?: project.basePath
        val handle = AgentChangeTracker.getInstance(project).openSession(profile, directory)
        handle?.let { session ->
            Disposer.register(parent, Disposable { session.close() })
            onSessionOpened(session)
        }

        val monitor = AgentActivityMonitor(
            parent,
            profile,
            { activity ->
                handle?.onActivity(activity)
                onActivityChange(activity)
            },
            onTitleChange,
        )
        val runner = BellAwareTerminalRunner(project, monitor::onBell) { raw ->
            handle?.onTitle(raw)
            monitor.onTitle(raw)
        }

        val options = ShellStartupOptions.Builder()
            .workingDirectory(directory)
            .build()
        val widget = runner.startShellTerminalWidget(parent, options, true)

        val command = handle?.decorate(profile.command) ?: profile.command
        command?.let { widget.sendCommandToExecute(it) }
        return widget
    }
}
