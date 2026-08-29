package dev.mterm.terminal

internal class SpinnerActivityDetector(private val clock: () -> Long = System::currentTimeMillis) {

    enum class Signal { NONE, STARTED, FINISHED, CANCELLED }

    private var busy = false
    private var glyphDriven = false
    private var streak = 0
    private var lastChangeAt = 0L
    private var busySince = 0L

    val watchesQuietPeriod: Boolean get() = busy && !glyphDriven

    fun onTitle(title: String): Signal {
        val now = clock()
        if (SpinnerGlyphs.present(title)) {
            glyphDriven = true
            streak = 0
            return start(now)
        }
        if (glyphDriven) {
            streak = 0
            return if (busy) finish() else Signal.NONE
        }
        streak = if (now - lastChangeAt <= RAPID_WINDOW_MS) streak + 1 else 1
        lastChangeAt = now
        return if (streak >= RAPID_CHANGES) start(now) else Signal.NONE
    }

    fun onQuietPeriod(): Signal {
        streak = 0
        if (glyphDriven || !busy) return Signal.NONE
        val lasted = clock() - busySince
        return if (lasted >= MIN_TEMPO_TURN_MS) finish() else cancel()
    }

    private fun start(now: Long): Signal {
        if (busy) return Signal.NONE
        busy = true
        busySince = now
        return Signal.STARTED
    }

    private fun finish(): Signal {
        busy = false
        return Signal.FINISHED
    }

    private fun cancel(): Signal {
        busy = false
        return Signal.CANCELLED
    }

    private companion object {
        const val RAPID_WINDOW_MS = 2_000L
        const val RAPID_CHANGES = 3
        const val MIN_TEMPO_TURN_MS = 2_500L
    }
}
