package dev.pseudoscript.actions

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import dev.pseudoscript.settings.PseudoScriptSettings

/** The `pds.toml` filename the menu attaches to (LANG.md §1 project root). */
private const val PDS_TOML = "pds.toml"

/** True when `file` is a `pds.toml` project root, not a directory. */
private fun isPdsToml(file: VirtualFile?): Boolean =
    file != null && !file.isDirectory && file.name == PDS_TOML

/** Show/enable the action only when the context file is a `pds.toml`. */
private fun AnActionEvent.gateOnPdsToml() {
    presentation.isEnabledAndVisible = isPdsToml(getData(CommonDataKeys.VIRTUAL_FILE))
}

/**
 * The "PseudoScript" submenu on a `pds.toml` context menu. Hidden everywhere
 * else; its child actions inherit that visibility.
 */
class PdsTomlActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) = e.gateOnPdsToml()
}

/**
 * Runs `pds doc <workspace> [extraArgs]` for the selected `pds.toml`, streaming
 * output to a console tool window with a stop button (which kills the process —
 * the way to stop `--serve`).
 */
sealed class PdsDocAction(private val extraArgs: List<String>, private val consoleTitle: String) :
    AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) = e.gateOnPdsToml()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val workDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.parent?.path ?: return
        val pds = PseudoScriptSettings.getInstance().pdsPath

        val commandLine = GeneralCommandLine(buildList {
            add(pds)
            add("doc")
            add(workDir)
            addAll(extraArgs)
        }).withWorkDirectory(workDir).withCharset(Charsets.UTF_8)

        val handler = try {
            OSProcessHandler(commandLine)
        } catch (ex: ExecutionException) {
            Messages.showErrorDialog(
                project,
                "Could not run `$pds`: ${ex.message}\n\n" +
                    "Set the pds binary path in Settings > Languages & Frameworks > PseudoScript.",
                "PseudoScript",
            )
            return
        }

        // run() attaches the console to the handler and calls startNotify() itself;
        // a second startNotify() here throws "startNotify called already".
        RunContentExecutor(project, handler)
            .withTitle(consoleTitle)
            .withActivateToolWindow(true)
            .run()
    }
}

/** `pds doc <workspace>` — generate the documentation site once. */
class BuildDocsAction : PdsDocAction(emptyList(), "pds doc")

/** `pds doc <workspace> --serve` — generate, then serve over HTTP until stopped. */
class ServeDocsAction : PdsDocAction(listOf("--serve"), "pds doc --serve")
