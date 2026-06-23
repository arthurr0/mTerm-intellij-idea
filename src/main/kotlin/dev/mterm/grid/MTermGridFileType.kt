package dev.mterm.grid

import com.intellij.openapi.fileTypes.FileType
import dev.mterm.ui.MTermIcons
import javax.swing.Icon

object MTermGridFileType : FileType {
    override fun getName(): String = "MTermGrid"

    override fun getDescription(): String = "mTerm agents grid"

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = MTermIcons.Tab

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true
}
