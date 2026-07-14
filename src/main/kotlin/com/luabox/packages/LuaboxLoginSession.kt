package com.luabox.packages

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.luabox.settings.LuaboxBinary
import java.nio.charset.StandardCharsets

/** The `prompt` event from `luabox login --format json`'s first NDJSON line. */
data class LoginPrompt(
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresIn: Int,
)

/**
 * Callbacks for the streaming `luabox login` flow. Every method fires on the EDT,
 * so implementations may touch Swing/notifications directly. Exactly one terminal
 * callback ([onSuccess] or [onError]) fires per session; [onPrompt] fires once
 * before it.
 */
interface LoginListener {
    fun onPrompt(prompt: LoginPrompt)
    fun onSuccess(login: String)

    /** [cliTooOld] is true when the installed luabox predates the `login` subcommand. */
    fun onError(message: String, cliTooOld: Boolean)
}

/**
 * Drives `luabox login --format json` — a GitHub device-flow sign-in that streams
 * newline-delimited JSON to stdout:
 *  1. a `prompt` event (user code + verification URI), then it blocks polling
 *     GitHub until
 *  2. exactly one terminal `success` / `error` line (and a non-zero exit on error).
 *
 * Reads stdout line by line off the EDT via an [OSProcessHandler] (its reader
 * thread), and deliberately applies NO timeout — the device flow legitimately
 * blocks until the user authorizes or the code expires (~15 min). Keychain
 * fallback warnings on stderr are ignored for parsing. [cancel] destroys the
 * process. No `LUABOX_GITHUB_TOKEN` is passed: the CLI stores the resulting token
 * in its own OS keychain, so nothing sensitive is handled or logged here.
 */
class LuaboxLoginSession(
    private val project: Project,
    private val listener: LoginListener,
) {
    private val log = Logger.getInstance(LuaboxLoginSession::class.java)

    @Volatile private var handler: OSProcessHandler? = null
    @Volatile private var terminalSeen = false

    // Touched only from the process reader thread (onTextAvailable is serial).
    private val stdoutBuffer = StringBuilder()
    private val stderrBuffer = StringBuilder()

    /** Start the login process. Call OFF the EDT — spawning can block or throw. */
    fun start() {
        if (!LuaboxBinary.isAvailable()) {
            edt { listener.onError(LuaboxBinary.notFoundMessage(), false) }
            return
        }
        val cmd = GeneralCommandLine(LuaboxBinary.resolve(), "login", "--format", "json")
            .withCharset(StandardCharsets.UTF_8)
        project.basePath?.let { cmd.withWorkDirectory(it) }

        val h = try {
            OSProcessHandler(cmd)
        } catch (e: ExecutionException) {
            edt { listener.onError("Couldn't start luabox login: ${e.message}", false) }
            return
        }
        handler = h
        h.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when (outputType) {
                    ProcessOutputTypes.STDOUT -> appendStdout(event.text)
                    ProcessOutputTypes.STDERR -> stderrBuffer.append(event.text)
                }
            }

            override fun processTerminated(event: ProcessEvent) = onTerminated(event.exitCode)
        })
        h.startNotify()
    }

    /** Destroy the login process (user cancelled). Safe to call more than once. */
    fun cancel() {
        // Suppress the terminal-error path: a user cancel is not an error.
        terminalSeen = true
        handler?.destroyProcess()
    }

    // --- stdout line framing ----------------------------------------------

    private fun appendStdout(text: String) {
        stdoutBuffer.append(text)
        while (true) {
            val nl = stdoutBuffer.indexOf("\n")
            if (nl < 0) break
            val line = stdoutBuffer.substring(0, nl).trim()
            stdoutBuffer.delete(0, nl + 1)
            if (line.isNotEmpty()) handleLine(line)
        }
    }

    private fun handleLine(line: String) {
        val obj = try {
            JsonParser.parseString(line).asJsonObject
        } catch (e: Exception) {
            log.debug("luabox login: ignoring non-JSON stdout line: $line")
            return
        }
        when (obj.str("event")) {
            "prompt" -> {
                val prompt = LoginPrompt(
                    userCode = obj.str("user_code").orEmpty(),
                    verificationUri = obj.str("verification_uri").orEmpty(),
                    verificationUriComplete = obj.str("verification_uri_complete"),
                    expiresIn = obj.intOrZero("expires_in"),
                )
                edt { listener.onPrompt(prompt) }
            }

            "success" -> {
                terminalSeen = true
                val login = obj.str("login").orEmpty()
                edt { listener.onSuccess(login) }
            }

            "error" -> {
                terminalSeen = true
                val msg = obj.str("message") ?: "GitHub sign-in failed."
                edt { listener.onError(msg, false) }
            }
        }
    }

    private fun onTerminated(exitCode: Int) {
        // A terminal event (success/error) or a user cancel already resolved this.
        if (terminalSeen) return
        val err = stderrBuffer.toString().trim()
        val cliTooOld = err.contains("unrecognized subcommand", ignoreCase = true) ||
            err.contains("unexpected argument", ignoreCase = true) ||
            err.contains("no such subcommand", ignoreCase = true)
        val message = if (cliTooOld) {
            "This luabox is too old for GitHub sign-in (needs the `login` command)."
        } else {
            buildString {
                append("luabox login exited (code ")
                append(exitCode)
                append(") without completing sign-in.")
                if (err.isNotEmpty()) append("\n").append(err.take(300))
            }
        }
        edt { listener.onError(message, cliTooOld) }
    }

    // --- helpers -----------------------------------------------------------

    private fun edt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) block()
        }
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.intOrZero(key: String): Int =
        get(key)?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull() ?: 0
}
