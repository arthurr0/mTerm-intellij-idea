package dev.mterm.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import dev.mterm.agents.AgentActivity
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import javax.swing.JComponent
import javax.swing.Timer
import kotlin.math.PI
import kotlin.math.sin

class ActivityDot(private val agentColor: Color) : JComponent() {

    private var phase = 0.0

    private val timer = Timer(PULSE_INTERVAL_MS) {
        phase += PULSE_STEP
        if (phase > 2 * PI) phase -= 2 * PI
        repaint()
    }

    var activity: AgentActivity = AgentActivity.IDLE
        set(value) {
            if (field == value) return
            field = value
            phase = 0.0
            syncTimer()
            repaint()
        }

    init {
        isOpaque = false
        val size = JBUI.scale(DOT_BOX)
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        toolTipText = tooltipFor(activity)
    }

    private fun syncTimer() {
        toolTipText = tooltipFor(activity)
        if (activity == AgentActivity.BUSY && isDisplayable) timer.start() else timer.stop()
    }

    override fun addNotify() {
        super.addNotify()
        syncTimer()
    }

    override fun removeNotify() {
        timer.stop()
        super.removeNotify()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val diameter = JBUI.scale(DOT_SIZE).toFloat()
            val x = (width - diameter) / 2f
            val y = (height - diameter) / 2f
            val circle = Ellipse2D.Float(x, y, diameter, diameter)

            when (activity) {
                AgentActivity.BUSY -> {
                    val glow = 0.45f + 0.55f * ((sin(phase) + 1.0) / 2.0).toFloat()
                    val halo = JBUI.scale(HALO_SIZE).toFloat()
                    g2.color = withAlpha(agentColor, (glow * 60).toInt())
                    g2.fill(Ellipse2D.Float((width - halo) / 2f, (height - halo) / 2f, halo, halo))
                    g2.color = withAlpha(agentColor, (120 + glow * 135).toInt())
                    g2.fill(circle)
                }

                AgentActivity.ATTENTION -> {
                    g2.color = ATTENTION_COLOR
                    g2.fill(circle)
                }

                AgentActivity.IDLE -> {
                    g2.stroke = BasicStroke(JBUI.scale(1).toFloat())
                    g2.color = MTermColors.muted
                    g2.draw(Ellipse2D.Float(x, y, diameter - 1f, diameter - 1f))
                }
            }
        } finally {
            g2.dispose()
        }
    }

    private fun withAlpha(color: Color, alpha: Int): Color =
        Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))

    private fun tooltipFor(activity: AgentActivity): String = when (activity) {
        AgentActivity.BUSY -> "Working"
        AgentActivity.ATTENTION -> "Waiting for you"
        AgentActivity.IDLE -> "Idle"
    }

    private companion object {
        const val DOT_BOX = 14
        const val DOT_SIZE = 7
        const val HALO_SIZE = 13
        const val PULSE_INTERVAL_MS = 60
        const val PULSE_STEP = 0.22

        val ATTENTION_COLOR = JBColor(Color(0xE0, 0x8A, 0x1E), Color(0xF0, 0xA3, 0x3D))
    }
}
