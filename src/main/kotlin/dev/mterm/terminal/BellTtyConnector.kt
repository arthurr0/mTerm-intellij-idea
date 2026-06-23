package dev.mterm.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector

class BellTtyConnector(
    private val delegate: TtyConnector,
    private val onActivity: (hadBell: Boolean) -> Unit,
    private val onTitle: (String) -> Unit,
) : TtyConnector {

    private var state = NORMAL
    private val osc = StringBuilder()
    private var oscSawEsc = false
    private var lastTitle: String? = null

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val read = delegate.read(buf, offset, length)
        if (read > 0) {
            var hadBell = false
            val end = offset + read
            var i = offset
            while (i < end) {
                val code = buf[i].code
                when (state) {
                    NORMAL -> when (code) {
                        ESC -> state = AFTER_ESC
                        BEL -> hadBell = true
                    }

                    AFTER_ESC -> {
                        if (code == RIGHT_BRACKET) {
                            state = IN_OSC
                            osc.setLength(0)
                            oscSawEsc = false
                        } else {
                            state = NORMAL
                        }
                    }

                    IN_OSC -> when {
                        oscSawEsc -> {
                            if (code == BACKSLASH) finishOsc()
                            state = NORMAL
                        }
                        code == BEL -> {
                            finishOsc()
                            state = NORMAL
                        }
                        code == ESC -> oscSawEsc = true
                        else -> if (osc.length < MAX_OSC) osc.append(buf[i])
                    }
                }
                i++
            }
            onActivity(hadBell)
        }
        return read
    }

    private fun finishOsc() {
        val content = osc.toString()
        osc.setLength(0)
        val separator = content.indexOf(';')
        if (separator < 0) return
        val command = content.substring(0, separator)
        val text = content.substring(separator + 1)
        if ((command == "0" || command == "2") && text.isNotBlank() && text != lastTitle) {
            lastTitle = text
            onTitle(text)
        }
    }

    override fun write(bytes: ByteArray) = delegate.write(bytes)

    override fun write(string: String) = delegate.write(string)

    override fun isConnected(): Boolean = delegate.isConnected

    override fun waitFor(): Int = delegate.waitFor()

    override fun ready(): Boolean = delegate.ready()

    override fun getName(): String = delegate.name

    override fun close() = delegate.close()

    override fun resize(termSize: TermSize) = delegate.resize(termSize)

    private companion object {
        const val NORMAL = 0
        const val AFTER_ESC = 1
        const val IN_OSC = 2

        const val BEL = 7
        const val ESC = 27
        const val RIGHT_BRACKET = 93
        const val BACKSLASH = 92
        const val MAX_OSC = 512
    }
}
