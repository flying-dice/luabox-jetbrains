package com.luabox.packages

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the "luabox Packages" tool window (see plugin.xml). The whole UI —
 * discover/search + installed deps with outdated/update/remove — lives in
 * [LuaboxPackagesPanel].
 */
class LuaboxPackagesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LuaboxPackagesPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        panel.refreshAll()
    }
}
