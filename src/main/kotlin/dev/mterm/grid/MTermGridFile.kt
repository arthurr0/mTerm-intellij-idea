package dev.mterm.grid

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import dev.mterm.vfs.MTermFileSystem

class MTermGridFile : LightVirtualFile("mTerm", MTermGridFileType, "") {

    init {
        isWritable = false
    }

    override fun getFileType(): FileType = MTermGridFileType

    override fun getFileSystem(): VirtualFileSystem = MTermFileSystem.getInstance()

    override fun getPath(): String = MTermFileSystem.GRID_PATH
}
