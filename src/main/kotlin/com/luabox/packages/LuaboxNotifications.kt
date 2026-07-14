package com.luabox.packages

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.luabox.settings.LuaboxBinary

/**
 * Balloon notifications for package-management and sign-in outcomes. The group id
 * `luabox` is registered in plugin.xml. Errors carry contextual actions — a
 * sign-in hint on rate-limit, a settings/install link when the binary is missing,
 * a releases link when the CLI is too old for device-flow sign-in.
 */
object LuaboxNotifications {
    private const val GROUP_ID = "luabox"
    private const val LUABOX_RELEASES = "https://github.com/flying-dice/luabox/releases"

    fun info(project: Project, content: String) {
        group().createNotification(content, NotificationType.INFORMATION).notify(project)
    }

    /** Report a CLI failure, attaching the right recovery action for its kind. */
    fun error(project: Project, ex: LuaboxCliException) {
        val notification: Notification = when {
            ex.isRateLimited -> group()
                .createNotification(
                    "GitHub rate limit reached. Sign in with GitHub to raise the limit.",
                    NotificationType.WARNING,
                )
                .addAction(NotificationAction.createSimple("Sign in with GitHub") {
                    LuaboxSignIn.start(project)
                })

            ex.binaryMissing -> group()
                .createNotification(ex.message ?: "The luabox binary wasn't found.", NotificationType.ERROR)
                .addAction(NotificationAction.createSimple("Configure path…") {
                    LuaboxBinary.openSettings(project)
                })

            else -> group()
                .createNotification(ex.message ?: "luabox command failed.", NotificationType.ERROR)
        }
        notification.notify(project)
    }

    /**
     * The sticky device-flow prompt: shows the user code and where to enter it,
     * with a "copy code & open browser" action and a cancel action. Returned so
     * the caller can [Notification.expire] it once the flow completes.
     */
    fun loginPrompt(
        project: Project,
        userCode: String,
        verificationUri: String,
        expiresInMinutes: Int,
        onCopyAndOpen: () -> Unit,
        onCancel: () -> Unit,
    ): Notification {
        val n = group().createNotification(
            "Sign in to GitHub",
            "Enter the code <b>$userCode</b> at $verificationUri to finish signing in. " +
                "The code expires in about $expiresInMinutes minutes.",
            NotificationType.INFORMATION,
        )
        n.isImportant = true
        n.addAction(NotificationAction.createSimple("Copy code & open browser") { onCopyAndOpen() })
        n.addAction(NotificationAction.createSimple("Cancel") { onCancel() })
        n.notify(project)
        return n
    }

    /** The installed CLI predates `luabox login` — point the user at an update. */
    fun cliTooOldForLogin(project: Project) {
        group()
            .createNotification(
                "Update luabox to sign in with GitHub",
                "GitHub sign-in needs luabox ≥ 0.1.4. Update the luabox CLI and try again.",
                NotificationType.ERROR,
            )
            .addAction(NotificationAction.createSimple("Get luabox releases") {
                BrowserUtil.browse(LUABOX_RELEASES)
            })
            .notify(project)
    }

    private fun group() =
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
}
