package dev.pseudoscript.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/** The `.pds` file type. */
object PseudoScriptFileType : LanguageFileType(PseudoScriptLanguage) {
    override fun getName(): String = "PseudoScript"

    override fun getDescription(): String = "PseudoScript architecture model"

    override fun getDefaultExtension(): String = "pds"

    override fun getIcon(): Icon = PseudoScriptIcons.FILE
}
