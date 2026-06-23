package dev.mterm.grid

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.mterm.AgentKind
import dev.mterm.session.MTermSessionLauncher
import dev.mterm.ui.MTermColors
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Collections
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.abs

class MTermGridPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
) {

    private val panes = mutableListOf<AgentPane>()

    private var draggingPane: AgentPane? = null
    private var dropTarget: AgentPane? = null

    private val countLabel = JBLabel().apply { foreground = MUTED }

    private val columnsCombo = JComboBox(arrayOf("Auto", "1", "2", "3", "4")).apply {
        isFocusable = false
        putClientProperty("JComboBox.isBorderless", true)
        addActionListener {
            val selected = selectedItem as String
            tileGrid.setColumnsSetting(if (selected == "Auto") null else selected.toInt())
        }
    }

    private val tileGrid = ResizableTileGrid()

    private val placeholder = buildPlaceholder()

    private val toolbar = buildToolbar()

    private val center = JPanel(BorderLayout()).apply {
        background = BACKGROUND
        add(placeholder, BorderLayout.CENTER)
    }

    private val rootPanel = JPanel(BorderLayout()).apply {
        background = BACKGROUND
        add(toolbar, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
    }

    init {
        refreshCount()
        applyTheme()
        ApplicationManager.getApplication().messageBus.connect(parentDisposable)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { applyTheme() })
    }

    val component: JComponent
        get() = rootPanel

    fun preferredFocusComponent(): JComponent? = panes.lastOrNull()?.component

    private fun applyTheme() {
        val islands = MTermColors.islandsEnabled
        val canvas = if (islands) MTermColors.canvas else BACKGROUND
        rootPanel.background = canvas
        center.background = canvas
        center.border = if (islands) JBUI.Borders.empty(MTermColors.islandGap) else JBUI.Borders.empty()
        placeholder.background = canvas
        toolbar.background = if (islands) canvas else PANEL
        toolbar.border = if (islands) {
            JBUI.Borders.empty(2, 8)
        } else {
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                JBUI.Borders.empty(1, 8),
            )
        }
        tileGrid.refreshColors()
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    private fun buildToolbar(): JPanel {
        val bar = JPanel(BorderLayout())
        countLabel.font = JBUI.Fonts.smallFont()
        bar.add(countLabel, BorderLayout.WEST)

        columnsCombo.font = JBUI.Fonts.smallFont()
        val addButton = buildAddButton()
        val east = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            isOpaque = false
            add(JBLabel("Columns").apply { foreground = MUTED; font = JBUI.Fonts.smallFont() })
            add(columnsCombo)
            add(addButton)
        }
        bar.add(east, BorderLayout.EAST)
        return bar
    }

    private fun buildAddButton(): JComponent {
        val arc = JBUI.scale(8)
        val button = object : JBLabel("Add agent") {
            var hovered = false

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = MTermColors.buttonBackground
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                    if (hovered) {
                        g2.color = MTermColors.buttonHoverBackground
                        g2.fillRoundRect(0, 0, width, height, arc, arc)
                    }
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }
        return button.apply {
            icon = AllIcons.General.Add
            font = JBUI.Fonts.smallFont()
            iconTextGap = JBUI.scale(4)
            foreground = TEXT
            isOpaque = false
            border = JBUI.Borders.empty(3, 8)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = showAddMenu(button)
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

    private fun showAddMenu(anchor: JComponent) {
        val menu = JPopupMenu()
        for (agent in AgentKind.entries) {
            val item = JMenuItem("${agent.glyph}  ${agent.displayName}")
            item.addActionListener { addAgent(agent) }
            menu.add(item)
        }
        menu.show(anchor, 0, anchor.height)
    }

    private fun addAgent(agent: AgentKind) {
        val paneDisposable = Disposer.newDisposable(parentDisposable, "mterm-agent")
        var pane: AgentPane? = null
        val widget = MTermSessionLauncher.launch(project, paneDisposable, agent, project.basePath) { title ->
            pane?.updateTitle(title)
        }
        pane = AgentPane(agent, paneDisposable, widget)
        panes.add(pane)
        relayout()
    }

    private fun closeAgent(pane: AgentPane) {
        if (!panes.remove(pane)) return
        Disposer.dispose(pane.disposable)
        relayout()
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
        refreshCount()
        center.revalidate()
        center.repaint()
    }

    private fun startDrag(pane: AgentPane) {
        draggingPane = pane
    }

    private fun updateDropTarget(pointInGrid: Point) {
        val target = panes.firstOrNull { it !== draggingPane && it.component.bounds.contains(pointInGrid) }
        if (target !== dropTarget) {
            dropTarget?.setDropHighlight(false)
            dropTarget = target
            dropTarget?.setDropHighlight(true)
        }
    }

    private fun finishDrag() {
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
            }
        }
    }

    private fun refreshCount() {
        val count = panes.size
        countLabel.text = if (count == 1) "1 agent" else "$count agents"
    }

    private fun buildPlaceholder(): JComponent {
        val label = JBLabel("No agents running — click “+ Add agent” to start one", SwingConstants.CENTER).apply {
            foreground = MUTED
        }
        return JPanel(BorderLayout()).apply {
            background = BACKGROUND
            add(label, BorderLayout.CENTER)
        }
    }

    private inner class AgentPane(
        val agent: AgentKind,
        val disposable: Disposable,
        widget: TerminalWidget,
    ) {
        private val nameLabel = JBLabel("  ${agent.displayName}").apply { foreground = TEXT }

        val component: IslandTilePanel = IslandTilePanel(BorderLayout()).apply {
            add(buildHeader(), BorderLayout.NORTH)
            add(widget.component, BorderLayout.CENTER)
        }

        fun setDropHighlight(on: Boolean) {
            component.dropTarget = on
        }

        fun updateTitle(title: String) {
            val trimmed = if (title.length > 48) title.take(47) + "…" else title
            nameLabel.text = "  $trimmed"
        }

        private fun buildHeader(): JComponent {
            val header = JPanel(BorderLayout()).apply {
                background = PANEL
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                    JBUI.Borders.empty(0, 10, 0, 6),
                )
                preferredSize = Dimension(0, JBUI.scale(28))
                cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            }

            val glyph = JBLabel(agent.glyph).apply { foreground = agent.glyphColor }
            val title = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(glyph)
                add(nameLabel)
            }
            header.add(title, BorderLayout.WEST)

            val close = JBLabel("✕", SwingConstants.CENTER).apply {
                foreground = MUTED
                preferredSize = Dimension(20, 20)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = closeAgent(this@AgentPane)
                    override fun mouseEntered(e: MouseEvent) {
                        foreground = TEXT
                    }

                    override fun mouseExited(e: MouseEvent) {
                        foreground = MUTED
                    }
                })
            }
            header.add(close, BorderLayout.EAST)

            val dragHandler = object : MouseAdapter() {
                private var armed = false
                private var startX = 0
                private var startY = 0

                override fun mousePressed(e: MouseEvent) {
                    armed = true
                    startX = e.xOnScreen
                    startY = e.yOnScreen
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (!armed) return
                    if (draggingPane == null) {
                        if (abs(e.xOnScreen - startX) + abs(e.yOnScreen - startY) < DRAG_THRESHOLD) return
                        startDrag(this@AgentPane)
                    }
                    val point = SwingUtilities.convertPoint(e.component, e.point, tileGrid)
                    updateDropTarget(point)
                }

                override fun mouseReleased(e: MouseEvent) {
                    armed = false
                    finishDrag()
                }
            }
            for (handle in listOf(header, title, glyph, nameLabel)) {
                handle.addMouseListener(dragHandler)
                handle.addMouseMotionListener(dragHandler)
            }
            return header
        }
    }

    private companion object {
        const val DRAG_THRESHOLD = 5

        val BACKGROUND: Color get() = MTermColors.background
        val PANEL: Color get() = MTermColors.panel
        val BORDER: Color get() = MTermColors.border
        val TEXT: Color get() = MTermColors.text
        val MUTED: Color get() = MTermColors.muted
    }
}
