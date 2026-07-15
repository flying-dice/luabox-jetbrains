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

/**
 * A discovered rock from `luabox search --format json` (luarocks.org, the
 * registry — an anonymous read, so results carry no GitHub metadata). [latest]
 * is `null` when the rock has no numeric-versioned release; [description] is
 * always `null` for now — the luarocks.org manifest a listing reads carries no
 * per-rock description (see the CLI's `search_cmd.rs`).
 */
data class PackageResult(
    val name: String,
    val latest: String?,
    val versions: Int,
    val description: String?,
)

/** An installed dependency from `luabox outdated --format json`. */
data class Dependency(
    val name: String,
    /** `git` | `path` | `workspace` | `registry` | `url`. */
    val kind: String,
    val repo: String?,
    val url: String?,
    val current: String?,
    val latest: String?,
    val outdated: Boolean,
) {
    /**
     * Whether `luabox update [name]` can move this dependency's pin: a `git`
     * dep re-pins a tag to its GitHub repo's latest release, a `registry` dep
     * re-resolves to the highest luarocks.org version satisfying its
     * constraint. `path`/`workspace` deps have nothing to move, and a `url`
     * dep is pinned by sha256 — immutable content, never outdated or
     * updatable (see the CLI's `deps_cmd.rs` `update`/`outdated_cmd.rs`).
     */
    val isUpdatable: Boolean get() = kind == "git" || kind == "registry"
}

/**
 * Auth state from `luabox whoami --format json`:
 * `{"login":"<user>|null","source":"keychain|env|null","luarocks":bool}`.
 * [login] null means not signed in to GitHub; [source] tells us how the CLI is
 * authenticating — `keychain` (the device-flow login the plugin drives), `env`
 * (a `LUABOX_GITHUB_TOKEN` PAT override), or null. GitHub auth only matters for
 * **git-source** dependency operations (`outdated`/`update`'s release
 * probing) — luarocks.org registry search/install are always anonymous.
 * [luarocks] is additive: whether a luarocks.org API key is configured for
 * `luabox publish` (`luabox login --luarocks`); the plugin doesn't drive that
 * flow yet, but surfaces it for forward compatibility.
 */
data class WhoAmI(val login: String?, val source: String?, val luarocks: Boolean) {
    val signedIn: Boolean get() = login != null
    val viaTokenOverride: Boolean get() = source == "env"
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

    /** `luabox search [QUERY] --format json`. Empty query lists the registry (capped). */
    fun search(project: Project, query: String): List<PackageResult> {
        val args = mutableListOf("search")
        if (query.isNotBlank()) args += query.trim()
        args += listOf("--format", "json")
        // Anonymous luarocks.org read — never rate-limit-classified.
        val json = runJson(project, args)
        val results = json.getAsJsonArray("results") ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            PackageResult(
                name = name,
                latest = o.str("latest"),
                versions = o.int("versions"),
                description = o.str("description"),
            )
        }
    }

    /**
     * `luabox outdated --format json`. Git-source deps make GitHub release
     * calls, so a 403/rate-limit failure here is classified [LuaboxCliException.isRateLimited].
     */
    fun outdated(project: Project): List<Dependency> {
        val json = runJson(project, listOf("outdated", "--format", "json"), rateLimitAware = true)
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

    /**
     * `luabox add <name>[@<versionReq>]` — a bare luarocks.org registry add;
     * the CLI edits the project's rockspec and installs. An absent/blank
     * [versionReq] lets the CLI resolve the highest available version. Anonymous
     * (registry reads/edits never touch GitHub) — never rate-limit-classified.
     */
    fun add(project: Project, name: String, versionReq: String? = null) {
        val spec = if (versionReq.isNullOrBlank()) name else "$name@$versionReq"
        run(project, listOf("add", spec))
    }

    /** `luabox remove <name>` — drops it from wherever it's declared (rockspec or `luabox.toml`). */
    fun remove(project: Project, name: String) {
        run(project, listOf("remove", name))
    }

    /**
     * `luabox update [name]` — re-pins a tag-pinned git dep to its GitHub
     * repo's latest release and/or re-resolves `name` to the highest
     * satisfying version. A git-source operation: 403/rate-limit failures are
     * classified [LuaboxCliException.isRateLimited].
     */
    fun update(project: Project, name: String) {
        run(project, listOf("update", name), rateLimitAware = true)
    }

    /**
     * `luabox whoami --format json` — who the CLI is authenticated to GitHub
     * as (and how), plus whether a luarocks.org API key is configured. On
     * older CLIs without the subcommand (or when not signed in and the CLI
     * exits non-zero) this throws [LuaboxCliException]; the caller treats that as
     * "not signed in" rather than surfacing an error.
     */
    fun whoami(project: Project): WhoAmI {
        val json = runJson(project, listOf("whoami", "--format", "json"))
        return WhoAmI(login = json.str("login"), source = json.str("source"), luarocks = json.bool("luarocks"))
    }

    /** `luabox logout` — clears the CLI keychain. Idempotent. */
    fun logout(project: Project) {
        run(project, listOf("logout"))
    }

    // --- internals ---------------------------------------------------------

    private fun runJson(project: Project, args: List<String>, rateLimitAware: Boolean = false): JsonObject {
        val out = run(project, args, rateLimitAware)
        return try {
            JsonParser.parseString(out).asJsonObject
        } catch (e: Exception) {
            throw LuaboxCliException(
                "luabox returned output that wasn't the expected JSON:\n" + out.take(500),
            )
        }
    }

    /**
     * @param rateLimitAware whether a 403/"rate limit" failure should be
     *   classified [LuaboxCliException.isRateLimited]. Registry (luarocks.org)
     *   operations are anonymous and never hit GitHub, so this is `false` by
     *   default; only git-source operations (`outdated`, `update`) opt in.
     */
    private fun run(project: Project, args: List<String>, rateLimitAware: Boolean = false): String {
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
            val rateLimited = rateLimitAware &&
                (err.contains("403") || err.contains("rate limit", ignoreCase = true))
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
}
