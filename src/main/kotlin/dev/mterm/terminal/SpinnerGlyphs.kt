package dev.mterm.terminal

internal object SpinnerGlyphs {

    private val RANGES = listOf(
        0x2800..0x28FF,
        0x2596..0x259F,
        0x25D0..0x25D3,
        0x25DC..0x25DF,
        0x25E2..0x25E5,
        0x25F0..0x25F7,
        0x1F311..0x1F318,
    )

    fun present(title: String): Boolean =
        title.codePoints().anyMatch { code -> RANGES.any { code in it } }
}
