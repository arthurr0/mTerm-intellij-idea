package dev.mterm.terminal

import com.intellij.openapi.project.Project
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner

class BellAwareTerminalRunner(
    project: Project,
    private val onActivity: (hadBell: Boolean) -> Unit,
    private val onTitle: (String) -> Unit,
) : LocalTerminalDirectRunner(project) {

    override fun createTtyConnector(process: PtyProcess): TtyConnector =
        BellTtyConnector(super.createTtyConnector(process), onActivity, onTitle)
}
