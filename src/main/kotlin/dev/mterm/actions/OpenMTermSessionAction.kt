package dev.mterm.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import dev.mterm.AgentKind
import dev.mterm.session.MTermSessionFile

abstract class OpenMTermSessionAction(private val agent: AgentKind) : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = MTermSessionFile(agent, project.basePath)
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class OpenClaudeCodeTabAction : OpenMTermSessionAction(AgentKind.CLAUDE)

class OpenCodexTabAction : OpenMTermSessionAction(AgentKind.CODEX)

class OpenSystemTerminalTabAction : OpenMTermSessionAction(AgentKind.SHELL)
