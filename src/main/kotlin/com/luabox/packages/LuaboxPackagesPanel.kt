package com.luabox.packages

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.notification.Notification
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.luabox.settings.LuaboxBinary
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The "luabox Packages" tool window UI: an npm-like package manager backed by
 * the `luabox` CLI.
 *
 * Top: a toolbar with Refresh + an outdated-count label. Body (a vertical split):
 *  - Discover — a search field over `luabox search`, one card per result with an
 *    Install action.
 *  - Installed — one row per dependency from `luabox outdated`, with an
 *    "outdated: current → latest" indicator and Update/Remove for git deps
 *    (non-git deps are read-only).
 *
 * Every CLI call runs off the EDT via [Task.Backgroundable]; the UI is rebuilt on
 * the EDT in the task's onSuccess. Mutations and any VFS change to `luabox.toml`
 * refresh the Installed view.
 */
class LuaboxPackagesPanel(private val project: Project) :
    SimpleToolWindowPanel(true, true), Disposable {

    private val discoverContainer = JPanel(VerticalLayout(0))
    private val installedContainer = JPanel(VerticalLayout(0))
    private val searchField = SearchTextField()
    private val outdatedLabel = JBLabel()
    private var lastQuery = ""

    // --- auth bar / sign-in state -----------------------------------------
    private val authStatusLabel = JBLabel()
    private val authButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
    private val authBar = JPanel(BorderLayout())
    /** The running device-flow login, if any (so Cancel/dispose can destroy it). */
    private var loginSession: LuaboxLoginSession? = null
    /** The sticky prompt notification, held so we can expire it on completion. */
    private var promptNotification: Notification? = null

    init {
        toolbar = buildToolbar()
        setContent(buildBody())

        searchField.textEditor.addActionListener { doSearch(searchField.text) }

        // Rebuild the Installed view when luabox.toml changes on disk (e.g. the
        // CLI mutating it, or an external edit).
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { it.file?.name == "luabox.toml" }) refreshInstalled()
                }
            },
        )
    }

    // --- layout ------------------------------------------------------------

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Reload search results and installed dependencies", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = refreshAll()
            })
        }
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("LuaboxPackages", group, true)
        actionToolbar.targetComponent = this

        val actionRow = JPanel(BorderLayout())
        actionRow.add(actionToolbar.component, BorderLayout.WEST)
        outdatedLabel.border = JBUI.Borders.emptyRight(10)
        actionRow.add(outdatedLabel, BorderLayout.EAST)

        // Stack the action row above the GitHub auth bar.
        val stack = JPanel(BorderLayout())
        stack.add(actionRow, BorderLayout.NORTH)
        stack.add(buildAuthBar(), BorderLayout.SOUTH)
        return stack
    }

    private fun buildAuthBar(): JComponent {
        authBar.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(4, 8),
        )
        authStatusLabel.border = JBUI.Borders.emptyRight(8)
        authBar.add(authStatusLabel, BorderLayout.CENTER)
        authBar.add(authButtonPanel, BorderLayout.EAST)
        return authBar
    }

    private fun buildBody(): JComponent {
        val discover = JPanel(BorderLayout()).apply {
            add(sectionHeader("Discover"), BorderLayout.NORTH)
            val top = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, 4)
                add(searchField, BorderLayout.CENTER)
            }
            val body = JPanel(BorderLayout()).apply {
                add(top, BorderLayout.NORTH)
                add(JBScrollPane(discoverContainer), BorderLayout.CENTER)
            }
            add(body, BorderLayout.CENTER)
        }
        val installed = JPanel(BorderLayout()).apply {
            add(sectionHeader("Installed"), BorderLayout.NORTH)
            add(JBScrollPane(installedContainer), BorderLayout.CENTER)
        }
        val split = com.intellij.ui.JBSplitter(true, 0.5f)
        split.firstComponent = discover
        split.secondComponent = installed
        return split
    }

    private fun sectionHeader(text: String): JComponent =
        JBLabel(text).apply {
            font = JBFont.label().asBold()
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

    // --- refresh -----------------------------------------------------------

    fun refreshAll() {
        refreshAuth()
        doSearch(lastQuery)
        refreshInstalled()
    }

    private fun doSearch(query: String) {
        lastQuery = query
        if (!LuaboxBinary.isAvailable()) {
            renderBinaryMissing(discoverContainer)
            return
        }
        setMessage(discoverContainer, "Searching…")
        runBg("Searching luabox packages", { LuaboxCli.search(project, query) }) { results ->
            renderDiscover(results)
        }
    }

    private fun refreshInstalled() {
        if (!LuaboxBinary.isAvailable()) {
            renderBinaryMissing(installedContainer)
            outdatedLabel.text = ""
            return
        }
        if (!LuaboxCli.hasManifest(project)) {
            renderNoManifest()
            outdatedLabel.text = ""
            return
        }
        setMessage(installedContainer, "Loading dependencies…")
        runBg("Loading luabox dependencies", { LuaboxCli.outdated(project) }) { deps ->
            renderInstalled(deps)
        }
    }

    // --- GitHub auth -------------------------------------------------------

    /**
     * Re-query `luabox whoami` off the EDT and repaint the auth bar. Failures
     * (older CLI without the subcommand, or a not-signed-in non-zero exit) are
     * treated as "not signed in" — no error notification.
     */
    fun refreshAuth() {
        if (!LuaboxBinary.isAvailable()) {
            renderAuthSignedOut()
            return
        }
        authStatusLabel.text = "Checking sign-in…"
        authStatusLabel.foreground = JBColor.GRAY
        authButtonPanel.removeAll()
        revalidateRepaint(authBar)

        val result = arrayOfNulls<WhoAmI>(1)
        object : Task.Backgroundable(project, "Checking luabox GitHub sign-in", true) {
            override fun run(indicator: ProgressIndicator) {
                result[0] = try {
                    LuaboxCli.whoami(project)
                } catch (e: LuaboxCliException) {
                    null
                }
            }

            override fun onSuccess() {
                val who = result[0]
                if (who != null && who.signedIn) renderAuthSignedIn(who) else renderAuthSignedOut()
            }
        }.queue()
    }

    private fun renderAuthSignedIn(who: WhoAmI) {
        val login = who.login ?: return renderAuthSignedOut()
        authButtonPanel.removeAll()
        authStatusLabel.foreground = SIGNED_IN_COLOR
        if (who.viaTokenOverride) {
            // Authenticating via the LUABOX_GITHUB_TOKEN PAT override, not the
            // keychain — `luabox logout` wouldn't change this, so offer settings.
            authStatusLabel.text = "✓ Signed in as $login (token override)"
            authButtonPanel.add(
                JButton("Manage token…").apply {
                    toolTipText = "This session uses the GitHub token override in Settings"
                    addActionListener { LuaboxBinary.openSettings(project) }
                },
            )
        } else {
            authStatusLabel.text = "✓ Signed in as $login"
            authButtonPanel.add(
                JButton("Sign out").apply {
                    toolTipText = "Run luabox logout (clears the CLI's stored GitHub token)"
                    addActionListener { signOut() }
                },
            )
        }
        revalidateRepaint(authBar)
    }

    private fun renderAuthSignedOut() {
        authButtonPanel.removeAll()
        authStatusLabel.text = "Not signed in"
        authStatusLabel.foreground = JBColor.GRAY
        authButtonPanel.add(
            JButton("Sign in with GitHub").apply {
                toolTipText = "Sign in to GitHub with the device flow (opens your browser)"
                addActionListener { signIn() }
            },
        )
        revalidateRepaint(authBar)
    }

    /** Start the GitHub device-flow sign-in. Reentry cancels any in-flight login. */
    fun signIn() {
        if (!LuaboxBinary.isAvailable()) {
            LuaboxNotifications.error(
                project,
                LuaboxCliException(LuaboxBinary.notFoundMessage(), binaryMissing = true),
            )
            return
        }
        loginSession?.cancel()
        dismissPrompt()
        authStatusLabel.text = "Starting GitHub sign-in…"
        authStatusLabel.foreground = JBColor.GRAY
        authButtonPanel.removeAll()
        revalidateRepaint(authBar)

        val session = LuaboxLoginSession(
            project,
            object : LoginListener {
                override fun onPrompt(prompt: LoginPrompt) = showLoginPrompt(prompt)

                override fun onSuccess(login: String) {
                    dismissPrompt()
                    LuaboxNotifications.info(project, "Signed in to GitHub as $login.")
                    // Re-auth, and re-run search/installed now that calls authenticate.
                    refreshAll()
                }

                override fun onError(message: String, cliTooOld: Boolean) {
                    dismissPrompt()
                    if (cliTooOld) LuaboxNotifications.cliTooOldForLogin(project)
                    else LuaboxNotifications.error(project, LuaboxCliException(message))
                    refreshAuth()
                }
            },
        )
        loginSession = session
        // Spawning the child process can block; keep it off the EDT.
        ApplicationManager.getApplication().executeOnPooledThread { session.start() }
    }

    private fun showLoginPrompt(prompt: LoginPrompt) {
        dismissPrompt()
        authStatusLabel.text = "Waiting for GitHub authorization…"
        authStatusLabel.foreground = JBColor.GRAY
        val target = prompt.verificationUriComplete ?: prompt.verificationUri
        val minutes = (prompt.expiresIn / 60).coerceAtLeast(1)
        promptNotification = LuaboxNotifications.loginPrompt(
            project = project,
            userCode = prompt.userCode,
            verificationUri = prompt.verificationUri,
            expiresInMinutes = minutes,
            onCopyAndOpen = {
                CopyPasteManager.getInstance().setContents(StringSelection(prompt.userCode))
                BrowserUtil.browse(target)
            },
            onCancel = {
                loginSession?.cancel()
                dismissPrompt()
                refreshAuth()
            },
        )
    }

    private fun signOut() {
        runBg("Signing out of GitHub", { LuaboxCli.logout(project) }) {
            LuaboxNotifications.info(project, "Signed out of GitHub.")
            refreshAll()
        }
    }

    private fun dismissPrompt() {
        promptNotification?.expire()
        promptNotification = null
    }

    // --- rendering ---------------------------------------------------------

    private fun renderDiscover(results: List<PackageResult>) {
        discoverContainer.removeAll()
        if (results.isEmpty()) {
            setMessage(
                discoverContainer,
                if (lastQuery.isBlank())
                    "No luabox packages found. Type a query and press Enter to search."
                else
                    "No packages matched “$lastQuery”.",
            )
            return
        }
        results.forEach { discoverContainer.add(packageRow(it)) }
        revalidateRepaint(discoverContainer)
    }

    private fun renderInstalled(deps: List<Dependency>) {
        installedContainer.removeAll()
        val outdatedCount = deps.count { it.isGit && it.outdated }
        outdatedLabel.text = when {
            deps.isEmpty() -> ""
            outdatedCount == 0 -> "Up to date"
            else -> "$outdatedCount outdated"
        }
        if (deps.isEmpty()) {
            setMessage(installedContainer, "No dependencies yet. Install one from Discover above.")
            return
        }
        deps.forEach { installedContainer.add(dependencyRow(it)) }
        revalidateRepaint(installedContainer)
    }

    private fun packageRow(pkg: PackageResult): JComponent {
        val info = JPanel(VerticalLayout(2)).apply { isOpaque = false }
        info.add(JBLabel(pkg.name).apply { font = JBFont.label().asBold() })

        val meta = buildString {
            if (pkg.repo.isNotBlank()) append(pkg.repo)
            append("   ★ ").append(pkg.stars)
            pkg.latest?.let { append("   latest ").append(it) }
        }
        info.add(secondaryLabel(meta))
        pkg.description?.takeIf { it.isNotBlank() }?.let { info.add(secondaryLabel(truncate(it))) }

        val install = JButton("Install").apply {
            toolTipText = pkg.latest?.let { "Add ${pkg.name} @ $it as a dependency" }
                ?: "This package has no tagged release to install"
            isEnabled = pkg.latest != null && pkg.url.isNotBlank()
            addActionListener { installPackage(pkg) }
        }
        val open = JButton("Open").apply {
            toolTipText = "Open ${pkg.repo} on GitHub"
            isEnabled = pkg.url.isNotBlank()
            addActionListener { BrowserUtil.browse(pkg.url) }
        }
        return row(info, listOf(install, open))
    }

    private fun dependencyRow(dep: Dependency): JComponent {
        val info = JPanel(VerticalLayout(2)).apply { isOpaque = false }
        info.add(JBLabel(dep.name).apply { font = JBFont.label().asBold() })

        val meta = buildString {
            append(dep.kind.ifBlank { "dependency" })
            dep.current?.let { append("   ").append(it) }
        }
        info.add(secondaryLabel(meta))

        if (dep.isGit && dep.outdated && dep.latest != null) {
            val from = dep.current ?: "?"
            info.add(
                JBLabel("outdated: $from → ${dep.latest}").apply {
                    foreground = OUTDATED_COLOR
                },
            )
        }

        val actions = mutableListOf<JButton>()
        if (dep.isGit) {
            if (dep.outdated) {
                actions += JButton("Update").apply {
                    toolTipText = "Re-pin ${dep.name} to its latest tag"
                    addActionListener { updateDependency(dep) }
                }
            }
            actions += JButton("Remove").apply {
                toolTipText = "Remove ${dep.name} from luabox.toml"
                addActionListener { removeDependency(dep) }
            }
        } else {
            info.add(secondaryLabel("read-only (${dep.kind} dependency)"))
        }
        return row(info, actions)
    }

    private fun row(info: JComponent, buttons: List<JButton>): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(6, 8),
            )
        }
        panel.add(info, BorderLayout.CENTER)
        if (buttons.isNotEmpty()) {
            val east = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
            buttons.forEach { east.add(it) }
            panel.add(east, BorderLayout.EAST)
        }
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height)
        return panel
    }

    private fun renderNoManifest() {
        installedContainer.removeAll()
        val msg = JPanel(VerticalLayout(6)).apply { border = JBUI.Borders.empty(12) }
        msg.add(JBLabel("No luabox.toml in this project."))
        msg.add(secondaryLabel("Run “luabox new” in a terminal to create a project manifest."))
        installedContainer.add(msg)
        revalidateRepaint(installedContainer)
    }

    private fun renderBinaryMissing(container: JPanel) {
        container.removeAll()
        val msg = JPanel(VerticalLayout(6)).apply { border = JBUI.Borders.empty(12) }
        msg.add(JBLabel("The luabox binary wasn't found."))
        msg.add(secondaryLabel("Package management needs the luabox CLI."))
        val configure = JButton("Configure path…").apply {
            addActionListener { LuaboxBinary.openSettings(project) }
        }
        val install = JButton("Get luabox").apply {
            addActionListener { BrowserUtil.browse("https://github.com/flying-dice/luabox#install") }
        }
        msg.add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(configure)
            add(install)
        })
        container.add(msg)
        revalidateRepaint(container)
    }

    private fun setMessage(container: JPanel, text: String) {
        container.removeAll()
        container.add(JBLabel(text).apply { border = JBUI.Borders.empty(12) })
        revalidateRepaint(container)
    }

    // --- mutations ---------------------------------------------------------

    private fun installPackage(pkg: PackageResult) {
        val tag = pkg.latest ?: return
        runBg("Installing ${pkg.name}", { LuaboxCli.add(project, pkg.name, pkg.url, tag) }) {
            LuaboxNotifications.info(project, "Installed ${pkg.name} @ $tag.")
            refreshInstalled()
        }
    }

    private fun updateDependency(dep: Dependency) {
        runBg("Updating ${dep.name}", { LuaboxCli.update(project, dep.name) }) {
            LuaboxNotifications.info(project, "Updated ${dep.name} to its latest tag.")
            refreshInstalled()
        }
    }

    private fun removeDependency(dep: Dependency) {
        val confirm = Messages.showYesNoDialog(
            project,
            "Remove ${dep.name} from luabox.toml?",
            "Remove Dependency",
            Messages.getQuestionIcon(),
        )
        if (confirm != Messages.YES) return
        runBg("Removing ${dep.name}", { LuaboxCli.remove(project, dep.name) }) {
            LuaboxNotifications.info(project, "Removed ${dep.name}.")
            refreshInstalled()
        }
    }

    // --- helpers -----------------------------------------------------------

    private fun <T> runBg(title: String, compute: () -> T, onOk: (T) -> Unit) {
        val result = arrayOfNulls<Any?>(1)
        val error = arrayOfNulls<LuaboxCliException>(1)
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    result[0] = compute()
                } catch (e: LuaboxCliException) {
                    error[0] = e
                }
            }

            override fun onSuccess() {
                val e = error[0]
                if (e != null) {
                    LuaboxNotifications.error(project, e)
                    if (e.binaryMissing) {
                        renderBinaryMissing(discoverContainer)
                        renderBinaryMissing(installedContainer)
                    }
                    return
                }
                @Suppress("UNCHECKED_CAST")
                onOk(result[0] as T)
            }
        }.queue()
    }

    private fun secondaryLabel(text: String): JBLabel =
        JBLabel(text).apply {
            foreground = JBColor.GRAY
            toolTipText = text
        }

    private fun truncate(text: String, max: Int = 140): String {
        val oneLine = text.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= max) oneLine else oneLine.take(max - 1) + "…"
    }

    private fun revalidateRepaint(component: JComponent) {
        component.revalidate()
        component.repaint()
    }

    override fun dispose() {
        loginSession?.cancel()
        dismissPrompt()
    }

    private companion object {
        val OUTDATED_COLOR = JBColor(0xB58900, 0xD9A441)
        val SIGNED_IN_COLOR = JBColor(0x3C8E4A, 0x57A64A)
    }
}
