package dev.pseudoscript.lang

import com.intellij.lang.Commenter

/** Drives Ctrl+/ (line) and Ctrl+Shift+/ (block) commenting (LANG.md §2.1). */
class PseudoScriptCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
