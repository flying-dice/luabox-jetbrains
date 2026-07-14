package com.luabox.lang

import com.intellij.lang.Language

/**
 * The Lua language as served by the luabox toolchain.
 *
 * The registry id is the product name (`luabox`), not the bare `Lua`, so this
 * plugin never hard-clashes at the Language-registry level with another Lua
 * plugin (e.g. EmmyLua) that registers the `Lua` id. Which plugin owns the
 * `.lua` extension then remains a file-type mapping the user controls under
 * Settings > Editor > File Types.
 */
object LuaLanguage : Language("luabox") {
    override fun getDisplayName(): String = "Lua"
    override fun isCaseSensitive(): Boolean = true
    private fun readResolve(): Any = LuaLanguage
}
