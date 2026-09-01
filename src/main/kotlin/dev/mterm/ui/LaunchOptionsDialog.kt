package dev.mterm.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.mterm.agents.AgentLaunchSpec
import dev.mterm.agents.AgentProfile
import dev.mterm.agents.LaunchOptions
import dev.mterm.settings.MTermSettings
import javax.swing.JComponent

class LaunchOptionsDialog(
    profile: AgentProfile,
    spec: AgentLaunchSpec,
    initial: LaunchOptions,
) : DialogWrapper(true) {

    private val modelCombo = combo(spec.modelPresets, initial.model)
    private val effortCombo = combo(spec.effortPresets, initial.effort)

    init {
        title = "Start ${profile.displayName}"
        setOKButtonText("Start")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Model:", modelCombo)
            .addLabeledComponent("Effort:", effortCombo)
            .addComponentToRightColumn(hint("\"$DEFAULT\" keeps the CLI's own setting. You can type any value."))
            .panel
        panel.border = JBUI.Borders.empty(8)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = modelCombo

    fun result(): LaunchOptions = LaunchOptions.of(value(modelCombo), value(effortCombo))

    private fun combo(presets: List<String>, selected: String?): ComboBox<String> =
        ComboBox((listOf(DEFAULT) + presets).toTypedArray()).apply {
            isEditable = true
            prototypeDisplayValue = "claude-fable-5-1[1m]      "
            selectedItem = selected ?: DEFAULT
        }

    private fun value(combo: ComboBox<String>): String? =
        (combo.editor.item as? String)?.trim()?.takeUnless { it.isEmpty() || it == DEFAULT }

    private fun hint(text: String): JComponent = JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }

    companion object {
        const val DEFAULT = "Default"

        fun prompt(profile: AgentProfile): LaunchOptions? {
            val spec = AgentLaunchSpec.forProfile(profile) ?: return LaunchOptions.NONE
            val settings = MTermSettings.getInstance()
            val dialog = LaunchOptionsDialog(profile, spec, settings.lastLaunchOptions(profile.id))
            if (!dialog.showAndGet()) return null
            return dialog.result().also { settings.rememberLaunchOptions(profile.id, it) }
        }
    }
}
