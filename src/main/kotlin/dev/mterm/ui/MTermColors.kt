package dev.mterm.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.UIManager

internal object MTermColors {

    val background: Color
        get() = EditorColorsManager.getInstance().globalScheme.defaultBackground

    val panel: Color
        get() = UIUtil.getPanelBackground()

    val border: Color
        get() = JBColor.border()

    val text: Color
        get() = UIUtil.getLabelForeground()

    val muted: Color
        get() = UIUtil.getContextHelpForeground()

    val accent: Color
        get() = JBUI.CurrentTheme.Focus.focusColor()

    val buttonBackground: Color
        get() = UIManager.getColor("Button.startBackground")
            ?: UIManager.getColor("Button.background")
            ?: panel

    val buttonHoverBackground: Color
        get() = JBUI.CurrentTheme.ActionButton.hoverBackground()

    val islandsEnabled: Boolean
        get() = when (val value = UIManager.get("Islands")) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> false
        }

    val canvas: Color
        get() = UIManager.getColor("MainWindow.background") ?: panel

    val islandArc: Int
        get() = JBUIScale.scale(UIManager.getInt("Island.arc").takeIf { it > 0 } ?: 20)

    val islandGap: Int
        get() = JBUIScale.scale(8)
}
