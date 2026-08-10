package dev.mterm.notify

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import dev.mterm.settings.MTermSettings

object MTermNotifier {

    private const val GROUP_ID = "mTerm"

    fun agentFinished(project: Project, agentName: String, detail: String?, onShow: () -> Unit) {
        val settings = MTermSettings.getInstance()
        if (!settings.notifyEnabled) return
        if (settings.notifyOnlyWhenIdeUnfocused && isIdeFocused(project)) return

        val group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
        group.createNotification("$agentName finished", detail.orEmpty(), NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring("Show") { onShow() })
            .notify(project)
    }

    private fun isIdeFocused(project: Project): Boolean =
        WindowManager.getInstance().getFrame(project)?.isActive == true
}
