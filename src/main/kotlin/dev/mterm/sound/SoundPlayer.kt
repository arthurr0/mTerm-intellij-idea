package dev.mterm.sound

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Toolkit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object SoundPlayer {

    private const val SAMPLE_RATE = 44100
    private const val MASTER = 0.6

    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("mTerm.Sound", 1)

    fun play(sound: AgentSound) {
        executor.execute {
            try {
                if (sound == AgentSound.SYSTEM) {
                    Toolkit.getDefaultToolkit().beep()
                } else {
                    playPcm(render(notesFor(sound)))
                }
            } catch (t: Throwable) {
                thisLogger().warn("Failed to play mTerm sound", t)
                runCatching { Toolkit.getDefaultToolkit().beep() }
            }
        }
    }

    private enum class Wave { SINE, TRIANGLE }

    private class Note(
        val wave: Wave,
        val start: Double,
        val dur: Double,
        val stop: Double,
        val freqStart: Double,
        val freqEnd: Double,
        val glide: Double,
        val peak: Double,
        val attack: Double,
    )

    private fun notesFor(sound: AgentSound): List<Note> = when (sound) {
        AgentSound.CHIME -> listOf(
            Note(Wave.SINE, 0.0, 0.55, 0.55, 523.25, 523.25, 0.0, MASTER * 0.6, 0.015),
            Note(Wave.SINE, 0.18, 0.65, 0.65, 659.25, 659.25, 0.0, MASTER * 0.6, 0.015),
        )
        AgentSound.POP -> listOf(
            Note(Wave.SINE, 0.0, 0.18, 0.2, 180.0, 900.0, 0.06, MASTER * 0.6, 0.005),
        )
        AgentSound.DROP -> listOf(
            Note(Wave.TRIANGLE, 0.0, 0.45, 0.45, 1200.0, 220.0, 0.35, MASTER * 0.55, 0.01),
        )
        AgentSound.SUCCESS -> listOf(
            Note(Wave.TRIANGLE, 0.0, 0.18, 0.18, 523.25, 523.25, 0.0, MASTER * 0.5, 0.01),
            Note(Wave.TRIANGLE, 0.1, 0.18, 0.18, 659.25, 659.25, 0.0, MASTER * 0.5, 0.01),
            Note(Wave.TRIANGLE, 0.2, 0.35, 0.35, 783.99, 783.99, 0.0, MASTER * 0.5, 0.01),
        )
        AgentSound.SYSTEM -> emptyList()
    }

    private fun render(notes: List<Note>): ByteArray {
        val totalSec = notes.maxOf { it.start + it.stop } + 0.02
        val total = (SAMPLE_RATE * totalSec).toInt()
        val mix = DoubleArray(total)

        for (note in notes) {
            val startSample = (note.start * SAMPLE_RATE).toInt()
            val stopSample = ((note.start + note.stop) * SAMPLE_RATE).toInt().coerceAtMost(total)
            var phase = 0.0
            var i = startSample
            while (i < stopSample) {
                val t = (i - startSample).toDouble() / SAMPLE_RATE
                phase += 2.0 * PI * frequencyAt(note, t) / SAMPLE_RATE
                val wave = if (note.wave == Wave.SINE) sin(phase) else (2.0 / PI) * asin(sin(phase))
                mix[i] += wave * envelopeAt(note, t)
                i++
            }
        }

        val out = ByteArray(total * 2)
        var j = 0
        for (value in mix) {
            val sample = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt()
            out[j++] = (sample and 0xFF).toByte()
            out[j++] = ((sample shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun frequencyAt(note: Note, t: Double): Double {
        if (note.glide <= 0.0 || note.freqStart == note.freqEnd) return note.freqStart
        val progress = (t / note.glide).coerceIn(0.0, 1.0)
        return note.freqStart * (note.freqEnd / note.freqStart).pow(progress)
    }

    private fun envelopeAt(note: Note, t: Double): Double {
        if (t < note.attack) return note.peak * (t / note.attack)
        if (t >= note.dur) return 0.0
        val progress = (t - note.attack) / (note.dur - note.attack)
        val gain = note.peak * (0.0001 / note.peak).pow(progress)
        return if (gain < 0.0002) 0.0 else gain
    }

    private fun playPcm(pcm: ByteArray) {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        try {
            line.open(format)
            line.start()
            var offset = 0
            while (offset < pcm.size) {
                offset += line.write(pcm, offset, min(pcm.size - offset, line.bufferSize))
            }
            line.drain()
            line.stop()
        } finally {
            line.close()
        }
    }
}
