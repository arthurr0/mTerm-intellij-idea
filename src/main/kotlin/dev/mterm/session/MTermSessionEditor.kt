package dev.mterm.session

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import dev.mterm.MTermService
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class MTermSessionEditor(
    project: Project,
    private val file: MTermSessionFile,
) : UserDataHolderBase(), FileEditor {

    private val view = MTermService.getInstance(project).sessionView(file)

    override fun getComponent(): JComponent = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.preferredFocusComponent()

    override fun getName(): String = file.agent.displayName

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = file

    override fun dispose() {}
}
