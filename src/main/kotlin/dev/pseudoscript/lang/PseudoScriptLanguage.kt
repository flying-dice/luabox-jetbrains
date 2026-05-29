package dev.pseudoscript.lang

import com.intellij.lang.Language

/** The PseudoScript language (`.pds`). */
object PseudoScriptLanguage : Language("PseudoScript") {
    private fun readResolve(): Any = PseudoScriptLanguage

    override fun getDisplayName(): String = "PseudoScript"
}
