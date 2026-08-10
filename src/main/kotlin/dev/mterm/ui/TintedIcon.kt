package dev.mterm.ui

import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon

class TintedIcon(private val source: Icon, private val color: Color) : Icon {

    override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
        val image = UIUtil.createImage(component, iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
        val canvas = image.createGraphics()
        try {
            source.paintIcon(component, canvas, 0, 0)
            canvas.composite = AlphaComposite.SrcAtop
            canvas.color = color
            canvas.fillRect(0, 0, iconWidth, iconHeight)
        } finally {
            canvas.dispose()
        }
        UIUtil.drawImage(g, image, x, y, null)
    }

    override fun getIconWidth(): Int = source.iconWidth

    override fun getIconHeight(): Int = source.iconHeight
}
