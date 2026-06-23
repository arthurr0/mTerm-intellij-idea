package dev.mterm.session

import com.intellij.openapi.fileTypes.FileType
import dev.mterm.ui.MTermIcons
import javax.swing.Icon

object MTermSessionFileType : FileType {
    override fun getName(): String = "MTermSession"

    override fun getDescription(): String = "mTerm terminal session"

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = MTermIcons.Tab

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true
}
