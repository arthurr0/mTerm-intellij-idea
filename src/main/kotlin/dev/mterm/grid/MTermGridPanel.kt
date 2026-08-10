package dev.mterm.grid

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.agents.AgentRegistry
import dev.mterm.notify.MTermNotifier
import dev.mterm.settings.MTermSettings
import dev.mterm.ui.MTermColors
import dev.mterm.vfs.MTermFileSystem
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import java.util.Collections
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class MTermGridPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
) {

    private val panes = mutableListOf<AgentPane>()

    private var draggingPane: AgentPane? = null
    private var dropTarget: AgentPane? = null
    private var focusedPane: AgentPane? = null
    private var maximizedPane: AgentPane? = null
    private var restoring = false

    private val tileGrid = ResizableTileGrid().apply {
        onFractionsChanged = { saveLayout() }
    }

    private val toolbar = MTermGridToolbar(
        onAddAgent = { anchor -> showAddMenu(anchor) },
        onColumnsChanged = { columns ->
            if (!restoring) {
                tileGrid.setColumnsSetting(columns)
                saveLayout()
            }
        },
        onBroadcastVisibilityChanged = { visible ->
            panes.forEach { it.setBroadcastControlsVisible(visible) }
        },
        onBroadcastSend = { text ->
            panes.filter { it.includedInBroadcast }.forEach { it.sendText(text) }
        },
    )

    private var placeholder = buildPlaceholder()

    private val center = JPanel(BorderLayout()).apply {
        add(placeholder, BorderLayout.CENTER)
    }

    private val rootPanel = JPanel(BorderLayout()).apply {
        add(toolbar.component, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
    }

    private val focusWatcher = PropertyChangeListener { event ->
        val owner = event.newValue as? java.awt.Component ?: return@PropertyChangeListener
        val pane = panes.firstOrNull { SwingUtilities.isDescendingFrom(owner, it.component) }
        if (pane != null) setFocusedPane(pane)
    }

    private val paneCallbacks = PaneCallbacks()

    init {
        refreshCount()
        applyTheme()
        registerShortcuts()

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("permanentFocusOwner", focusWatcher)
        Disposer.register(parentDisposable) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("permanentFocusOwner", focusWatcher)
        }

        val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { applyTheme() })
        connection.subscribe(AgentRegistry.TOPIC, AgentRegistry.Listener { rebuildPlaceholder() })

        restoreLayout()
    }

    val component: JComponent
        get() = rootPanel

    fun preferredFocusComponent(): JComponent? =
        (focusedPane ?: panes.lastOrNull())?.focusComponent

    fun focusPane(index: Int) {
        val pane = panes.getOrNull(index) ?: return
        setFocusedPane(pane)
        pane.focusComponent?.requestFocusInWindow()
    }

    private fun applyTheme() {
        val islands = MTermColors.islandsEnabled
        val canvas = if (islands) MTermColors.canvas else MTermColors.background
        rootPanel.background = canvas
        center.background = canvas
        center.border = if (islands) {
            JBUI.Borders.empty(0, MTermColors.TILE_GAP, MTermColors.TILE_GAP, MTermColors.TILE_GAP)
        } else {
            JBUI.Borders.empty()
        }
        placeholder.background = canvas
        toolbar.applyTheme()
        panes.forEach { it.applyTheme() }
        tileGrid.refreshColors()
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    private fun showAddMenu(anchor: JComponent) {
        val menu = JPopupMenu()
        for (profile in AgentRegistry.getInstance().enabledProfiles()) {
            val item = JMenuItem("${profile.glyph}  ${profile.displayName}")
            item.addActionListener { addAgent(profile) }
            menu.add(item)
        }
        if (menu.componentCount == 0) {
            menu.add(JMenuItem("No agents enabled — configure them in Settings").apply { isEnabled = false })
        }
        menu.show(anchor, 0, anchor.height)
    }

    private fun addAgent(profile: AgentProfile) {
        val pane = AgentPane(project, profile, parentDisposable, paneCallbacks)
        panes.add(pane)
        relayout()
        saveLayout()
        SwingUtilities.invokeLater {
            setFocusedPane(pane)
            pane.focusComponent?.requestFocusInWindow()
        }
    }

    private fun closeAgent(pane: AgentPane) {
        if (!panes.remove(pane)) return
        if (maximizedPane === pane) maximizedPane = null
        if (focusedPane === pane) focusedPane = null
        pane.dispose()
        relayout()
        saveLayout()
    }

    private fun setFocusedPane(pane: AgentPane?) {
        if (focusedPane === pane) return
        focusedPane?.setFocused(false)
        focusedPane = pane
        pane?.setFocused(true)
        refreshCount()
    }

    private fun toggleMaximize(pane: AgentPane) {
        val next = if (maximizedPane === pane) null else pane
        maximizedPane = next
        tileGrid.maximizedTile = next?.component
        panes.forEach { it.setMaximized(it === next) }
        tileGrid.revalidate()
        tileGrid.repaint()
        SwingUtilities.invokeLater { (next ?: pane).focusComponent?.requestFocusInWindow() }
    }

    private fun relayout() {
        if (panes.isEmpty()) {
            center.removeAll()
            center.add(placeholder, BorderLayout.CENTER)
        } else {
            tileGrid.setTiles(panes.map { it.component })
            if (center.componentCount == 0 || center.getComponent(0) !== tileGrid) {
                center.removeAll()
                center.add(tileGrid, BorderLayout.CENTER)
            }
        }
        panes.forEach { it.setBroadcastControlsVisible(toolbar.broadcastVisible) }
        refreshCount()
        center.revalidate()
        center.repaint()
    }

    private fun refreshCount() {
        toolbar.setStatus(
            total = panes.size,
            working = panes.count { it.activity == AgentActivity.BUSY },
            waiting = panes.count { it.activity == AgentActivity.ATTENTION },
        )
    }

    private fun restoreLayout() {
        if (!MTermSettings.getInstance().restoreLayout) return
        val store = MTermLayoutStore.getInstance(project)
        val registry = AgentRegistry.getInstance()
        val profiles = store.agentIds.mapNotNull { registry.find(it) }
        if (profiles.isEmpty()) return

        restoring = true
        try {
            toolbar.setColumns(store.columns)
            profiles.forEach { panes.add(AgentPane(project, it, parentDisposable, paneCallbacks)) }
            tileGrid.restoreLayout(
                panes.map { it.component },
                store.columns,
                store.columnFractions(),
                store.rowFractions(),
            )
            center.removeAll()
            center.add(tileGrid, BorderLayout.CENTER)
            panes.forEach { it.setBroadcastControlsVisible(toolbar.broadcastVisible) }
            refreshCount()
        } finally {
            restoring = false
        }
    }

    private fun saveLayout() {
        if (restoring) return
        MTermLayoutStore.getInstance(project).save(
            agentIds = panes.map { it.profile.id },
            columns = tileGrid.columns,
            colFractions = tileGrid.columnFractions(),
            rowFractions = tileGrid.rowFractions(),
        )
    }

    private fun rebuildPlaceholder() {
        placeholder = buildPlaceholder()
        if (panes.isEmpty()) {
            center.removeAll()
            center.add(placeholder, BorderLayout.CENTER)
            applyTheme()
            center.revalidate()
            center.repaint()
        }
    }

    private fun buildPlaceholder(): JPanel {
        val title = JBLabel("No agents running", SwingConstants.CENTER).apply {
            foreground = MTermColors.text
            font = JBUI.Fonts.label().deriveFont(JBUI.Fonts.label().size2D + JBUI.scale(3))
        }
        val subtitle = JBLabel("Pick one to start, or press Alt+Shift+A", SwingConstants.CENTER).apply {
            foreground = MTermColors.muted
            font = JBUI.Fonts.smallFont()
        }

        val quickStart = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0)).apply {
            isOpaque = false
            for (profile in AgentRegistry.getInstance().enabledProfiles()) {
                add(buildQuickStartCard(profile))
            }
        }

        val column = JPanel().apply {
            isOpaque = false
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(title.alignCenter())
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(subtitle.alignCenter())
            add(Box.createVerticalStrut(JBUI.scale(18)))
            add(quickStart.alignCenter())
        }

        return JPanel(GridBagLayout()).apply {
            background = MTermColors.background
            add(column, GridBagConstraints())
        }
    }

    private fun <T : JComponent> T.alignCenter(): T = apply { alignmentX = JComponent.CENTER_ALIGNMENT }

    private fun buildQuickStartCard(profile: AgentProfile): JComponent {
        val arc = JBUI.scale(10)
        val card = object : JPanel(BorderLayout()) {
            var hovered = false

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = if (hovered) MTermColors.buttonHoverBackground else MTermColors.panel
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                    g2.color = if (hovered) profile.color else MTermColors.border
                    g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
                } finally {
                    g2.dispose()
                }
            }
        }

        val glyph = JBLabel(profile.glyph, SwingConstants.CENTER).apply {
            foreground = profile.color
            font = font.deriveFont(font.size2D + JBUI.scale(8))
        }
        val name = JBLabel(profile.displayName, SwingConstants.CENTER).apply {
            foreground = MTermColors.text
            font = JBUI.Fonts.smallFont()
        }

        return card.apply {
            isOpaque = false
            border = JBUI.Borders.empty(14, 18)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(glyph, BorderLayout.CENTER)
            add(name, BorderLayout.SOUTH)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = addAgent(profile)
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }
    }

    private fun registerShortcuts() {
        for (index in 0 until 9) {
            shortcutAction("Focus mTerm pane ${index + 1}", KeyEvent.VK_1 + index) { focusPane(index) }
        }
        shortcutAction("Add mTerm agent", KeyEvent.VK_A) { showAddMenu(toolbar.addAnchor) }
        shortcutAction("Toggle mTerm broadcast", KeyEvent.VK_B) {
            toolbar.setBroadcastVisible(!toolbar.broadcastVisible)
        }
        shortcutAction("Maximize mTerm pane", KeyEvent.VK_M) {
            focusedPane?.let { toggleMaximize(it) }
        }
        shortcutAction("Restart mTerm agent", KeyEvent.VK_R) { focusedPane?.restart() }
        shortcutAction("Close mTerm pane", KeyEvent.VK_W) { focusedPane?.let { closeAgent(it) } }
    }

    private fun shortcutAction(name: String, keyCode: Int, handler: () -> Unit) {
        val action = object : AnAction(name), DumbAware {
            override fun actionPerformed(e: AnActionEvent) = handler()
        }
        val stroke = KeyStroke.getKeyStroke(keyCode, KeyEvent.ALT_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK)
        action.registerCustomShortcutSet(CustomShortcutSet(stroke), rootPanel, parentDisposable)
    }

    private inner class PaneCallbacks : AgentPane.Callbacks {
        override fun onClose(pane: AgentPane) = closeAgent(pane)

        override fun onToggleMaximize(pane: AgentPane) = toggleMaximize(pane)

        override fun onDragStart(pane: AgentPane) {
            draggingPane = pane
        }

        override fun onDragTo(pane: AgentPane, event: MouseEvent) {
            if (draggingPane == null) return
            val point: Point = SwingUtilities.convertPoint(event.component, event.point, tileGrid)
            val target = panes.firstOrNull { it !== draggingPane && it.component.bounds.contains(point) }
            if (target !== dropTarget) {
                dropTarget?.setDropHighlight(false)
                dropTarget = target
                dropTarget?.setDropHighlight(true)
            }
        }

        override fun onDragFinish(pane: AgentPane) {
            val dragged = draggingPane
            val target = dropTarget
            draggingPane = null
            dropTarget = null
            target?.setDropHighlight(false)
            if (dragged != null && target != null && dragged !== target) {
                val from = panes.indexOf(dragged)
                val to = panes.indexOf(target)
                if (from >= 0 && to >= 0) {
                    Collections.swap(panes, from, to)
                    tileGrid.reorderTiles(panes.map { it.component })
                    saveLayout()
                }
            }
        }

        override fun onActivityChanged(pane: AgentPane, activity: AgentActivity) {
            refreshCount()
            if (activity != AgentActivity.ATTENTION) return
            if (pane === focusedPane && rootPanel.isShowing) return
            MTermNotifier.agentFinished(project, pane.profile.displayName, project.name) {
                FileEditorManager.getInstance(project).openFile(MTermFileSystem.getInstance().consoleFile(), true)
                focusPane(panes.indexOf(pane))
            }
        }

        override fun onFocusRequested(pane: AgentPane) {
            setFocusedPane(pane)
            pane.focusComponent?.requestFocusInWindow()
        }
    }

}
