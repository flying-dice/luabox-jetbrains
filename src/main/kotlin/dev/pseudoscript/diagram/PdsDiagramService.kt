package dev.pseudoscript.diagram

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Opens diagrams in the main editor area. Holds one reusable [DiagramVirtualFile]
 * per project so successive picks update the same tab. The structure tree calls
 * [show]; the rest is the diagram editor's job.
 */
@Service(Service.Level.PROJECT)
class PdsDiagramService(private val project: Project) {
    private val file = DiagramVirtualFile()
    private var current: DiagramTarget? = null

    /**
     * Render [target] in the (single, reused) diagram tab and focus it.
     * Re-requesting the diagram already shown just focuses the tab — no
     * re-render — so selecting and then "Open Visual" on the same node is cheap.
     */
    fun show(target: DiagramTarget) {
        if (target != current) {
            current = target
            file.show(target)
        }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    companion object {
        fun getInstance(project: Project): PdsDiagramService =
            project.getService(PdsDiagramService::class.java)
    }
}
