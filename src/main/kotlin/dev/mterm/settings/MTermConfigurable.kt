package dev.mterm.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.mterm.sound.AgentSound
import dev.mterm.sound.SoundPlayer
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class MTermConfigurable : Configurable {

    private val soundEnabled = JBCheckBox("Play a sound when an agent finishes (terminal bell)")
    private val soundCombo = ComboBox(AgentSound.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it.displayName }
    }
    private val soundForShell = JBCheckBox("Also play for plain system terminals")
    private val reflectTitle = JBCheckBox("Show what the agent is doing in the pane title")

    override fun getDisplayName(): String = "mTerm"

    override fun createComponent(): JComponent {
        val testButton = JButton("Test").apply {
            addActionListener { SoundPlayer.play(soundCombo.item) }
        }
        val soundRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(soundCombo)
            add(testButton)
        }

        soundEnabled.addActionListener { syncEnabled() }

        val form = FormBuilder.createFormBuilder()
            .addComponent(soundEnabled)
            .addLabeledComponent("Sound:", soundRow)
            .addComponent(soundForShell)
            .addComponent(reflectTitle)
            .panel
        form.border = JBUI.Borders.empty(11)

        reset()
        return form
    }

    private fun syncEnabled() {
        val on = soundEnabled.isSelected
        soundCombo.isEnabled = on
        soundForShell.isEnabled = on
    }

    override fun isModified(): Boolean {
        val settings = MTermSettings.getInstance()
        return soundEnabled.isSelected != settings.soundEnabled ||
            soundCombo.item != settings.sound ||
            soundForShell.isSelected != settings.soundForShell ||
            reflectTitle.isSelected != settings.reflectAgentTitle
    }

    override fun apply() {
        val settings = MTermSettings.getInstance()
        settings.soundEnabled = soundEnabled.isSelected
        settings.sound = soundCombo.item
        settings.soundForShell = soundForShell.isSelected
        settings.reflectAgentTitle = reflectTitle.isSelected
    }

    override fun reset() {
        val settings = MTermSettings.getInstance()
        soundEnabled.isSelected = settings.soundEnabled
        soundCombo.item = settings.sound
        soundForShell.isSelected = settings.soundForShell
        reflectTitle.isSelected = settings.reflectAgentTitle
        syncEnabled()
    }
}
