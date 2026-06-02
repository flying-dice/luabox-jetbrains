package dev.pseudoscript.notification

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import dev.pseudoscript.settings.PdsBinary
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows a warning banner atop `.pds` source files and `pds.toml` manifests when
 * the `pds` binary can't be found. Highlighting still works; the banner explains
 * why diagnostics, diagrams, and docs don't, and links to the fix.
 *
 * The banner re-evaluates whenever editor notifications are refreshed — the
 * settings page calls [com.intellij.ui.EditorNotifications.updateAll] on Apply,
 * so saving a valid path clears it without reopening files.
 */
class PdsMissingBinaryEditorNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!appliesTo(file) || PdsBinary.isAvailable()) return null
        return Function { _ -> createPanel(project) }
    }

    private fun appliesTo(file: VirtualFile): Boolean =
        !file.isDirectory && (file.extension == "pds" || file.name == "pds.toml")

    private fun createPanel(project: Project): EditorNotificationPanel =
        EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
            text = PdsBinary.notFoundMessage()
            createActionLabel("Configure path…") { PdsBinary.openSettings(project) }
            createActionLabel("Install instructions") { BrowserUtil.browse(PdsBinary.INSTALL_URL) }
        }
}
