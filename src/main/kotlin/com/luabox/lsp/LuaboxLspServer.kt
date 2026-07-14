package com.luabox.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.luabox.settings.LuaboxBinary
import java.io.IOException

/**
 * Launches `luabox lsp` (the stdio language server) as a child process.
 *
 * The binary is resolved by [LuaboxBinary] (configured path, else `luabox` on
 * `PATH`, else `~/.luabox/bin`). The working directory is the project root so
 * the server can locate the workspace and resolve module paths.
 */
class LuaboxLspServer(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        val commandLine = GeneralCommandLine(LuaboxBinary.resolve(), "lsp")
            .withCharset(Charsets.UTF_8)
        project.basePath?.let { commandLine.withWorkDirectory(it) }
        super.setCommandLine(commandLine)
    }

    /**
     * Fail fast with an informative message when the binary is missing, instead
     * of letting the spawn surface a raw "Cannot run program" error. The editor
     * banner (see LuaboxMissingBinaryEditorNotificationProvider) is the primary,
     * persistent explanation; this keeps the LSP path from being opaque.
     */
    @Throws(IOException::class)
    override fun start() {
        if (!LuaboxBinary.isAvailable()) throw IOException(LuaboxBinary.notFoundMessage())
        super.start()
    }
}
