package dev.pseudoscript.diagram

import com.intellij.testFramework.LightVirtualFile

/**
 * What a diagram tab currently shows: the workspace to render against and either
 * a symbol FQN (its fitting view) or `null` (the whole-workspace context view).
 * [title] is the human label for the toolbar/status line.
 */
data class DiagramTarget(val workspaceDir: String, val fqn: String?, val title: String)

/**
 * A single in-memory file backing the diagram editor. One instance is reused per
 * project (see [PdsDiagramService]) so picking different symbols refreshes the
 * same tab rather than spawning a new one. The open editor subscribes a reload
 * listener; [show] swaps the target and notifies it.
 */
class DiagramVirtualFile : LightVirtualFile("PseudoScript Diagram", PdsDiagramFileType, "") {
    @Volatile
    var target: DiagramTarget? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addReloadListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeReloadListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Point the tab at a new diagram and ask any open editor to re-render. */
    fun show(target: DiagramTarget) {
        this.target = target
        listeners.toList().forEach { it() }
    }

    override fun isWritable(): Boolean = false
}
