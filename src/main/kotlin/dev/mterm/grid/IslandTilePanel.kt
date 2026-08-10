package dev.mterm.grid

import com.intellij.ui.scale.JBUIScale
import dev.mterm.ui.MTermColors
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

class IslandTilePanel(layout: LayoutManager) : JPanel(layout) {

    var dropHighlighted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    var focusedPane: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    var accentColor: Color? = null
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    private var maskShape: Area? = null
    private var maskWidth = -1
    private var maskHeight = -1
    private var maskArc = -1

    init {
        isOpaque = true
    }

    private fun arc(): Int = if (MTermColors.islandsEnabled) MTermColors.islandArc else 0

    override fun isPaintingOrigin(): Boolean = MTermColors.islandsEnabled

    override fun paintComponent(g: Graphics) {
        g.color = MTermColors.background
        g.fillRect(0, 0, width, height)
    }

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        val arc = arc()
        if (arc > 0) paintCornerMask(g, arc)
        paintTileBorder(g, arc)
    }

    private fun paintCornerMask(g: Graphics, arc: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.color = MTermColors.canvas
            g2.fill(cornerMask(arc))
        } finally {
            g2.dispose()
        }
    }

    private fun cornerMask(arc: Int): Area {
        val cached = maskShape
        if (cached != null && maskWidth == width && maskHeight == height && maskArc == arc) return cached

        val w = width.toFloat()
        val h = height.toFloat()
        val mask = Area(Rectangle2D.Float(0f, 0f, w, h))
        mask.subtract(Area(RoundRectangle2D.Float(0f, 0f, w, h, arc.toFloat(), arc.toFloat())))
        maskShape = mask
        maskWidth = width
        maskHeight = height
        maskArc = arc
        return mask
    }

    private fun paintTileBorder(g: Graphics, arc: Int) {
        val highlighted = dropHighlighted || focusedPane
        if (!highlighted && arc > 0) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            val stroke = if (highlighted) JBUIScale.scale(2).toFloat() else 1f
            g2.stroke = BasicStroke(stroke)
            g2.color = when {
                dropHighlighted -> MTermColors.accent
                focusedPane -> accentColor ?: MTermColors.accent
                else -> MTermColors.border
            }
            val offset = stroke / 2f
            val outerArc = (if (arc > 0) arc else JBUIScale.scale(8)).toFloat()
            val effectiveArc = (outerArc - stroke).coerceAtLeast(0f)
            g2.draw(
                RoundRectangle2D.Float(offset, offset, width - stroke, height - stroke, effectiveArc, effectiveArc),
            )
        } finally {
            g2.dispose()
        }
    }
}
