package dev.mterm.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.Alarm
import dev.mterm.AgentKind
import dev.mterm.settings.MTermSettings
import dev.mterm.sound.SoundPlayer
import dev.mterm.terminal.BellAwareTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions

object MTermSessionLauncher {

    private const val COMPLETION_DELAY_MS = 400
    private const val BRAILLE_START = 0x2800
    private const val BRAILLE_END = 0x28FF
    private const val MAX_LABEL = 40

    private val LEADING_SYMBOLS = Regex("^[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")

    fun launch(
        project: Project,
        parent: Disposable,
        agent: AgentKind,
        workingDirectory: String?,
        onTitleChange: (String) -> Unit = {},
    ): TerminalWidget {
        val settings = MTermSettings.getInstance()
        val soundAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parent)
        var wasSpinning = false
        var lastShown: String? = null

        fun scheduleCompletionSound() {
            val mutedForShell = agent == AgentKind.SHELL && !settings.soundForShell
            if (!settings.soundEnabled || mutedForShell) return
            soundAlarm.cancelAllRequests()
            soundAlarm.addRequest({ SoundPlayer.play(settings.sound) }, COMPLETION_DELAY_MS)
        }

        val onActivity = { hadBell: Boolean ->
            if (hadBell) scheduleCompletionSound()
        }

        val onTitle = { raw: String ->
            val spinning = raw.any { it.code in BRAILLE_START..BRAILLE_END }
            if (spinning) {
                soundAlarm.cancelAllRequests()
            } else if (wasSpinning) {
                scheduleCompletionSound()
            }
            wasSpinning = spinning

            if (settings.reflectAgentTitle) {
                val clean = cleanTitle(raw)
                if (clean.isNotBlank() && clean != lastShown) {
                    lastShown = clean
                    ApplicationManager.getApplication().invokeLater { onTitleChange(clean) }
                }
            }
        }

        val runner = BellAwareTerminalRunner(project, onActivity, onTitle)

        val options = ShellStartupOptions.Builder()
            .workingDirectory(workingDirectory)
            .build()
        val widget = runner.startShellTerminalWidget(parent, options, true)

        agent.command?.let { widget.sendCommandToExecute(it) }
        return widget
    }

    private fun cleanTitle(raw: String): String {
        val stripped = WHITESPACE.replace(LEADING_SYMBOLS.replace(raw, ""), " ").trim()
        return if (stripped.length <= MAX_LABEL) stripped else stripped.take(MAX_LABEL - 1).trimEnd() + "…"
    }
}
