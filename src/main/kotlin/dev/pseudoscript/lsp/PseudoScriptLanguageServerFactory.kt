package dev.pseudoscript.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * Wires the `pds lsp` process into LSP4IJ. Registered in `plugin.xml` under the
 * `com.redhat.devtools.lsp4ij.server` extension point, then bound to the
 * PseudoScript language by a `languageMapping`.
 *
 * The default [com.redhat.devtools.lsp4ij.client.LanguageClientImpl] is used —
 * the server's capabilities (diagnostics, hover, formatting, goto-definition)
 * need no client-side customisation.
 */
class PseudoScriptLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        PseudoScriptLspServer(project)
}
