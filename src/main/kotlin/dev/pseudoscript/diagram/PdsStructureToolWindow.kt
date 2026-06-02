package dev.pseudoscript.diagram

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import dev.pseudoscript.lang.PseudoScriptIcons
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * Registers the "PseudoScript" tool window with two tabs: the **Structure**
 * tree (navigate/diagram symbols) and **Docs** (build, serve, and preview the
 * documentation site live).
 */
class PdsStructureToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val factory = ContentFactory.getInstance()
        val contentManager = toolWindow.contentManager

        val structure = factory.createContent(PdsStructurePanel(project), "Structure", false)
        contentManager.addContent(structure)

        val docs = PdsDocsPanel(project)
        val docsContent = factory.createContent(docs, "Docs", false)
        // Stop the docs server / dispose the embedded browser when the tab closes.
        com.intellij.openapi.util.Disposer.register(docsContent, docs)
        contentManager.addContent(docsContent)
    }
}

/** What a tree node stands for. Only [Context] and [Symbol] open a diagram. */
private sealed interface TreeEntry {
    /** A workspace grouping header (shown only when the repo has several). */
    data class Workspace(val label: String) : TreeEntry

    /** The whole-model context view of [workspaceDir]. */
    data class Context(val workspaceDir: String, val title: String) : TreeEntry

    /** A declared symbol; its diagram renders against [workspaceDir]. */
    data class Symbol(val node: PdsOutlineNode, val workspaceDir: String) : TreeEntry

    /** A non-actionable message leaf (e.g. a workspace that failed to load). */
    data class Message(val text: String) : TreeEntry
}

/** One discovered workspace and its outline result. */
private data class WorkspaceOutline(
    val dir: String,
    val label: String,
    val result: PdsResult<List<PdsOutlineNode>>,
)

/**
 * The structure tree: every `pds.toml` workspace in the repo (from `pds list`),
 * each one's symbols (from `pds outline`) nested by `parent`, with the
 * whole-model context view as its first entry. With several workspaces they are
 * grouped under headers; with one the tree is flat. Selecting a node renders its
 * diagram in the main editor area via [PdsDiagramService]. Mirrors the web IDE's
 * structure panel.
 */
private class PdsStructurePanel(private val project: Project) : JPanel(BorderLayout()) {
    private val tree = Tree(DefaultMutableTreeNode())
    private val status = JLabel("", SwingConstants.CENTER)
    private val cards = CardLayout()
    private val center = JPanel(cards)
    private val projectDir = project.basePath

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = EntryRenderer()
        installContextMenu()

        status.border = JBUI.Borders.empty(16)
        center.add(JBScrollPane(tree), CARD_TREE)
        center.add(JPanel(BorderLayout()).apply { add(status, BorderLayout.CENTER) }, CARD_STATUS)

        add(buildToolbar(), BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        refresh()
    }

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Rediscover workspaces and reload outlines", AllIcons.Actions.Refresh), DumbAware {
                override fun actionPerformed(e: AnActionEvent) = refresh()
            })
            // Right-aligned (RightAlignedToolbarAction) → lands at the top-right of the tool window.
            add(PdsHelpAction())
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    private fun refresh() {
        val dir = projectDir
        if (dir == null) {
            showStatus("Open a project directory to see its PseudoScript structure.")
            return
        }
        showStatus("Loading…")
        ApplicationManager.getApplication().executeOnPooledThread {
            // Discover every workspace, then outline each. Both run `pds`, so they
            // stay off the EDT; the tree is built back on it.
            val discovery = PdsCli.workspaces(dir)
            val outlines = (discovery as? PdsResult.Ok)?.value?.map { ws ->
                WorkspaceOutline(ws, relativeLabel(dir, ws), PdsCli.outline(ws))
            }
            ApplicationManager.getApplication().invokeLater({
                when {
                    discovery is PdsResult.Err -> showStatus("Could not find PseudoScript workspaces.\n\n${discovery.message}")
                    outlines.isNullOrEmpty() -> showStatus("No PseudoScript workspaces found under this project.")
                    else -> {
                        tree.model = buildModel(outlines)
                        TreeUtil.expandAll(tree)
                        cards.show(center, CARD_TREE)
                    }
                }
            }, ModalityState.any())
        }
    }

    /**
     * The tree's right-click menu. Right-clicking first selects the node under the
     * cursor (Swing doesn't do this for us), then offers:
     * - **Open Diagram** — render the node's C4 view or flow sequence (a context
     *   overview or any declared symbol). This is deliberately not on left-click,
     *   so browsing the tree doesn't keep spawning renders.
     * - **Open Source** — jump to the symbol's declaration in its `.pds` file
     *   (declared symbols only; the context view and headers have no location).
     *
     * Double-clicking a symbol also opens its source — the default tree action.
     */
    private fun installContextMenu() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = selectOnPopup(e)
            override fun mouseReleased(e: MouseEvent) = selectOnPopup(e)
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val entry = (tree.getPathForLocation(e.x, e.y)?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
                    if (entry is TreeEntry.Symbol) openSource(entry)
                }
            }
            private fun selectOnPopup(e: MouseEvent) {
                if (e.isPopupTrigger) tree.getPathForLocation(e.x, e.y)?.let { tree.selectionPath = it }
            }
        })
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Open Diagram", "Render this node's PseudoScript diagram", PseudoScriptIcons.GREY), DumbAware {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) {
                    val entry = selectedEntry()
                    e.presentation.isEnabledAndVisible = entry is TreeEntry.Context || entry is TreeEntry.Symbol
                }
                override fun actionPerformed(e: AnActionEvent) = openEntry(selectedEntry())
            })
            add(object : AnAction("Open Source", "Open the .pds source for this symbol", AllIcons.Actions.EditSource), DumbAware {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabledAndVisible = selectedSymbol() != null
                }
                override fun actionPerformed(e: AnActionEvent) = selectedSymbol()?.let(::openSource) ?: Unit
            })
        }
        PopupHandler.installPopupMenu(tree, group, "PseudoScriptStructurePopup")
    }

    /** The selected tree node's payload, if any. */
    private fun selectedEntry(): Any? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject

    /** The selected tree node if it is a declared symbol (the only kind with a source location). */
    private fun selectedSymbol(): TreeEntry.Symbol? = selectedEntry() as? TreeEntry.Symbol

    /**
     * Open the `.pds` file declaring [symbol] and place the caret at its
     * declaration. The module path maps to a file under the workspace
     * (`a::b` → `a/b.pds`); `pds outline` line/col are 1-based, the editor's are 0.
     */
    private fun openSource(symbol: TreeEntry.Symbol) {
        val node = symbol.node
        val path = Path.of(symbol.workspaceDir, node.module.replace("::", "/") + ".pds")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        if (file == null) {
            NotificationGroupManager.getInstance().getNotificationGroup("PseudoScript")
                .createNotification("PseudoScript", "Source file not found: $path", NotificationType.WARNING)
                .notify(project)
            return
        }
        OpenFileDescriptor(project, file, (node.line - 1).coerceAtLeast(0), (node.col - 1).coerceAtLeast(0))
            .navigate(true)
    }

    /** Opens a [TreeEntry.Context] or [TreeEntry.Symbol] as a diagram; ignores the rest. */
    private fun openEntry(entry: Any?) {
        val service = PdsDiagramService.getInstance(project)
        when (entry) {
            is TreeEntry.Context -> service.show(DiagramTarget(entry.workspaceDir, null, entry.title))
            is TreeEntry.Symbol -> service.show(DiagramTarget(entry.workspaceDir, entry.node.fqn, entry.node.fqn))
            else -> Unit
        }
    }

    private fun buildModel(workspaces: List<WorkspaceOutline>): DefaultTreeModel {
        val root = DefaultMutableTreeNode("PseudoScript")
        val grouped = workspaces.size > 1
        for (workspace in workspaces) {
            // With several workspaces, nest each under its own header; with one,
            // attach its entries straight to the (hidden) root for a flat tree.
            val container =
                if (grouped) DefaultMutableTreeNode(TreeEntry.Workspace(workspace.label)).also { root.add(it) } else root
            when (val result = workspace.result) {
                is PdsResult.Err ->
                    container.add(DefaultMutableTreeNode(TreeEntry.Message("could not load: ${result.message}")))
                is PdsResult.Ok -> {
                    val contextTitle = if (grouped) "Context — ${workspace.label}" else "Context overview"
                    container.add(DefaultMutableTreeNode(TreeEntry.Context(workspace.dir, contextTitle)))
                    attachSymbols(container, workspace.dir, result.value)
                }
            }
        }
        return DefaultTreeModel(root)
    }

    /** Nests [nodes] under [container] by structural `parent`, sorted by kind then name. */
    private fun attachSymbols(container: DefaultMutableTreeNode, workspaceDir: String, nodes: List<PdsOutlineNode>) {
        val byFqn = nodes.associateBy { it.fqn }
        val children = HashMap<String, MutableList<PdsOutlineNode>>()
        val roots = ArrayList<PdsOutlineNode>()
        for (node in nodes) {
            val parent = node.parent
            if (parent != null && byFqn.containsKey(parent)) {
                children.getOrPut(parent) { ArrayList() }.add(node)
            } else {
                roots.add(node)
            }
        }

        fun sorted(list: List<PdsOutlineNode>): List<PdsOutlineNode> =
            list.sortedWith(compareBy({ KIND_ORDER[it.kind] ?: KIND_ORDER.size }, { it.name }))

        fun attach(parentTreeNode: DefaultMutableTreeNode, parentFqn: String) {
            for (child in sorted(children[parentFqn].orEmpty())) {
                val treeNode = DefaultMutableTreeNode(TreeEntry.Symbol(child, workspaceDir))
                parentTreeNode.add(treeNode)
                attach(treeNode, child.fqn)
            }
        }

        for (rootNode in sorted(roots)) {
            val treeNode = DefaultMutableTreeNode(TreeEntry.Symbol(rootNode, workspaceDir))
            container.add(treeNode)
            attach(treeNode, rootNode.fqn)
        }
    }

    /** A workspace's path relative to the project root, or its dir name at the root. */
    private fun relativeLabel(base: String, workspace: String): String =
        try {
            val rel = Path.of(base).relativize(Path.of(workspace)).toString()
            rel.ifEmpty { Path.of(workspace).fileName?.toString() ?: workspace }
        } catch (e: IllegalArgumentException) {
            Path.of(workspace).fileName?.toString() ?: workspace
        }

    private fun showStatus(message: String) {
        status.text = "<html><div style='text-align:center'>" +
            message.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") +
            "</div></html>"
        cards.show(center, CARD_STATUS)
    }

    /** Paints each entry with its C4-kind icon; flows (triggered callables) stand out. */
    private class EntryRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val entry = (value as? DefaultMutableTreeNode)?.userObject) {
                is TreeEntry.Workspace -> {
                    icon = AllIcons.Nodes.Folder
                    append(entry.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is TreeEntry.Context -> {
                    icon = AllIcons.Toolwindows.ToolWindowStructure
                    append("Context overview")
                }
                is TreeEntry.Symbol -> {
                    val node = entry.node
                    icon = iconFor(node)
                    append(node.name)
                    val tag = if (node.triggered) "flow" else node.kind
                    append("  $tag", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
                is TreeEntry.Message -> {
                    icon = AllIcons.General.Warning
                    append(entry.text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                else -> Unit
            }
        }

        private fun iconFor(node: PdsOutlineNode): Icon =
            if (node.triggered) {
                AllIcons.Actions.Execute
            } else {
                when (node.kind) {
                    "person" -> AllIcons.Nodes.Interface
                    "system" -> AllIcons.Nodes.Module
                    "container" -> AllIcons.Nodes.Package
                    "component" -> AllIcons.Nodes.Class
                    "data" -> AllIcons.Nodes.Field
                    "callable" -> AllIcons.Nodes.Method
                    else -> AllIcons.Nodes.Unknown
                }
            }
    }

    companion object {
        private const val CARD_TREE = "tree"
        private const val CARD_STATUS = "status"
        private const val TOOLBAR_PLACE = "PseudoScriptStructure"
        private val KIND_ORDER =
            mapOf("person" to 0, "system" to 1, "container" to 2, "component" to 3, "data" to 4, "callable" to 5)
    }
}
