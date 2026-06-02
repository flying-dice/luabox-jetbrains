package dev.pseudoscript.diagram

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import dev.pseudoscript.lang.PseudoScriptIcons
import javax.swing.Icon

/**
 * The file type carried by a [DiagramVirtualFile]. Marked binary and read-only
 * so the platform's text editor never competes with the diagram editor for it.
 */
object PdsDiagramFileType : FileType {
    override fun getName(): String = "PseudoScriptDiagram"

    override fun getDescription(): String = "PseudoScript diagram preview"

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = PseudoScriptIcons.FILE

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
