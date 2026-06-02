package dev.pseudoscript.diagram

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Binds the diagram editor to [DiagramVirtualFile]s. Registered in `plugin.xml`
 * under `com.intellij.fileEditorProvider`. Hides the default text editor so the
 * synthetic diagram file opens straight into the canvas.
 */
class DiagramFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is DiagramVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        DiagramFileEditor(project, file as DiagramVirtualFile)

    override fun getEditorTypeId(): String = "pseudoscript-diagram"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
