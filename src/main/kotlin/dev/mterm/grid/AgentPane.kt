package dev.mterm.grid

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.changes.AgentChangeTracker
import dev.mterm.changes.ui.AgentChangesUi
import dev.mterm.session.MTermSessionLauncher
import dev.mterm.settings.MTermSettings
import dev.mterm.ui.ActivityDot
import dev.mterm.ui.MTermColors
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border

class AgentPane(
    private val project: Project,
    val profile: AgentProfile,
    parentDisposable: Disposable,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onClose(pane: AgentPane)
        fun onToggleMaximize(pane: AgentPane)
        fun onDragStart(pane: AgentPane)
        fun onDragTo(pane: AgentPane, event: MouseEvent)
        fun onDragFinish(pane: AgentPane)
        fun onActivityChanged(pane: AgentPane, activity: AgentActivity)
        fun onFocusRequested(pane: AgentPane)
    }

    private val paneDisposable = Disposer.newDisposable(parentDisposable, "mterm-pane")
    private var sessionDisposable: Disposable = Disposer.newDisposable(paneDisposable, "mterm-session")

    private val nameLabel = JBLabel(profile.displayName).apply {
        foreground = MTermColors.text
        border = JBUI.Borders.emptyLeft(6)
    }

    private val activityDot = ActivityDot(profile.color)

    private val changesLabel = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = MTermColors.muted
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isVisible = false
        toolTipText = "Files this agent changed — click to open Agent Changes"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = AgentChangesUi.getInstance(project).show(sessionId)
        })
    }

    private val broadcastToggle = HeaderButton(glyphText = BROADCAST_ON, tooltip = "Included in broadcast") {
        includedInBroadcast = !includedInBroadcast
    }

    private val maximizeButton = HeaderButton(icon = AllIcons.General.ExpandComponent, tooltip = "Maximize pane") {
        callbacks.onToggleMaximize(this)
    }

    private val closeButton = HeaderButton(glyphText = "✕", tooltip = "Close pane") { callbacks.onClose(this) }

    private val terminalHolder = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = contentBorder()
    }

    private var sessionId: String? = null

    private var widget: TerminalWidget = createWidget()

    var activity: AgentActivity = AgentActivity.IDLE
        private set

    var includedInBroadcast: Boolean = true
        set(value) {
            field = value
            broadcastToggle.glyph = if (value) BROADCAST_ON else BROADCAST_OFF
            broadcastToggle.dimmed = !value
            broadcastToggle.toolTipText = if (value) "Included in broadcast" else "Excluded from broadcast"
        }

    private val header: JPanel = buildHeader()

    val component: IslandTilePanel = IslandTilePanel(BorderLayout()).apply {
        accentColor = profile.color
        add(header, BorderLayout.NORTH)
        add(terminalHolder, BorderLayout.CENTER)
    }

    init {
        terminalHolder.add(widget.component, BorderLayout.CENTER)
        setBroadcastControlsVisible(false)
        project.messageBus.connect(paneDisposable).subscribe(
            AgentChangeTracker.TOPIC,
            object : AgentChangeTracker.Listener {
                override fun changesUpdated() = refreshChangeCount()
            },
        )
    }

    private fun refreshChangeCount() {
        val key = sessionId
        val record = key?.let { id ->
            AgentChangeTracker.getInstance(project).sessions().firstOrNull { it.id == id }
        }
        val count = record?.changedPaths?.size ?: 0
        changesLabel.text = if (count == 0) "" else "Δ $count"
        changesLabel.isVisible = count > 0
    }

    val focusComponent: JComponent? get() = widget.preferredFocusableComponent

    fun dispose() {
        Disposer.dispose(paneDisposable)
    }

    fun setDropHighlight(on: Boolean) {
        component.dropHighlighted = on
    }

    fun setFocused(on: Boolean) {
        component.focusedPane = on && MTermSettings.getInstance().highlightFocusedPane
        if (on && activity == AgentActivity.ATTENTION) applyActivity(AgentActivity.IDLE)
    }

    fun setMaximized(on: Boolean) {
        maximizeButton.icon = if (on) AllIcons.General.CollapseComponent else AllIcons.General.ExpandComponent
        maximizeButton.toolTipText = if (on) "Restore pane" else "Maximize pane"
    }

    fun setBroadcastControlsVisible(visible: Boolean) {
        broadcastToggle.isVisible = visible
    }

    fun sendText(text: String) {
        widget.sendCommandToExecute(text)
    }

    fun restart() {
        Disposer.dispose(sessionDisposable)
        terminalHolder.removeAll()
        sessionDisposable = Disposer.newDisposable(paneDisposable, "mterm-session")
        sessionId = null
        refreshChangeCount()
        widget = createWidget()
        terminalHolder.add(widget.component, BorderLayout.CENTER)
        nameLabel.text = profile.displayName
        applyActivity(AgentActivity.IDLE)
        terminalHolder.revalidate()
        terminalHolder.repaint()
        SwingUtilities.invokeLater { callbacks.onFocusRequested(this) }
    }

    private fun createWidget(): TerminalWidget = MTermSessionLauncher.launch(
        project = project,
        parent = sessionDisposable,
        profile = profile,
        workingDirectory = profile.workingDirectory ?: project.basePath,
        onTitleChange = ::updateTitle,
        onActivityChange = ::onActivityChanged,
        onSessionOpened = { session ->
            sessionId = session.sessionId
            refreshChangeCount()
        },
    )

    private fun onActivityChanged(next: AgentActivity) {
        val hasFocus = component.focusedPane
        val effective = if (next == AgentActivity.ATTENTION && hasFocus) AgentActivity.IDLE else next
        applyActivity(effective)
        callbacks.onActivityChanged(this, next)
    }

    private fun applyActivity(next: AgentActivity) {
        activity = next
        activityDot.activity = if (MTermSettings.getInstance().showActivityIndicator) next else AgentActivity.IDLE
    }

    private fun updateTitle(title: String) {
        nameLabel.text = if (title.length > MAX_TITLE) title.take(MAX_TITLE - 1) + "…" else title
        nameLabel.toolTipText = title
    }

    fun applyTheme() {
        header.background = MTermColors.panel
        header.border = headerBorder()
        terminalHolder.border = contentBorder()
        nameLabel.foreground = MTermColors.text
        changesLabel.foreground = MTermColors.muted
        broadcastToggle.refreshColors()
        closeButton.refreshColors()
        component.revalidate()
        component.repaint()
    }

    private fun contentBorder(): Border = if (MTermColors.islandsEnabled) {
        JBUI.Borders.empty(0, ISLAND_CONTENT_PADDING, ISLAND_CONTENT_PADDING, ISLAND_CONTENT_PADDING)
    } else {
        JBUI.Borders.empty(0, FLAT_CONTENT_PADDING, FLAT_CONTENT_PADDING, FLAT_CONTENT_PADDING)
    }

    private fun headerBorder(): Border = if (MTermColors.islandsEnabled) {
        JBUI.Borders.empty(0, HEADER_PADDING_LEFT, 0, HEADER_PADDING_RIGHT)
    } else {
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, MTermColors.border),
            JBUI.Borders.empty(0, HEADER_PADDING_LEFT, 0, HEADER_PADDING_RIGHT),
        )
    }

    private fun buildHeader(): JPanel {
        val header = JPanel(BorderLayout()).apply {
            background = MTermColors.panel
            border = headerBorder()
            preferredSize = Dimension(0, JBUI.scale(HEADER_HEIGHT))
            cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        }

        val glyph = JBLabel(profile.glyph).apply {
            foreground = profile.color
            border = JBUI.Borders.emptyLeft(4)
        }
        val title = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(activityDot)
            add(glyph)
            add(nameLabel)
        }
        header.add(title, BorderLayout.WEST)

        val restartButton = HeaderButton(icon = AllIcons.Actions.Restart, tooltip = "Restart agent") { restart() }

        val actions = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val constraints = GridBagConstraints().apply {
                gridy = 0
                anchor = GridBagConstraints.CENTER
                insets = JBUI.insetsLeft(ACTION_GAP)
            }
            add(changesLabel, constraints)
            add(broadcastToggle, constraints)
            add(restartButton, constraints)
            add(maximizeButton, constraints)
            add(closeButton, constraints)
        }
        header.add(actions, BorderLayout.EAST)

        val dragHandler = object : MouseAdapter() {
            private var armed = false
            private var startX = 0
            private var startY = 0
            private var dragging = false

            override fun mousePressed(e: MouseEvent) {
                armed = true
                dragging = false
                startX = e.xOnScreen
                startY = e.yOnScreen
                callbacks.onFocusRequested(this@AgentPane)
            }

            override fun mouseDragged(e: MouseEvent) {
                if (!armed) return
                if (!dragging) {
                    val distance = kotlin.math.abs(e.xOnScreen - startX) + kotlin.math.abs(e.yOnScreen - startY)
                    if (distance < DRAG_THRESHOLD) return
                    dragging = true
                    callbacks.onDragStart(this@AgentPane)
                }
                callbacks.onDragTo(this@AgentPane, e)
            }

            override fun mouseReleased(e: MouseEvent) {
                armed = false
                if (dragging) callbacks.onDragFinish(this@AgentPane)
                dragging = false
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) callbacks.onToggleMaximize(this@AgentPane)
            }
        }
        for (handle in listOf<JComponent>(header, title, glyph, nameLabel, activityDot)) {
            handle.addMouseListener(dragHandler)
            handle.addMouseMotionListener(dragHandler)
        }
        return header
    }

    private class HeaderButton(
        icon: Icon? = null,
        glyphText: String? = null,
        tooltip: String,
        private val onClick: () -> Unit,
    ) : JBLabel() {

        private val tinted = glyphText != null
        private var hovered = false

        var dimmed: Boolean = false
            set(value) {
                field = value
                refreshColors()
            }

        var glyph: String
            get() = text
            set(value) { text = value }

        init {
            icon?.let { this.icon = it }
            glyphText?.let { text = it }
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition = SwingConstants.CENTER
            toolTipText = tooltip
            isOpaque = false
            border = JBUI.Borders.empty()
            val side = JBUI.scale(ACTION_SIZE)
            preferredSize = Dimension(side, side)
            minimumSize = preferredSize
            maximumSize = preferredSize
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            refreshColors()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onClick()

                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    if (tinted) foreground = MTermColors.text
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    refreshColors()
                    repaint()
                }
            })
        }

        fun refreshColors() {
            if (!tinted) return
            foreground = if (dimmed) MTermColors.disabled else MTermColors.muted
        }

        override fun paintComponent(g: Graphics) {
            if (hovered) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = MTermColors.buttonHoverBackground
                    val arc = JBUI.scale(6)
                    g2.fillRoundRect(0, 0, width, height, arc, arc)
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }
    }

    private companion object {
        const val DRAG_THRESHOLD = 5
        const val HEADER_HEIGHT = 34
        const val HEADER_PADDING_LEFT = 14
        const val HEADER_PADDING_RIGHT = 12
        const val ACTION_GAP = 4
        const val ACTION_SIZE = 22
        const val ISLAND_CONTENT_PADDING = 10
        const val FLAT_CONTENT_PADDING = 8
        const val MAX_TITLE = 48
        const val BROADCAST_ON = "◉"
        const val BROADCAST_OFF = "○"
    }
}
