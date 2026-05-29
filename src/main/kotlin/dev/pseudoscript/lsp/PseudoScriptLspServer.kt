package dev.pseudoscript.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import dev.pseudoscript.settings.PseudoScriptSettings

/**
 * Launches `pds lsp` (the stdio language server) as a child process.
 *
 * The binary path comes from [PseudoScriptSettings]; a bare name is resolved on
 * `PATH` by [GeneralCommandLine]. The working directory is the project root so
 * the server can locate `pds.toml` and resolve workspace FQNs (LANG.md §1).
 */
class PseudoScriptLspServer(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        val pds = PseudoScriptSettings.getInstance().pdsPath
        val commandLine = GeneralCommandLine(pds, "lsp")
            .withCharset(Charsets.UTF_8)
        project.basePath?.let { commandLine.withWorkDirectory(it) }
        super.setCommandLine(commandLine)
    }
}
