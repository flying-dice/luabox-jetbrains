package dev.pseudoscript.diagram

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import dev.pseudoscript.settings.PseudoScriptSettings
import java.nio.charset.StandardCharsets

/**
 * One node of `pds outline` — the structure-tree payload, mirroring the web
 * IDE's workspace outline. Defaults make Gson tolerant of an absent `parent`.
 */
data class PdsOutlineNode(
    val fqn: String = "",
    val name: String = "",
    val kind: String = "",
    val parent: String? = null,
    val triggered: Boolean = false,
    val module: String = "",
    val line: Int = 1,
    val col: Int = 1,
)

/** The outcome of a `pds` invocation: parsed value, or a message for the UI. */
sealed interface PdsResult<out T> {
    data class Ok<T>(val value: T) : PdsResult<T>
    data class Err(val message: String) : PdsResult<Nothing>
}

/**
 * Thin wrapper over the `pds` CLI for diagram data. Runs the configured binary
 * (PATH-resolved or absolute) with the workspace directory as the working
 * directory — exactly as the LSP launcher does, so `pds` finds `pds.toml` and
 * resolves workspace FQNs (LANG.md §1). Blocking; call off the EDT.
 */
object PdsCli {
    private val gson = Gson()
    private const val TIMEOUT_MS = 30_000

    /**
     * Every `pds.toml` workspace under [projectDir], as absolute directories
     * (`pds list` → `monorepo::discover`: skips `target`/`pds_modules`/hidden,
     * and a workspace owns its subtree). A repo may hold several side by side.
     */
    fun workspaces(projectDir: String): PdsResult<List<String>> =
        when (val raw = run(projectDir, "list", projectDir)) {
            is PdsResult.Ok ->
                PdsResult.Ok(raw.value.lineSequence().map(String::trim).filter(String::isNotEmpty).toList())
            is PdsResult.Err -> raw
        }

    /** The workspace's symbol outline, for the structure tree. */
    fun outline(workspaceDir: String): PdsResult<List<PdsOutlineNode>> =
        when (val raw = run(workspaceDir, "outline")) {
            is PdsResult.Ok -> try {
                PdsResult.Ok(gson.fromJson(raw.value, Array<PdsOutlineNode>::class.java).toList())
            } catch (e: JsonSyntaxException) {
                PdsResult.Err("could not parse `pds outline` output: ${e.message}")
            }
            is PdsResult.Err -> raw
        }

    /** The fitting diagram for a symbol (C4 sub-view, or a flow's sequence), in `theme`. */
    fun symbolSvg(workspaceDir: String, fqn: String, theme: String): PdsResult<String> =
        run(workspaceDir, "svg", "--symbol", fqn, "--theme", theme)

    /** The whole-workspace context diagram (no symbol selected), in `theme`. */
    fun contextSvg(workspaceDir: String, theme: String): PdsResult<String> =
        run(workspaceDir, "svg", "--view", "context", "--theme", theme)

    private fun run(workspaceDir: String, vararg args: String): PdsResult<String> {
        val pds = PseudoScriptSettings.getInstance().pdsPath
        val cmd = GeneralCommandLine(pds)
            .withParameters(*args)
            .withWorkDirectory(workspaceDir)
            .withCharset(StandardCharsets.UTF_8)
        return try {
            val output = CapturingProcessHandler(cmd).runProcess(TIMEOUT_MS)
            when {
                output.isTimeout -> PdsResult.Err("`pds ${args.joinToString(" ")}` timed out")
                output.exitCode != 0 ->
                    PdsResult.Err(output.stderr.trim().ifBlank { "pds exited ${output.exitCode}" })
                else -> PdsResult.Ok(output.stdout)
            }
        } catch (e: Exception) {
            PdsResult.Err(
                "could not run `$pds`: ${e.message}\n" +
                    "Set the pds binary path in Settings > Languages & Frameworks > PseudoScript.",
            )
        }
    }
}
