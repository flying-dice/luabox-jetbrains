package com.luabox.lang

import com.intellij.lang.Commenter

/**
 * Comment toggling for `.lua`: `--` line comments and `--[[ ]]` block comments.
 * Powers Code > Comment with Line/Block Comment on Lua files.
 */
class LuaCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "--"
    override fun getBlockCommentPrefix(): String = "--[["
    override fun getBlockCommentSuffix(): String = "]]"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
