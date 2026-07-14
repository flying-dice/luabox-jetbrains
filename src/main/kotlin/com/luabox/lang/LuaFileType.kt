package com.luabox.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * File type for `.lua` sources, backed by [LuaLanguage]. Claiming the extension
 * makes `.lua` a first-class file (icon, base lexical highlighting, LSP
 * semantic tokens) instead of IDE-detected plain text, and stops the
 * "plugins supporting *.lua" marketplace nagging.
 */
class LuaFileType private constructor() : LanguageFileType(LuaLanguage) {
    override fun getName(): String = "Lua (luabox)"
    override fun getDescription(): String = "Lua source (served by luabox)"
    override fun getDefaultExtension(): String = "lua"
    override fun getIcon(): Icon = LuaIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = LuaFileType()
    }
}
