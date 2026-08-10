package dev.mterm.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.mterm.agents.AgentProfile
import dev.mterm.agents.AgentRegistry
import dev.mterm.sound.AgentSound
import dev.mterm.sound.SoundPlayer
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class MTermConfigurable : Configurable {

    private val soundEnabled = JBCheckBox("Play a sound when an agent finishes (terminal bell)")
    private val soundCombo = ComboBox(AgentSound.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it.displayName }
    }
    private val soundForShell = JBCheckBox("Also play for plain system terminals")
    private val reflectTitle = JBCheckBox("Show what the agent is doing in the pane title")
    private val showActivity = JBCheckBox("Show the activity indicator in pane headers")
    private val highlightFocused = JBCheckBox("Outline the focused pane")
    private val notifyEnabled = JBCheckBox("Show an IDE notification when an agent finishes")
    private val notifyOnlyUnfocused = JBCheckBox("Only when the IDE window is in the background")
    private val restoreLayout = JBCheckBox("Restore the grid layout when the project reopens")

    private val tableModel = AgentTableModel()
    private val agentTable = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = JBUI.scale(24)
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        tableHeader.reorderingAllowed = false
        columnModel.getColumn(COLUMN_ENABLED).apply {
            maxWidth = JBUI.scale(60)
            minWidth = JBUI.scale(60)
        }
        columnModel.getColumn(COLUMN_AGENT).preferredWidth = JBUI.scale(180)
        columnModel.getColumn(COLUMN_COMMAND).preferredWidth = JBUI.scale(200)
        columnModel.getColumn(COLUMN_DIRECTORY).preferredWidth = JBUI.scale(180)
        setDefaultRenderer(String::class.java, AgentCellRenderer())
    }

    override fun getDisplayName(): String = "mTerm"

    override fun createComponent(): JComponent {
        val testButton = JButton("Test").apply {
            addActionListener { SoundPlayer.play(soundCombo.item) }
        }
        val soundRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(soundCombo)
            add(testButton)
        }

        soundEnabled.addActionListener { syncEnabled() }
        notifyEnabled.addActionListener { syncEnabled() }

        val tablePanel = ToolbarDecorator.createDecorator(agentTable)
            .setAddAction {
                val dialog = AgentProfileDialog(null)
                if (dialog.showAndGet()) tableModel.add(dialog.result())
            }
            .setEditAction {
                val row = agentTable.selectedRow.takeIf { it >= 0 } ?: return@setEditAction
                val dialog = AgentProfileDialog(tableModel.rows[row])
                if (dialog.showAndGet()) tableModel.update(row, dialog.result())
            }
            .setRemoveAction {
                val row = agentTable.selectedRow.takeIf { it >= 0 } ?: return@setRemoveAction
                tableModel.removeAt(row)
            }
            .setRemoveActionUpdater { agentTable.selectedRow.let { it >= 0 && !tableModel.rows[it].builtIn } }
            .setEditActionUpdater { agentTable.selectedRow >= 0 }
            .createPanel()
        tablePanel.preferredSize = Dimension(JBUI.scale(640), JBUI.scale(190))

        val form = FormBuilder.createFormBuilder()
            .addComponent(sectionLabel("Agents"))
            .addComponent(tablePanel)
            .addComponent(hint("Built-in agents can be renamed or given extra flags; custom ones can be removed."))
            .addSeparator(JBUI.scale(8))
            .addComponent(sectionLabel("Panes"))
            .addComponent(reflectTitle)
            .addComponent(showActivity)
            .addComponent(highlightFocused)
            .addComponent(restoreLayout)
            .addSeparator(JBUI.scale(8))
            .addComponent(sectionLabel("When an agent finishes"))
            .addComponent(soundEnabled)
            .addLabeledComponent("Sound:", soundRow)
            .addComponent(soundForShell)
            .addComponent(notifyEnabled)
            .addComponent(notifyOnlyUnfocused)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        form.border = JBUI.Borders.empty(11)

        reset()
        return form
    }

    private fun sectionLabel(text: String): JComponent = JBLabel(text).apply {
        font = font.deriveFont(font.style or java.awt.Font.BOLD)
        border = JBUI.Borders.emptyBottom(4)
    }

    private fun hint(text: String): JComponent = JBLabel(text).apply {
        foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
        border = JBUI.Borders.emptyTop(2)
    }

    private fun syncEnabled() {
        val soundOn = soundEnabled.isSelected
        soundCombo.isEnabled = soundOn
        soundForShell.isEnabled = soundOn
        notifyOnlyUnfocused.isEnabled = notifyEnabled.isSelected
    }

    override fun isModified(): Boolean {
        val settings = MTermSettings.getInstance()
        return soundEnabled.isSelected != settings.soundEnabled ||
            soundCombo.item != settings.sound ||
            soundForShell.isSelected != settings.soundForShell ||
            reflectTitle.isSelected != settings.reflectAgentTitle ||
            showActivity.isSelected != settings.showActivityIndicator ||
            highlightFocused.isSelected != settings.highlightFocusedPane ||
            notifyEnabled.isSelected != settings.notifyEnabled ||
            notifyOnlyUnfocused.isSelected != settings.notifyOnlyWhenIdeUnfocused ||
            restoreLayout.isSelected != settings.restoreLayout ||
            tableModel.rows != AgentRegistry.getInstance().profiles()
    }

    override fun apply() {
        val settings = MTermSettings.getInstance()
        settings.soundEnabled = soundEnabled.isSelected
        settings.sound = soundCombo.item
        settings.soundForShell = soundForShell.isSelected
        settings.reflectAgentTitle = reflectTitle.isSelected
        settings.showActivityIndicator = showActivity.isSelected
        settings.highlightFocusedPane = highlightFocused.isSelected
        settings.notifyEnabled = notifyEnabled.isSelected
        settings.notifyOnlyWhenIdeUnfocused = notifyOnlyUnfocused.isSelected
        settings.restoreLayout = restoreLayout.isSelected
        AgentRegistry.getInstance().replaceAll(tableModel.rows)
    }

    override fun reset() {
        val settings = MTermSettings.getInstance()
        soundEnabled.isSelected = settings.soundEnabled
        soundCombo.item = settings.sound
        soundForShell.isSelected = settings.soundForShell
        reflectTitle.isSelected = settings.reflectAgentTitle
        showActivity.isSelected = settings.showActivityIndicator
        highlightFocused.isSelected = settings.highlightFocusedPane
        notifyEnabled.isSelected = settings.notifyEnabled
        notifyOnlyUnfocused.isSelected = settings.notifyOnlyWhenIdeUnfocused
        restoreLayout.isSelected = settings.restoreLayout
        tableModel.replaceAll(AgentRegistry.getInstance().profiles())
        syncEnabled()
    }

    private class AgentTableModel : AbstractTableModel() {

        val rows = mutableListOf<AgentProfile>()

        fun replaceAll(profiles: List<AgentProfile>) {
            rows.clear()
            rows.addAll(profiles)
            fireTableDataChanged()
        }

        fun add(profile: AgentProfile) {
            rows.add(profile)
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
        }

        fun update(index: Int, profile: AgentProfile) {
            rows[index] = profile
            fireTableRowsUpdated(index, index)
        }

        fun removeAt(index: Int) {
            rows.removeAt(index)
            fireTableRowsDeleted(index, index)
        }

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String = when (column) {
            COLUMN_ENABLED -> "Show"
            COLUMN_AGENT -> "Agent"
            COLUMN_COMMAND -> "Command"
            else -> "Working directory"
        }

        override fun getColumnClass(column: Int): Class<*> =
            if (column == COLUMN_ENABLED) Boolean::class.javaObjectType else String::class.java

        override fun isCellEditable(row: Int, column: Int): Boolean = column == COLUMN_ENABLED

        override fun getValueAt(row: Int, column: Int): Any {
            val profile = rows[row]
            return when (column) {
                COLUMN_ENABLED -> profile.enabled
                COLUMN_AGENT -> "${profile.glyph}  ${profile.displayName}"
                COLUMN_COMMAND -> profile.command ?: "(system shell)"
                else -> profile.workingDirectory ?: "(project root)"
            }
        }

        override fun setValueAt(value: Any?, row: Int, column: Int) {
            if (column != COLUMN_ENABLED) return
            rows[row] = rows[row].copy(enabled = value as? Boolean ?: true)
            fireTableRowsUpdated(row, row)
        }
    }

    private inner class AgentCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val profile = tableModel.rows.getOrNull(agentTable.convertRowIndexToModel(row))
            if (!isSelected && profile != null) {
                foreground = when (column) {
                    COLUMN_AGENT -> profile.color
                    COLUMN_COMMAND -> if (profile.command == null) {
                        com.intellij.util.ui.UIUtil.getContextHelpForeground()
                    } else {
                        com.intellij.util.ui.UIUtil.getLabelForeground()
                    }

                    else -> com.intellij.util.ui.UIUtil.getContextHelpForeground()
                }
            }
            border = JBUI.Borders.emptyLeft(6)
            return component
        }
    }

    private companion object {
        const val COLUMN_ENABLED = 0
        const val COLUMN_AGENT = 1
        const val COLUMN_COMMAND = 2
        const val COLUMN_DIRECTORY = 3
    }
}
