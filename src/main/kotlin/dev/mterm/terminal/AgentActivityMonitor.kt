package dev.mterm.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.Alarm
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.settings.MTermSettings
import dev.mterm.sound.SoundPlayer

class AgentActivityMonitor(
    parent: Disposable,
    private val profile: AgentProfile,
    private val onActivityChange: (AgentActivity) -> Unit,
    private val onTitleChange: (String) -> Unit,
) {

    private val soundAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parent)
    private val quietAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parent)
    private val detector = SpinnerActivityDetector()

    private var lastActivity = AgentActivity.IDLE
    private var lastShown: String? = null

    @Synchronized
    fun onBell(hadBell: Boolean) {
        if (hadBell) finishTurn()
    }

    @Synchronized
    fun onTitle(raw: String) {
        when (detector.onTitle(raw)) {
            SpinnerActivityDetector.Signal.STARTED -> {
                soundAlarm.cancelAllRequests()
                publish(AgentActivity.BUSY)
            }

            SpinnerActivityDetector.Signal.FINISHED -> finishTurn()
            SpinnerActivityDetector.Signal.CANCELLED -> publish(AgentActivity.IDLE)
            SpinnerActivityDetector.Signal.NONE -> Unit
        }
        rearmQuietWatch()
        reflectTitle(raw)
    }

    @Synchronized
    private fun onQuietPeriod() {
        when (detector.onQuietPeriod()) {
            SpinnerActivityDetector.Signal.FINISHED -> finishTurn()
            SpinnerActivityDetector.Signal.CANCELLED -> publish(AgentActivity.IDLE)
            else -> Unit
        }
    }

    private fun rearmQuietWatch() {
        quietAlarm.cancelAllRequests()
        if (detector.watchesQuietPeriod) quietAlarm.addRequest(::onQuietPeriod, QUIET_DELAY_MS)
    }

    private fun finishTurn() {
        scheduleCompletionSound()
        publish(AgentActivity.ATTENTION)
    }

    private fun scheduleCompletionSound() {
        val settings = MTermSettings.getInstance()
        val mutedForShell = profile.isShell && !settings.soundForShell
        if (!settings.soundEnabled || mutedForShell) return
        soundAlarm.cancelAllRequests()
        soundAlarm.addRequest({ SoundPlayer.play(settings.sound) }, COMPLETION_DELAY_MS)
    }

    private fun publish(activity: AgentActivity) {
        if (activity == lastActivity) return
        lastActivity = activity
        ApplicationManager.getApplication().invokeLater { onActivityChange(activity) }
    }

    private fun reflectTitle(raw: String) {
        if (!MTermSettings.getInstance().reflectAgentTitle) return
        val clean = cleanTitle(raw)
        if (clean.isBlank() || clean == lastShown) return
        lastShown = clean
        ApplicationManager.getApplication().invokeLater { onTitleChange(clean) }
    }

    private fun cleanTitle(raw: String): String {
        val stripped = WHITESPACE.replace(LEADING_SYMBOLS.replace(raw, ""), " ").trim()
        return if (stripped.length <= MAX_LABEL) stripped else stripped.take(MAX_LABEL - 1).trimEnd() + "…"
    }

    private companion object {
        const val COMPLETION_DELAY_MS = 400
        const val QUIET_DELAY_MS = 1200
        const val MAX_LABEL = 40

        val LEADING_SYMBOLS = Regex("^[^\\p{L}\\p{N}]+")
        val WHITESPACE = Regex("\\s+")
    }
}
