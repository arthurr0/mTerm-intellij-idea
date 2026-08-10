package dev.mterm.grid

import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import dev.mterm.ui.MTermColors
import dev.mterm.ui.MTermIcons
import dev.mterm.ui.TintedIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class MTermGridToolbar(
    private val onAddAgent: (JComponent) -> Unit,
    private val onColumnsChanged: (Int?) -> Unit,
    private val onBroadcastVisibilityChanged: (Boolean) -> Unit,
    private val onBroadcastSend: (String) -> Unit,
) {

    private val brandIcon = JBLabel(MTermIcons.Tab)

    private val countLabel = JBLabel().apply {
        font = JBUI.Fonts.label().asBold()
    }

    private val activityLabel = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
    }

    private val columnsControl = SegmentedControl(
        listOf("Auto" to null, "1" to 1, "2" to 2, "3" to 3, "4" to 4),
        onColumnsChanged,
    )

    private val broadcastButton = PillButton("Broadcast", AllIcons.Actions.Lightning, PillButton.Style.TOGGLE) {
        setBroadcastVisible(!broadcastBar.isVisible)
    }

    private val addButton = PillButton("Add agent", AllIcons.General.Add, PillButton.Style.PRIMARY) {
        onAddAgent(it)
    }

    private val broadcastField = JBTextField().apply {
        emptyText.text = "Type a prompt and press Enter to send it to every marked pane"
        addActionListener { send() }
    }

    private val sendButton = PillButton("Send", AllIcons.Actions.Execute, PillButton.Style.PRIMARY) { send() }

    private val broadcastHint = JBLabel("Panes marked ◉ receive it — toggle the mark in a pane header").apply {
        font = JBUI.Fonts.smallFont()
    }

    private val broadcastBar = JPanel(BorderLayout()).apply {
        isOpaque = false
        isVisible = false
        border = JBUI.Borders.emptyTop(10)
        add(
            JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                isOpaque = false
                add(broadcastField, BorderLayout.CENTER)
                add(centered(sendButton), BorderLayout.EAST)
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(6)
                add(broadcastHint, BorderLayout.WEST)
            },
            BorderLayout.SOUTH,
        )
    }

    private val actionRow = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(buildStatusSide(), BorderLayout.WEST)
        add(buildActionSide(), BorderLayout.EAST)
    }

    val component: JPanel = JPanel(BorderLayout()).apply {
        add(actionRow, BorderLayout.CENTER)
        add(broadcastBar, BorderLayout.SOUTH)
    }

    val addAnchor: JComponent get() = addButton

    init {
        applyTheme()
    }

    private fun buildStatusSide(): JComponent {
        val row = JPanel(GridBagLayout()).apply { isOpaque = false }
        val constraints = GridBagConstraints().apply {
            gridy = 0
            anchor = GridBagConstraints.CENTER
        }
        row.add(brandIcon, constraints)
        constraints.insets = JBUI.insetsLeft(8)
        row.add(countLabel, constraints)
        constraints.insets = JBUI.insetsLeft(10)
        row.add(activityLabel, constraints)
        return row
    }

    private fun buildActionSide(): JComponent {
        val row = JPanel(GridBagLayout()).apply { isOpaque = false }
        val constraints = GridBagConstraints().apply {
            gridy = 0
            anchor = GridBagConstraints.CENTER
        }
        row.add(columnsControl, constraints)
        constraints.insets = JBUI.insetsLeft(10)
        row.add(broadcastButton, constraints)
        row.add(addButton, constraints)
        return row
    }

    private fun centered(component: JComponent): JComponent =
        JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(component, GridBagConstraints().apply { gridy = 0 })
        }

    private fun send() {
        val text = broadcastField.text
        if (text.isBlank()) return
        onBroadcastSend(text)
        broadcastField.text = ""
    }

    fun setBroadcastVisible(visible: Boolean) {
        if (broadcastBar.isVisible == visible) return
        broadcastBar.isVisible = visible
        broadcastButton.active = visible
        onBroadcastVisibilityChanged(visible)
        component.revalidate()
        component.repaint()
        if (visible) broadcastField.requestFocusInWindow()
    }

    val broadcastVisible: Boolean get() = broadcastBar.isVisible

    fun setColumns(value: Int?) = columnsControl.select(value, notify = false)

    fun setStatus(total: Int, working: Int, waiting: Int) {
        countLabel.text = if (total == 1) "1 agent" else "$total agents"
        val parts = buildList {
            if (working > 0) add(if (working == 1) "1 working" else "$working working")
            if (waiting > 0) add(if (waiting == 1) "1 waiting" else "$waiting waiting")
        }
        activityLabel.text = parts.joinToString("  ·  ")
        activityLabel.foreground = if (waiting > 0) ATTENTION_COLOR else MTermColors.muted
    }

    fun applyTheme() {
        val islands = MTermColors.islandsEnabled
        val background = if (islands) MTermColors.canvas else MTermColors.panel
        component.background = background
        component.isOpaque = true
        component.border = if (islands) {
            JBUI.Borders.empty(PADDING_Y_ISLANDS, MTermColors.TILE_GAP)
        } else {
            JBUI.Borders.merge(
                JBUI.Borders.customLine(MTermColors.border, 0, 0, 1, 0),
                JBUI.Borders.empty(PADDING_Y, MTermColors.TILE_GAP),
                true,
            )
        }
        countLabel.foreground = MTermColors.text
        broadcastHint.foreground = MTermColors.muted
        columnsControl.applyTheme()
        broadcastButton.applyTheme()
        addButton.applyTheme()
        sendButton.applyTheme()
        component.revalidate()
        component.repaint()
    }

    class PillButton(
        text: String,
        private val baseIcon: Icon?,
        private val style: Style,
        private val onClick: (PillButton) -> Unit,
    ) : JBLabel(text) {

        enum class Style { NORMAL, PRIMARY, TOGGLE }

        private var hovered = false

        var active: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                applyTheme()
            }

        init {
            isOpaque = false
            font = JBUI.Fonts.smallFont()
            iconTextGap = JBUI.scale(5)
            border = JBUI.Borders.empty(PILL_PADDING_Y, PILL_PADDING_X)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            applyTheme()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onClick(this@PillButton)

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

        fun applyTheme() {
            foreground = when {
                style == Style.PRIMARY -> contrastOn(MTermColors.accent)
                style == Style.TOGGLE && active -> MTermColors.accent
                else -> MTermColors.text
            }
            icon = tintedIcon()
            repaint()
        }

        private fun tintedIcon(): Icon? {
            val base = baseIcon ?: return null
            val tint = when {
                style == Style.PRIMARY -> foreground
                style == Style.TOGGLE && active -> MTermColors.accent
                else -> return base
            }
            return TintedIcon(base, tint)
        }

        private fun fillColor(): Color = when {
            style == Style.PRIMARY -> if (hovered) ColorUtil.brighter(MTermColors.accent, 1) else MTermColors.accent
            style == Style.TOGGLE && active -> ColorUtil.withAlpha(MTermColors.accent, if (hovered) 0.28 else 0.20)
            hovered -> MTermColors.buttonHoverBackground
            else -> MTermColors.buttonBackground
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = height
                g2.color = fillColor()
                g2.fillRoundRect(0, 0, width, height, arc, arc)
                if (style == Style.TOGGLE && active) {
                    g2.color = MTermColors.accent
                    g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
                }
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    class SegmentedControl(
        options: List<Pair<String, Int?>>,
        private val onSelect: (Int?) -> Unit,
    ) : JPanel(GridBagLayout()) {

        private val segments = mutableListOf<Segment>()

        init {
            isOpaque = false
            border = JBUI.Borders.empty(SEGMENT_INSET)
            val constraints = GridBagConstraints().apply {
                gridy = 0
                anchor = GridBagConstraints.CENTER
            }
            for ((label, value) in options) {
                val segment = Segment(label, value)
                segments.add(segment)
                add(segment, constraints)
            }
            select(null, notify = false)
        }

        fun select(value: Int?, notify: Boolean) {
            segments.forEach { it.selected = it.columns == value }
            repaint()
            if (notify) onSelect(value)
        }

        fun applyTheme() {
            segments.forEach { it.applyTheme() }
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = MTermColors.buttonBackground
                g2.fillRoundRect(0, 0, width, height, height, height)
            } finally {
                g2.dispose()
            }
        }

        private inner class Segment(text: String, val columns: Int?) :
            JBLabel(text, SwingConstants.CENTER) {

            private var hovered = false

            var selected: Boolean = false
                set(value) {
                    field = value
                    applyTheme()
                }

            init {
                isOpaque = false
                font = JBUI.Fonts.smallFont()
                border = JBUI.Borders.empty(SEGMENT_PADDING_Y, SEGMENT_PADDING_X)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = if (columns == null) "Automatic column count" else "$columns columns"
                applyTheme()
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = select(columns, notify = true)

                    override fun mouseEntered(e: MouseEvent) {
                        hovered = true
                        applyTheme()
                    }

                    override fun mouseExited(e: MouseEvent) {
                        hovered = false
                        applyTheme()
                    }
                })
            }

            fun applyTheme() {
                foreground = when {
                    selected -> MTermColors.accent
                    hovered -> MTermColors.text
                    else -> MTermColors.muted
                }
                repaint()
            }

            override fun paintComponent(g: Graphics) {
                if (selected) {
                    val g2 = g.create() as Graphics2D
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2.color = ColorUtil.withAlpha(MTermColors.accent, 0.22)
                        g2.fillRoundRect(0, 0, width, height, height, height)
                        g2.color = ColorUtil.withAlpha(MTermColors.accent, 0.55)
                        g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
                    } finally {
                        g2.dispose()
                    }
                }
                super.paintComponent(g)
            }
        }
    }

    private companion object {
        const val PADDING_Y = 7
        const val PADDING_Y_ISLANDS = 8
        const val PILL_PADDING_Y = 6
        const val PILL_PADDING_X = 14
        const val SEGMENT_INSET = 3
        const val SEGMENT_PADDING_Y = 4
        const val SEGMENT_PADDING_X = 11

        val ATTENTION_COLOR = Color(0xE0, 0x8A, 0x1E)

        fun contrastOn(background: Color): Color {
            val luminance =
                (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
            return if (luminance > 0.6) Color(0x1A, 0x1A, 0x1A) else Color.WHITE
        }
    }
}
