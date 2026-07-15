package com.luabox.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings > Languages & Frameworks > luabox. */
class LuaboxConfigurable : Configurable {
    private val settings = LuaboxSettings.getInstance()
    private var pathField: TextFieldWithBrowseButton? = null
    private var tokenField: JBPasswordField? = null
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
        val token = JBPasswordField().apply { text = settings.githubToken }
        tokenField = token
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Path to the luabox binary:"), field, 1, false)
            .addComponentToRightColumn(
                JBLabel(
                    "A bare name (default “luabox”) is resolved on PATH, then in " +
                        "~/.luabox/bin. The server is launched as “<path> lsp”.",
                ),
                1,
            )
            .addLabeledComponent(JBLabel("GitHub token override (optional, git-source dependencies only):"), token, 1, false)
            .addComponentToRightColumn(
                JBLabel(
                    "<html>Registry package search and install (luarocks.org, in the luabox " +
                        "Packages tool window) are always anonymous — this token is never used for " +
                        "them. It only affects <b>git-source</b> dependencies: it raises GitHub's " +
                        "API rate limit for <code>luabox outdated</code>/<code>luabox update</code> " +
                        "and grants access to private git repos. The normal way to provide it is " +
                        "<b>Sign in with GitHub</b> in the luabox Packages tool window (device flow; " +
                        "the token is stored in your OS keychain). This field is an <b>override</b> " +
                        "for restricted orgs or GHE: when set it is passed as LUABOX_GITHUB_TOKEN and " +
                        "takes precedence over the keychain sign-in.</html>",
                ),
                1,
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean =
        effectivePath() != settings.luaboxPath || effectiveToken() != settings.githubToken

    override fun apply() {
        settings.luaboxPath = effectivePath()
        settings.githubToken = effectiveToken()
        // The missing-binary banner is keyed on the configured path; refresh open
        // editors so it clears (or reappears) without reopening files.
        EditorNotifications.updateAll()
    }

    override fun reset() {
        pathField?.text = settings.luaboxPath
        tokenField?.text = settings.githubToken
    }

    private fun effectiveToken(): String =
        tokenField?.password?.let { String(it) }?.trim().orEmpty()

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
        tokenField = null
        panel = null
    }
}
