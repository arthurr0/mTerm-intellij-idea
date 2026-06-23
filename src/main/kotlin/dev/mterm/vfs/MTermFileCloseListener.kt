package dev.mterm.vfs

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import dev.mterm.MTermService
import dev.mterm.grid.MTermGridFile
import dev.mterm.session.MTermSessionFile

class MTermFileCloseListener : FileEditorManagerListener {

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file is MTermGridFile || file is MTermSessionFile) {
            MTermService.getInstance(source.project).scheduleRelease(file) { source.isFileOpen(file) }
        }
    }
}
