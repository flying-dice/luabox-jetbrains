package dev.pseudoscript.diagram

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import dev.pseudoscript.settings.PseudoScriptSettings
import java.awt.BorderLayout
import java.awt.CardLayout
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The "Docs" tab: builds and serves the workspace documentation site
 * (`pds doc --watch`) and shows the live, auto-reloading result in an embedded
 * browser. Picks the workspace of the active `.pds` file (falling back to the
 * first discovered one), starts the server on a free port, waits for its
 * readiness banner, then loads the URL. Where JCEF is unavailable it opens the
 * site in the system browser instead. The server is tied to this panel's
 * lifetime — closing the tool window stops it.
 */
class PdsDocsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val cards = CardLayout()
    private val center = JPanel(cards)
    private val status = JLabel("", SwingConstants.CENTER)
    private val workspaceLabel = JBLabel()

    // The embedded browser, or null when JCEF is unsupported (→ external browser).
    private val browser: JBCefBrowser? =
        if (JBCefApp.isSupported()) JBCefBrowser.createBuilder().build() else null

    private var handler: OSProcessHandler? = null
    private var baseUrl: String? = null

    init {
        status.border = com.intellij.util.ui.JBUI.Borders.empty(16)
        center.add(JPanel(BorderLayout()).apply { add(status, BorderLayout.CENTER) }, CARD_STATUS)
        browser?.let {
            Disposer.register(this, it)
            center.add(it.component, CARD_BROWSER)
        }
        add(buildToolbar(), BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        showStatus(
            if (browser == null) {
                "Press ▶ to build & serve the docs — they'll open in your browser " +
                    "(this IDE has no embedded browser support)."
            } else {
                "Press ▶ to build & serve the docs and preview them here, live as you edit."
            },
        )
    }

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(serverAction("Build & Serve", "Build the docs and serve them with live reload", AllIcons.Actions.Execute, enabledWhenRunning = false) { serve() })
            add(serverAction("Stop", "Stop the docs server", AllIcons.Actions.Suspend, enabledWhenRunning = true) { stopServer(); showWelcome() })
            addSeparator()
            add(serverAction("Reload", "Reload the page", AllIcons.Actions.Refresh, enabledWhenRunning = true) { browser?.cefBrowser?.reload() })
            add(serverAction("Open in Browser", "Open the docs in your system browser", AllIcons.General.Web, enabledWhenRunning = true) { baseUrl?.let(BrowserUtil::browse) })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        workspaceLabel.border = com.intellij.util.ui.JBUI.Borders.empty(0, 8)
        return JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(workspaceLabel, BorderLayout.CENTER)
        }
    }

    /** An action that is enabled only while the server is (not) running. */
    private fun serverAction(
        text: String,
        description: String,
        icon: javax.swing.Icon,
        enabledWhenRunning: Boolean,
        run: () -> Unit,
    ): AnAction =
        object : AnAction(text, description, icon), com.intellij.openapi.project.DumbAware {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = (handler != null) == enabledWhenRunning
            }

            override fun actionPerformed(e: AnActionEvent) = run()
        }

    // --- server lifecycle ---------------------------------------------------

    private fun serve() {
        stopServer() // restart-safe
        showStatus("Building & serving docs…")
        // Read the active file on the EDT; resolve the workspace (which may shell
        // out to `pds list`) off it, then start the server back on the EDT.
        val active = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val base = project.basePath
        ApplicationManager.getApplication().executeOnPooledThread {
            val dir = active?.let { workspaceRootOf(it) }
                ?: base?.let { (PdsCli.workspaces(it) as? PdsResult.Ok)?.value?.firstOrNull() ?: it }
            onEdt {
                if (dir == null) {
                    showStatus("No `pds.toml` workspace found. Open a `.pds` file in a workspace and try again.")
                } else {
                    startServer(dir)
                }
            }
        }
    }

    private fun startServer(dir: String) {
        workspaceLabel.text = relativeLabel(dir)
        val port = findFreePort()
        val pds = PseudoScriptSettings.getInstance().pdsPath
        val command = GeneralCommandLine(pds, "doc", dir, "--watch", "--port", port.toString())
            .withWorkDirectory(dir)
            .withCharset(StandardCharsets.UTF_8)

        val process = try {
            OSProcessHandler(command)
        } catch (e: ExecutionException) {
            showStatus(
                "Could not run `$pds`: ${e.message}\n\n" +
                    "Set the pds binary path in Settings > Languages & Frameworks > PseudoScript.",
            )
            return
        }
        handler = process
        showStatus("Building & serving docs…")
        process.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val url = SERVE_URL.find(event.text)?.value ?: return
                onEdt { if (handler === process) onServerReady(url) }
            }

            override fun processTerminated(event: ProcessEvent) {
                onEdt { if (handler === process) onServerStopped(event.exitCode) }
            }
        })
        process.startNotify()
    }

    private fun onServerReady(url: String) {
        baseUrl = url
        val browser = browser
        if (browser != null) {
            browser.loadURL(url)
            cards.show(center, CARD_BROWSER)
        } else {
            BrowserUtil.browse(url)
            showStatus("Serving docs at $url (opened in your browser). Edit and save to live-reload.")
        }
    }

    private fun onServerStopped(exitCode: Int) {
        // Only reached on an *unexpected* exit — an explicit Stop clears `handler`
        // first, so the listener's identity guard skips this path.
        handler = null
        baseUrl = null
        showStatus("The docs server stopped (exit $exitCode). Press ▶ to try again.")
    }

    private fun stopServer() {
        handler?.destroyProcess()
        handler = null
    }

    // --- workspace resolution -----------------------------------------------

    private fun workspaceRootOf(file: VirtualFile): String? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            val manifest = dir.findChild("pds.toml")
            if (manifest != null && !manifest.isDirectory) return dir.path
            dir = dir.parent
        }
        return null
    }

    private fun relativeLabel(workspace: String): String {
        val base = project.basePath ?: return workspace
        return try {
            Path.of(base).relativize(Path.of(workspace)).toString().ifEmpty { Path.of(workspace).fileName?.toString() ?: workspace }
        } catch (e: IllegalArgumentException) {
            Path.of(workspace).fileName?.toString() ?: workspace
        }
    }

    // --- helpers ------------------------------------------------------------

    private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

    private fun onEdt(block: () -> Unit) =
        ApplicationManager.getApplication().invokeLater(block, ModalityState.any())

    private fun showStatus(message: String) {
        status.text = "<html><div style='text-align:center'>" +
            message.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") +
            "</div></html>"
        cards.show(center, CARD_STATUS)
    }

    private fun showWelcome() = showStatus("Stopped. Press ▶ to build & serve the docs again.")

    override fun dispose() {
        stopServer()
    }

    companion object {
        private const val CARD_STATUS = "status"
        private const val CARD_BROWSER = "browser"
        private const val TOOLBAR_PLACE = "PseudoScriptDocs"

        /** The readiness banner `pds doc --serve` prints once the site is up. */
        private val SERVE_URL = Regex("""http://127\.0\.0\.1:\d+/?""")
    }
}
