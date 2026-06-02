package dev.pseudoscript.diagram

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
 * The "PseudoScript → Open Diagram" context-menu action shown on `.pds` files in
 * the editor, editor tabs, and project view. Renders the diagram for the symbol
 * under the caret (matched to its declaration line) in the main editor area;
 * when the caret is not on a declaration — or there is no caret, as in the
 * project view — it falls back to the workspace's whole-model context view.
 */
class OpenDiagramAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && !file.isDirectory && file.extension == "pds"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val workspaceDir = workspaceRootOf(file) ?: return

        // Read the caret context now (EDT); resolution happens off it.
        val editor = e.getData(CommonDataKeys.EDITOR)
        val caretLine = editor?.caretModel?.logicalPosition?.line
        val lineText = if (editor != null && caretLine != null) lineText(editor, caretLine) else null
        val declarationLine = caretLine?.plus(1) // outline lines are 1-based

        ApplicationManager.getApplication().executeOnPooledThread {
            val outline = PdsCli.outline(workspaceDir)
            val target = resolveTarget(workspaceDir, outline, declarationLine, lineText)
            ApplicationManager.getApplication().invokeLater({
                PdsDiagramService.getInstance(project).show(target)
            }, ModalityState.any())
        }
    }

    /**
     * The diagram to show: the node declared on [declarationLine] (disambiguated
     * by the caret line's text when several modules share a line number), else
     * the workspace context view.
     */
    private fun resolveTarget(
        workspaceDir: String,
        outline: PdsResult<List<PdsOutlineNode>>,
        declarationLine: Int?,
        lineText: String?,
    ): DiagramTarget {
        val nodes = (outline as? PdsResult.Ok)?.value.orEmpty()
        val onLine = nodes.filter { it.line == declarationLine }
        val node = when {
            onLine.isEmpty() -> null
            onLine.size == 1 -> onLine.first()
            else -> onLine.firstOrNull { lineText != null && lineText.contains(it.name) } ?: onLine.first()
        }
        return if (node != null) DiagramTarget(workspaceDir, node.fqn, node.fqn)
        else DiagramTarget(workspaceDir, null, "Context overview")
    }

    private fun lineText(editor: Editor, line: Int): String? {
        val document = editor.document
        if (line < 0 || line >= document.lineCount) return null
        return document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
    }

    /** The nearest enclosing `pds.toml` directory above [file], or `null`. */
    private fun workspaceRootOf(file: VirtualFile): String? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            val manifest = dir.findChild("pds.toml")
            if (manifest != null && !manifest.isDirectory) return dir.path
            dir = dir.parent
        }
        return null
    }
}
