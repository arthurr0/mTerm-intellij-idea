package dev.mterm.vfs

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import dev.mterm.grid.MTermGridFile

class MTermFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {

    private val console: MTermGridFile by lazy { MTermGridFile() }

    fun consoleFile(): MTermGridFile = console

    override fun getProtocol(): String = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? = if (path == GRID_PATH) console else null

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    override fun refresh(asynchronous: Boolean) {}

    override fun isReadOnly(): Boolean = true

    companion object {
        const val PROTOCOL = "mterm"
        const val GRID_PATH = "grid"

        fun getInstance(): MTermFileSystem =
            VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as MTermFileSystem
    }
}
