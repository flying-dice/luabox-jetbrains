package com.luabox.notification

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.luabox.settings.LuaboxBinary
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows a warning banner atop `.lua` files when the `luabox` binary can't be
 * found. Base highlighting still works; the banner explains why diagnostics,
 * hover, completion, and formatting don't, and links to the fix.
 *
 * The banner re-evaluates whenever editor notifications are refreshed — the
 * settings page calls [com.intellij.ui.EditorNotifications.updateAll] on Apply,
 * so saving a valid path clears it without reopening files.
 */
class LuaboxMissingBinaryEditorNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!appliesTo(file) || LuaboxBinary.isAvailable()) return null
        return Function { _ -> createPanel(project) }
    }

    private fun appliesTo(file: VirtualFile): Boolean =
        !file.isDirectory && file.extension == "lua"

    private fun createPanel(project: Project): EditorNotificationPanel =
        EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
            text = LuaboxBinary.notFoundMessage()
            createActionLabel("Configure path…") { LuaboxBinary.openSettings(project) }
            createActionLabel("Get luabox") { BrowserUtil.browse(LuaboxBinary.INSTALL_URL) }
        }
}
