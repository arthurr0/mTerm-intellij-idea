package dev.mterm.changes.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class AgentChangesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AgentChangesPanel(project, toolWindow.disposable)
        AgentChangesUi.getInstance(project).attach(panel)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}

@Service(Service.Level.PROJECT)
class AgentChangesUi(private val project: Project) {

    private var panel: AgentChangesPanel? = null

    fun attach(panel: AgentChangesPanel) {
        this.panel = panel
    }

    fun show(sessionId: String?) {
        val window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        window.activate({ sessionId?.let { panel?.selectSession(it) } }, true)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Agent Changes"

        fun getInstance(project: Project): AgentChangesUi = project.service()
    }
}
