package dev.pseudoscript.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings > Languages & Frameworks > PseudoScript. */
class PseudoScriptConfigurable : Configurable {
    private val settings = PseudoScriptSettings.getInstance()
    private var pathField: TextFieldWithBrowseButton? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "PseudoScript"

    override fun createComponent(): JComponent {
        val field = TextFieldWithBrowseButton().apply {
            text = settings.pdsPath
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.singleFile()
                    .withTitle("Select the pds Binary"),
            )
        }
        pathField = field
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Path to the pds binary:"), field, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean = effectivePath() != settings.pdsPath

    override fun apply() {
        settings.pdsPath = effectivePath()
        // The missing-binary banner is keyed on the configured path; refresh open
        // editors so it clears (or reappears) without reopening files.
        EditorNotifications.updateAll()
    }

    override fun reset() {
        pathField?.text = settings.pdsPath
    }

    /**
     * The field's value normalised the same way [PseudoScriptSettings.pdsPath] is —
     * a blank field means the default. Without this, clearing the field leaves the
     * dialog permanently "modified" (the getter reports the default, the field is
     * empty), so Apply could never settle.
     */
    private fun effectivePath(): String =
        pathField?.text?.trim()?.takeIf { it.isNotBlank() } ?: PseudoScriptSettings.DEFAULT_PATH

    override fun disposeUIResources() {
        pathField = null
        panel = null
    }
}
