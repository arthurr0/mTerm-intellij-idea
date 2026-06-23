package dev.mterm.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color

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
}
