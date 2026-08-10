package dev.mterm.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.mterm.agents.AgentProfile
import dev.mterm.agents.AgentRegistry
import java.awt.Color
import javax.swing.JComponent

class AgentProfileDialog(private val original: AgentProfile?) : DialogWrapper(true) {

    private val nameField = JBTextField(original?.displayName.orEmpty(), 24)
    private val commandField = JBTextField(original?.command.orEmpty(), 24)
    private val glyphField = JBTextField(original?.glyph ?: "❯", 4)
    private val directoryField = JBTextField(original?.workingDirectory.orEmpty(), 24)
    private val colorPanel = ColorPanel().apply {
        selectedColor = original?.color ?: Color(0x6E, 0x9E, 0xD8)
    }

    init {
        title = if (original == null) "Add Agent" else "Edit Agent"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("Command:", commandField)
            .addComponentToRightColumn(hint("Leave empty for a plain shell. Example: claude --continue"))
            .addLabeledComponent("Glyph:", glyphField)
            .addLabeledComponent("Colour:", colorPanel)
            .addLabeledComponent("Working directory:", directoryField)
            .addComponentToRightColumn(hint("Leave empty to use the project root"))
            .panel
        panel.border = JBUI.Borders.empty(8)
        return panel
    }

    private fun hint(text: String): JComponent = JBLabel(text).apply {
        foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    override fun doValidate(): ValidationInfo? = when {
        nameField.text.isBlank() -> ValidationInfo("Name cannot be empty", nameField)
        glyphField.text.isBlank() -> ValidationInfo("Glyph cannot be empty", glyphField)
        else -> null
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    fun result(): AgentProfile = AgentProfile(
        id = original?.id ?: AgentRegistry.getInstance().newProfileId(),
        displayName = nameField.text.trim(),
        command = commandField.text.trim().takeIf { it.isNotEmpty() },
        glyph = glyphField.text.trim().take(2),
        color = colorPanel.selectedColor ?: Color(0x6E, 0x9E, 0xD8),
        workingDirectory = directoryField.text.trim().takeIf { it.isNotEmpty() },
        builtIn = original?.builtIn ?: false,
        enabled = original?.enabled ?: true,
    )
}
