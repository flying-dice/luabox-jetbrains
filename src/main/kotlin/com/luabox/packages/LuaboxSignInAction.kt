package com.luabox.packages

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/** The tool window's id (see plugin.xml). */
internal const val PACKAGES_TOOL_WINDOW_ID = "luabox Packages"

/**
 * Entry point for starting the GitHub device-flow sign-in from anywhere (the
 * [LuaboxSignInAction], a rate-limit notification). Activates the Packages tool
 * window — which lazily builds [LuaboxPackagesPanel] — and delegates to its
 * [LuaboxPackagesPanel.signIn], so the auth bar and prompt notification all come
 * from one place.
 */
object LuaboxSignIn {
    fun start(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(PACKAGES_TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            val panel = toolWindow.contentManager.contents
                .firstOrNull()?.component as? LuaboxPackagesPanel
            panel?.signIn()
        }
    }
}

/**
 * Action `luabox.signInGithub`: start GitHub device-flow sign-in for luabox.
 * Optional — it only benefits git-source dependency operations
 * (`outdated`/`update`'s GitHub release probing and private-repo access); the
 * luarocks.org registry (search/install) is always anonymous.
 */
class LuaboxSignInAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LuaboxSignIn.start(project)
    }
}
