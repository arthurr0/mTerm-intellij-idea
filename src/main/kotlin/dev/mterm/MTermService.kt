package dev.mterm

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import dev.mterm.grid.MTermGridFile
import dev.mterm.grid.MTermGridPanel
import dev.mterm.session.MTermSessionFile
import dev.mterm.session.MTermSessionView

@Service(Service.Level.PROJECT)
class MTermService(private val project: Project) : Disposable {

    private var gridPanel: MTermGridPanel? = null
    private var gridDisposable: Disposable? = null

    private val sessionViews = HashMap<MTermSessionFile, MTermSessionView>()
    private val sessionDisposables = HashMap<MTermSessionFile, Disposable>()

    private val releaseAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    fun gridPanel(): MTermGridPanel {
        gridPanel?.let { return it }
        val disposable = Disposer.newDisposable(this, "mterm-grid")
        val panel = MTermGridPanel(project, disposable)
        gridPanel = panel
        gridDisposable = disposable
        return panel
    }

    fun sessionView(file: MTermSessionFile): MTermSessionView {
        sessionViews[file]?.let { return it }
        val disposable = Disposer.newDisposable(this, "mterm-session")
        val view = MTermSessionView(project, file, disposable)
        sessionViews[file] = view
        sessionDisposables[file] = disposable
        return view
    }

    fun scheduleRelease(file: VirtualFile, stillOpen: () -> Boolean) {
        releaseAlarm.addRequest({
            if (stillOpen()) return@addRequest
            when (file) {
                is MTermGridFile -> releaseConsole()
                is MTermSessionFile -> releaseSession(file)
            }
        }, RELEASE_DELAY_MS)
    }

    private fun releaseConsole() {
        thisLogger().info("mTerm: releasing grid console sessions")
        gridDisposable?.let { Disposer.dispose(it) }
        gridDisposable = null
        gridPanel = null
    }

    private fun releaseSession(file: MTermSessionFile) {
        thisLogger().info("mTerm: releasing terminal session ${file.agent.displayName}")
        sessionDisposables.remove(file)?.let { Disposer.dispose(it) }
        sessionViews.remove(file)
    }

    override fun dispose() {}

    companion object {
        private const val RELEASE_DELAY_MS = 1500

        fun getInstance(project: Project): MTermService = project.service()
    }
}
