package dev.mterm.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import dev.mterm.agents.AgentProfile
import dev.mterm.agents.AgentRegistry
import dev.mterm.session.MTermSessionFile

class OpenAgentTabAction(private val profile: AgentProfile) :
    AnAction("New ${profile.displayName} Tab", "Open a ${profile.displayName} session as an editor tab", null),
    DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = MTermSessionFile(profile, profile.workingDirectory ?: project.basePath)
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class MTermAgentActionGroup : ActionGroup(), DumbAware {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
        AgentRegistry.getInstance().enabledProfiles()
            .map { OpenAgentTabAction(it) }
            .toTypedArray()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
