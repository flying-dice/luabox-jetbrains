package com.luabox.packages

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.luabox.settings.LuaboxBinary

/**
 * Balloon notifications for package-management outcomes. The group id `luabox`
 * is registered in plugin.xml. Errors carry contextual actions — a token hint on
 * rate-limit, a settings/install link when the binary is missing.
 */
object LuaboxNotifications {
    private const val GROUP_ID = "luabox"

    fun info(project: Project, content: String) {
        group().createNotification(content, NotificationType.INFORMATION).notify(project)
    }

    /** Report a CLI failure, attaching the right recovery action for its kind. */
    fun error(project: Project, ex: LuaboxCliException) {
        val notification: Notification = when {
            ex.isRateLimited -> group()
                .createNotification(
                    "GitHub rate limit reached. Set a GitHub token in the luabox " +
                        "settings to raise the limit.",
                    NotificationType.WARNING,
                )
                .addAction(NotificationAction.createSimple("Set a GitHub token…") {
                    LuaboxBinary.openSettings(project)
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

    private fun group() =
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
}
