package dev.mterm.grid

import dev.mterm.ui.MTermColors
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class ResizableTileGrid : JPanel(null) {

    private val tiles = mutableListOf<JComponent>()
    private val dividers = mutableListOf<Divider>()
    private var cells = listOf<Cell>()
    private var columnsSetting: Int? = null
    private var colFractions = DoubleArray(0)
    private var rowFractions = DoubleArray(0)

    init {
        background = GAP
    }

    fun setColumnsSetting(value: Int?) {
        columnsSetting = value
        rebuild(preserveFractions = false)
    }

    fun setTiles(newTiles: List<JComponent>) {
        tiles.clear()
        tiles.addAll(newTiles)
        rebuild(preserveFractions = false)
    }

    fun reorderTiles(newTiles: List<JComponent>) {
        tiles.clear()
        tiles.addAll(newTiles)
        rebuild(preserveFractions = true)
    }

    private fun gridShape(): IntArray {
        val n = tiles.size
        if (n == 0) return intArrayOf(1, 1)
        val setting = columnsSetting
        var cols = if (setting != null) setting.coerceIn(1, n) else ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(n.toDouble() / cols).toInt().coerceAtLeast(1)
        if (rows == 1) cols = n
        return intArrayOf(cols, rows)
    }

    private fun computeCells(cols: Int, rows: Int): List<Cell> {
        val n = tiles.size
        if (n == 0) return emptyList()
        val lastRowStart = (rows - 1) * cols
        val lastRowCount = n - lastRowStart
        val result = ArrayList<Cell>(n)
        for (i in 0 until n) {
            if (i < lastRowStart) {
                val row = i / cols
                val col = i % cols
                val endRow = if (row == rows - 2 && col >= lastRowCount) rows - 1 else row
                result.add(Cell(i, col, row, endRow))
            } else {
                result.add(Cell(i, i - lastRowStart, rows - 1, rows - 1))
            }
        }
        return result
    }

    private fun rebuild(preserveFractions: Boolean) {
        removeAll()
        dividers.clear()
        val (cols, rows) = gridShape()
        if (!preserveFractions || colFractions.size != cols) colFractions = DoubleArray(cols) { 1.0 / cols }
        if (!preserveFractions || rowFractions.size != rows) rowFractions = DoubleArray(rows) { 1.0 / rows }
        cells = computeCells(cols, rows)

        for (cell in cells) add(tiles[cell.paneIndex])

        for (boundary in 0 until cols - 1) {
            Divider(true, boundary, 0).also { dividers.add(it); add(it) }
        }
        for (column in 0 until cols) {
            val inColumn = cells.filter { it.col == column }.sortedBy { it.startRow }
            for (k in 0 until inColumn.size - 1) {
                Divider(false, inColumn[k].endRow, column).also { dividers.add(it); add(it) }
            }
        }
        revalidate()
        repaint()
    }

    override fun doLayout() {
        if (tiles.isEmpty()) return
        val (cols, rows) = gridShape()
        if (colFractions.size != cols) colFractions = DoubleArray(cols) { 1.0 / cols }
        if (rowFractions.size != rows) rowFractions = DoubleArray(rows) { 1.0 / rows }

        val w = width
        val h = height
        val availW = max(1, w - (cols - 1) * GAP_SIZE)
        val availH = max(1, h - (rows - 1) * GAP_SIZE)

        val colX = IntArray(cols)
        val colW = IntArray(cols)
        var x = 0
        for (c in 0 until cols) {
            colX[c] = x
            colW[c] = (colFractions[c] * availW).toInt()
            x += colW[c] + GAP_SIZE
        }
        colW[cols - 1] = max(1, w - colX[cols - 1])

        val rowY = IntArray(rows)
        val rowH = IntArray(rows)
        var y = 0
        for (r in 0 until rows) {
            rowY[r] = y
            rowH[r] = (rowFractions[r] * availH).toInt()
            y += rowH[r] + GAP_SIZE
        }
        rowH[rows - 1] = max(1, h - rowY[rows - 1])

        for (cell in cells) {
            val top = rowY[cell.startRow]
            val bottom = rowY[cell.endRow] + rowH[cell.endRow]
            tiles[cell.paneIndex].setBounds(colX[cell.col], top, colW[cell.col], bottom - top)
        }

        for (d in dividers) {
            if (d.vertical) {
                d.setBounds(colX[d.boundary] + colW[d.boundary], 0, GAP_SIZE, h)
            } else {
                d.setBounds(colX[d.track], rowY[d.boundary] + rowH[d.boundary], colW[d.track], GAP_SIZE)
            }
        }
    }

    private fun adjust(vertical: Boolean, boundary: Int, deltaPx: Int, total: Int, snapshot: DoubleArray) {
        if (total <= 0 || boundary + 1 >= snapshot.size) return
        val fractions = if (vertical) colFractions else rowFractions
        if (boundary + 1 >= fractions.size) return
        val df = deltaPx.toDouble() / total
        var left = snapshot[boundary] + df
        var right = snapshot[boundary + 1] - df
        if (left < MIN_FRACTION) {
            right -= MIN_FRACTION - left
            left = MIN_FRACTION
        }
        if (right < MIN_FRACTION) {
            left -= MIN_FRACTION - right
            right = MIN_FRACTION
        }
        fractions[boundary] = left
        fractions[boundary + 1] = right
        revalidate()
        repaint()
    }

    private inner class Divider(val vertical: Boolean, val boundary: Int, val track: Int) : JPanel() {

        private var pressScreen = 0
        private var total = 0
        private var snapshot = DoubleArray(0)

        init {
            isOpaque = true
            background = GAP
            cursor = Cursor.getPredefinedCursor(
                if (vertical) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR,
            )
            val handler = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val (cols, rows) = gridShape()
                    total = if (vertical) {
                        max(1, this@ResizableTileGrid.width - (cols - 1) * GAP_SIZE)
                    } else {
                        max(1, this@ResizableTileGrid.height - (rows - 1) * GAP_SIZE)
                    }
                    pressScreen = if (vertical) e.xOnScreen else e.yOnScreen
                    snapshot = (if (vertical) colFractions else rowFractions).copyOf()
                    background = HANDLE_ACTIVE
                }

                override fun mouseDragged(e: MouseEvent) {
                    val now = if (vertical) e.xOnScreen else e.yOnScreen
                    adjust(vertical, boundary, now - pressScreen, total, snapshot)
                }

                override fun mouseReleased(e: MouseEvent) {
                    background = GAP
                    repaint()
                }

                override fun mouseEntered(e: MouseEvent) {
                    background = HANDLE_HOVER
                }

                override fun mouseExited(e: MouseEvent) {
                    background = GAP
                }
            }
            addMouseListener(handler)
            addMouseMotionListener(handler)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = LINE
            if (vertical) {
                val cx = width / 2
                g.drawLine(cx, 0, cx, height)
            } else {
                val cy = height / 2
                g.drawLine(0, cy, width, cy)
            }
        }
    }

    private class Cell(val paneIndex: Int, val col: Int, val startRow: Int, val endRow: Int)

    private companion object {
        const val GAP_SIZE = 8
        const val MIN_FRACTION = 0.08
        val GAP: Color get() = MTermColors.panel
        val LINE: Color get() = MTermColors.border
        val HANDLE_HOVER: Color get() = MTermColors.panel
        val HANDLE_ACTIVE: Color get() = MTermColors.accent
    }
}
