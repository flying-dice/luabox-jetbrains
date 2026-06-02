package dev.pseudoscript.diagram

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

/**
 * The editor that renders a [DiagramVirtualFile] in the main editor area. All
 * real work lives in [DiagramPanel]; this just satisfies the [FileEditor]
 * contract and forwards disposal.
 */
class DiagramFileEditor(project: Project, private val file: DiagramVirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val panel = DiagramPanel(project, file)
    private val changeSupport = PropertyChangeSupport(this)

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel

    override fun getName(): String = "Diagram"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) =
        changeSupport.addPropertyChangeListener(listener)

    override fun removePropertyChangeListener(listener: PropertyChangeListener) =
        changeSupport.removePropertyChangeListener(listener)

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() = panel.dispose()
}
