package com.luabox.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * Wires the `luabox lsp` process into LSP4IJ. Registered in `plugin.xml` under
 * the `com.redhat.devtools.lsp4ij.server` extension point, then bound to the
 * Lua language by a `languageMapping`.
 *
 * The default client is used — the server's capabilities (diagnostics, hover,
 * completion, formatting, goto-definition, semantic tokens) need no client-side
 * customisation.
 */
class LuaboxLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        LuaboxLspServer(project)
}
