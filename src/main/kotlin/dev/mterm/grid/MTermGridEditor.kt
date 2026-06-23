package dev.mterm.grid

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import dev.mterm.MTermService
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class MTermGridEditor(
    project: Project,
    private val file: MTermGridFile,
) : UserDataHolderBase(), FileEditor {

    private val panel = MTermService.getInstance(project).gridPanel()

    override fun getComponent(): JComponent = panel.component

    override fun getPreferredFocusedComponent(): JComponent? = panel.preferredFocusComponent()

    override fun getName(): String = "mTerm"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = file

    override fun dispose() {}
}
