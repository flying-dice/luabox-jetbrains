package dev.pseudoscript.settings

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Single source of truth for "can we reach the `pds` binary?" and the messaging
 * shown when we can't.
 *
 * Syntax highlighting is lexer-driven and needs no binary; diagnostics (`pds
 * lsp`), diagrams (`pds svg`/`outline`/`list`), and docs (`pds doc`) all do. When
 * the binary is missing the editor banner and the LSP launcher both lean on this
 * helper so the user gets one consistent explanation.
 */
object PdsBinary {

    /** Where the install / setup instructions live. */
    const val INSTALL_URL = "https://pseudoscript.dev"

    /** The id of the settings page that configures the binary path. */
    private const val CONFIGURABLE_ID = "dev.pseudoscript.settings.PseudoScriptConfigurable"

    /**
     * Whether the configured `pds` binary can be located, resolved the same way
     * [com.intellij.execution.configurations.GeneralCommandLine] would launch it:
     * a path with separators is checked verbatim; a bare name is looked up on
     * `PATH`.
     */
    fun isAvailable(): Boolean {
        val path = PseudoScriptSettings.getInstance().pdsPath
        return if (path.indexOfFirst { it == '/' || it == File.separatorChar } >= 0) {
            File(path).let { it.isFile && it.canExecute() }
        } else {
            PathEnvironmentVariableUtil.findInPath(path) != null
        }
    }

    /** The headline shown when the binary can't be found, naming the path we tried. */
    fun notFoundMessage(): String {
        val path = PseudoScriptSettings.getInstance().pdsPath
        return "The pds binary wasn't found (looked for ‘$path’). Syntax highlighting " +
            "works without it, but diagnostics, diagrams, and docs need it."
    }

    /** Opens Settings > Languages & Frameworks > PseudoScript. */
    fun openSettings(project: Project?) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, CONFIGURABLE_ID)
    }
}
