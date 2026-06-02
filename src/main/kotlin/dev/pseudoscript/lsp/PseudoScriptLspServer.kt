package dev.pseudoscript.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import dev.pseudoscript.settings.PdsBinary
import dev.pseudoscript.settings.PseudoScriptSettings
import java.io.IOException

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

    /**
     * Fail fast with an informative message when the binary is missing, instead
     * of letting the spawn surface a raw "Cannot run program" error. The editor
     * banner (see PdsMissingBinaryEditorNotificationProvider) is the primary,
     * persistent explanation; this keeps the LSP path from being opaque.
     */
    @Throws(IOException::class)
    override fun start() {
        if (!PdsBinary.isAvailable()) throw IOException(PdsBinary.notFoundMessage())
        super.start()
    }
}
