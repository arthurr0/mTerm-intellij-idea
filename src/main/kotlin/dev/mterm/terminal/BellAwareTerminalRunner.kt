package dev.mterm.terminal

import com.intellij.openapi.project.Project
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions

class BellAwareTerminalRunner(
    project: Project,
    private val onActivity: (hadBell: Boolean) -> Unit,
    private val onTitle: (String) -> Unit,
) : LocalTerminalDirectRunner(project) {

    override fun createTtyConnector(options: ShellStartupOptions): TtyConnector =
        BellTtyConnector(super.createTtyConnector(options), onActivity, onTitle)
}
