package dev.pseudoscript.lang

import com.intellij.openapi.util.IconLoader

object PseudoScriptIcons {
    /** The `.pds` file icon, shown in the project tree and editor tabs. */
    @JvmField
    val FILE = IconLoader.getIcon("/icons/pds.svg", PseudoScriptIcons::class.java)

    /**
     * The monochrome grey PseudoScript badge, for the tool window and menu
     * actions where the IDE expects a greyscale icon (a `_dark` variant ships
     * alongside it).
     */
    @JvmField
    val GREY = IconLoader.getIcon("/icons/pdsGrey.svg", PseudoScriptIcons::class.java)
}
