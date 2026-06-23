package dev.mterm.grid

import com.intellij.ui.scale.JBUIScale
import dev.mterm.ui.MTermColors
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

class IslandTilePanel(layout: LayoutManager) : JPanel(layout) {

    var dropTarget: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    init {
        isOpaque = true
    }

    private fun arc(): Int = if (MTermColors.islandsEnabled) MTermColors.islandArc else 0

    override fun isPaintingOrigin(): Boolean = MTermColors.islandsEnabled

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = arc()
            if (arc > 0) {
                g2.color = MTermColors.canvas
                g2.fillRect(0, 0, width, height)
                g2.color = MTermColors.background
                g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc.toFloat(), arc.toFloat()))
            } else {
                g2.color = MTermColors.background
                g2.fillRect(0, 0, width, height)
            }
        } finally {
            g2.dispose()
        }
    }

    override fun paintChildren(g: Graphics) {
        val arc = arc()
        if (arc > 0) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.clip(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc.toFloat(), arc.toFloat()))
                super.paintChildren(g2)
            } finally {
                g2.dispose()
            }
        } else {
            super.paintChildren(g)
        }
        paintTileBorder(g, arc)
    }

    private fun paintTileBorder(g: Graphics, arc: Int) {
        if (!dropTarget && arc > 0) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val stroke = if (dropTarget) 2f else 1f
            g2.stroke = BasicStroke(stroke)
            g2.color = if (dropTarget) MTermColors.accent else MTermColors.border
            val offset = stroke / 2f
            val effectiveArc = (if (arc > 0) arc else JBUIScale.scale(8)).toFloat()
            g2.draw(
                RoundRectangle2D.Float(offset, offset, width - stroke, height - stroke, effectiveArc, effectiveArc),
            )
        } finally {
            g2.dispose()
        }
    }
}
