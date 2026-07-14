package com.luabox.settings

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Single source of truth for "can we reach the `luabox` binary?", where it is,
 * and the messaging shown when we can't.
 *
 * Base lexical highlighting needs no binary; everything else (diagnostics,
 * hover, completion, formatting — all via `luabox lsp`) does. The LSP launcher
 * and the missing-binary editor banner both lean on this helper so the user
 * gets one consistent story.
 */
object LuaboxBinary {

    /** Where release binaries + install scripts live. */
    const val INSTALL_URL = "https://github.com/flying-dice/luabox/releases"

    /** The id of the settings page that configures the binary path. */
    private const val CONFIGURABLE_ID = "com.luabox.settings.LuaboxConfigurable"

    /** The luabox install scripts drop the binary here (`~/.luabox/bin`). */
    private val installDirBinary: File
        get() = File(File(System.getProperty("user.home"), ".luabox"), "bin")
            .resolve(if (SystemInfo.isWindows) "luabox.exe" else "luabox")

    /**
     * The command to launch, resolved the way the LSP process is spawned:
     * - a configured path with separators is used verbatim;
     * - a bare name is looked up on `PATH`, then in `~/.luabox/bin`;
     * - failing both, the bare name is returned so the spawn surfaces a clear
     *   "not found" (the editor banner explains it first).
     */
    fun resolve(): String {
        val configured = LuaboxSettings.getInstance().luaboxPath
        if (hasSeparators(configured)) return configured
        PathEnvironmentVariableUtil.findInPath(configured)?.let { return it.absolutePath }
        installDirBinary.takeIf { it.isFile && it.canExecute() }?.let { return it.absolutePath }
        return configured
    }

    /** Whether the configured `luabox` binary can actually be located. */
    fun isAvailable(): Boolean {
        val configured = LuaboxSettings.getInstance().luaboxPath
        return if (hasSeparators(configured)) {
            File(configured).let { it.isFile && it.canExecute() }
        } else {
            PathEnvironmentVariableUtil.findInPath(configured) != null ||
                installDirBinary.let { it.isFile && it.canExecute() }
        }
    }

    /** The headline shown when the binary can't be found, naming the path we tried. */
    fun notFoundMessage(): String {
        val path = LuaboxSettings.getInstance().luaboxPath
        return "The luabox binary wasn't found (looked for ‘$path’ on PATH and in " +
            "~/.luabox/bin). Syntax highlighting works without it, but diagnostics, " +
            "hover, completion, and formatting need it."
    }

    /** Opens Settings > Languages & Frameworks > luabox. */
    fun openSettings(project: Project?) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, CONFIGURABLE_ID)
    }

    private fun hasSeparators(path: String): Boolean =
        path.indexOfFirst { it == '/' || it == File.separatorChar } >= 0
}
