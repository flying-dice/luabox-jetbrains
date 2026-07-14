package com.luabox.packages

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.luabox.settings.LuaboxBinary
import com.luabox.settings.LuaboxSettings
import java.io.File

/** A discovered package from `luabox search --format json`. */
data class PackageResult(
    val name: String,
    val repo: String,
    val url: String,
    val description: String?,
    val stars: Int,
    val latest: String?,
    val topics: List<String>,
)

/** An installed dependency from `luabox outdated --format json`. */
data class Dependency(
    val name: String,
    val kind: String,
    val repo: String?,
    val url: String?,
    val current: String?,
    val latest: String?,
    val outdated: Boolean,
) {
    /** git deps are the only ones we can update/remote-resolve; others are read-only. */
    val isGit: Boolean get() = kind == "git"
}

/**
 * Failure from a `luabox` invocation, carrying enough context for the UI to pick
 * the right message: a rate-limit hint (offer the token setting) or a
 * binary-missing hint (offer settings + install link).
 */
class LuaboxCliException(
    message: String,
    val isRateLimited: Boolean = false,
    val binaryMissing: Boolean = false,
) : Exception(message)

/**
 * Thin wrapper over the `luabox` CLI — the single place the plugin shells out for
 * package management. Every call resolves the binary exactly the way the LSP does
 * ([LuaboxBinary.resolve]), runs in the project base dir, injects the configured
 * GitHub token as `LUABOX_GITHUB_TOKEN`, and is bounded by a timeout so a hung
 * CLI can never wedge the UI. The CLI owns all GitHub/TOML logic; we only parse
 * its JSON.
 *
 * Never call these on the EDT — they block on a child process.
 */
object LuaboxCli {
    private const val TIMEOUT_MS = 60_000

    /** Whether the project has a `luabox.toml` at its root. */
    fun hasManifest(project: Project): Boolean {
        val base = project.basePath ?: return false
        return File(base, "luabox.toml").isFile
    }

    /** `luabox search [QUERY] --format json`. Empty query lists all topic:luabox packages. */
    fun search(project: Project, query: String): List<PackageResult> {
        val args = mutableListOf("search")
        if (query.isNotBlank()) args += query.trim()
        args += listOf("--format", "json")
        val json = runJson(project, args)
        val results = json.getAsJsonArray("results") ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            PackageResult(
                name = name,
                repo = o.str("repo") ?: "",
                url = o.str("url") ?: "",
                description = o.str("description"),
                stars = o.int("stars"),
                latest = o.str("latest"),
                topics = o.strList("topics"),
            )
        }
    }

    /** `luabox outdated --format json`. */
    fun outdated(project: Project): List<Dependency> {
        val json = runJson(project, listOf("outdated", "--format", "json"))
        val deps = json.getAsJsonArray("dependencies") ?: return emptyList()
        return deps.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            Dependency(
                name = name,
                kind = o.str("kind") ?: "",
                repo = o.str("repo"),
                url = o.str("url"),
                current = o.str("current"),
                latest = o.str("latest"),
                outdated = o.bool("outdated"),
            )
        }
    }

    /** `luabox add <name> --git <url> --tag <tag>`. */
    fun add(project: Project, name: String, gitUrl: String, tag: String) {
        run(project, listOf("add", name, "--git", gitUrl, "--tag", tag))
    }

    /** `luabox remove <name>`. */
    fun remove(project: Project, name: String) {
        run(project, listOf("remove", name))
    }

    /** `luabox update <name>` — re-pins to the latest tag. */
    fun update(project: Project, name: String) {
        run(project, listOf("update", name))
    }

    // --- internals ---------------------------------------------------------

    private fun runJson(project: Project, args: List<String>): JsonObject {
        val out = run(project, args)
        return try {
            JsonParser.parseString(out).asJsonObject
        } catch (e: Exception) {
            throw LuaboxCliException(
                "luabox returned output that wasn't the expected JSON:\n" + out.take(500),
            )
        }
    }

    private fun run(project: Project, args: List<String>): String {
        if (!LuaboxBinary.isAvailable()) {
            throw LuaboxCliException(LuaboxBinary.notFoundMessage(), binaryMissing = true)
        }
        val command = ArrayList<String>(args.size + 1)
        command += LuaboxBinary.resolve()
        command += args
        val cmd = GeneralCommandLine(command).withCharset(Charsets.UTF_8)
        project.basePath?.let { cmd.withWorkDirectory(it) }
        LuaboxSettings.getInstance().githubToken
            .takeIf { it.isNotBlank() }
            ?.let { cmd.withEnvironment("LUABOX_GITHUB_TOKEN", it) }

        val output = try {
            CapturingProcessHandler(cmd).runProcess(TIMEOUT_MS)
        } catch (e: ExecutionException) {
            throw LuaboxCliException(
                "Couldn't start luabox: ${e.message}",
                binaryMissing = true,
            )
        }

        if (output.isTimeout) {
            throw LuaboxCliException(
                "luabox timed out after ${TIMEOUT_MS / 1000}s: ${cmd.commandLineString}",
            )
        }
        if (output.exitCode != 0) {
            val err = (output.stderr.trim() + "\n" + output.stdout.trim()).trim()
            val rateLimited = err.contains("403") ||
                err.contains("rate limit", ignoreCase = true)
            throw LuaboxCliException(
                "luabox ${args.firstOrNull().orEmpty()} failed (exit ${output.exitCode}):\n" +
                    err.take(500),
                isRateLimited = rateLimited,
            )
        }
        return output.stdout
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.int(key: String): Int =
        get(key)?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull() ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { !it.isJsonNull }?.runCatching { asBoolean }?.getOrNull() ?: false

    private fun JsonObject.strList(key: String): List<String> {
        val el = get(key) ?: return emptyList()
        if (!el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString }
    }
}
