package com.luabox.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings > Languages & Frameworks > luabox. */
class LuaboxConfigurable : Configurable {
    private val settings = LuaboxSettings.getInstance()
    private var pathField: TextFieldWithBrowseButton? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "luabox"

    override fun createComponent(): JComponent {
        val field = TextFieldWithBrowseButton().apply {
            text = settings.luaboxPath
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.singleFile()
                    .withTitle("Select the luabox Binary"),
            )
        }
        pathField = field
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Path to the luabox binary:"), field, 1, false)
            .addComponentToRightColumn(
                JBLabel(
                    "A bare name (default “luabox”) is resolved on PATH, then in " +
                        "~/.luabox/bin. The server is launched as “<path> lsp”.",
                ),
                1,
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean = effectivePath() != settings.luaboxPath

    override fun apply() {
        settings.luaboxPath = effectivePath()
        // The missing-binary banner is keyed on the configured path; refresh open
        // editors so it clears (or reappears) without reopening files.
        EditorNotifications.updateAll()
    }

    override fun reset() {
        pathField?.text = settings.luaboxPath
    }

    /**
     * The field's value normalised the same way [LuaboxSettings.luaboxPath] is —
     * a blank field means the default. Without this, clearing the field leaves
     * the dialog permanently "modified" (the getter reports the default, the
     * field is empty), so Apply could never settle.
     */
    private fun effectivePath(): String =
        pathField?.text?.trim()?.takeIf { it.isNotBlank() } ?: LuaboxSettings.DEFAULT_PATH

    override fun disposeUIResources() {
        pathField = null
        panel = null
    }
}
