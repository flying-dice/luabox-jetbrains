package dev.pseudoscript.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.editor.colors.TextAttributesKey
import dev.pseudoscript.lang.PseudoScriptIcons
import javax.swing.Icon

/** Settings > Editor > Color Scheme > PseudoScript. */
class PseudoScriptColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = PseudoScriptIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = PseudoScriptSyntaxHighlighter()

    override fun getDemoText(): String = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "PseudoScript"

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", PseudoScriptSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("Type", PseudoScriptSyntaxHighlighter.TYPE),
            AttributesDescriptor("Operation (callable)", PseudoScriptSyntaxHighlighter.FUNCTION),
            AttributesDescriptor("Identifier", PseudoScriptSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Semantic//Node name", PseudoScriptSyntaxHighlighter.NAMESPACE),
            AttributesDescriptor("Semantic//Operation call", PseudoScriptSyntaxHighlighter.FUNCTION_CALL),
            AttributesDescriptor("Semantic//Parameter", PseudoScriptSyntaxHighlighter.PARAMETER),
            AttributesDescriptor("Semantic//Local variable", PseudoScriptSyntaxHighlighter.LOCAL_VARIABLE),
            AttributesDescriptor("Semantic//Field", PseudoScriptSyntaxHighlighter.PROPERTY),
            AttributesDescriptor("Semantic//Union variant", PseudoScriptSyntaxHighlighter.ENUM_MEMBER),
            AttributesDescriptor("String", PseudoScriptSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", PseudoScriptSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Comment//Line", PseudoScriptSyntaxHighlighter.LINE_COMMENT),
            AttributesDescriptor("Comment//Block", PseudoScriptSyntaxHighlighter.BLOCK_COMMENT),
            AttributesDescriptor("Comment//Doc", PseudoScriptSyntaxHighlighter.DOC_COMMENT),
            AttributesDescriptor("Macro", PseudoScriptSyntaxHighlighter.MACRO),
            AttributesDescriptor("Operator", PseudoScriptSyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Braces", PseudoScriptSyntaxHighlighter.BRACES),
            AttributesDescriptor("Parentheses", PseudoScriptSyntaxHighlighter.PARENS),
            AttributesDescriptor("Brackets", PseudoScriptSyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Bad character", PseudoScriptSyntaxHighlighter.BAD_CHARACTER),
        )

        val DEMO = """
            //! Banking module.

            /// A retail customer.
            /// #actor
            public person Customer {
                /// Opens a new account.
                openAccount(name: string): Result<uuid, string> {
                    if (name) {
                        return Ok(self.allocate(name))
                    }
                    return Err("name required")
                }

                /// Looks up an account by name.
                findAccount(name: string): Option<uuid> {
                    return None
                }
            }

            #[diagram(c4)]
            system Bank {
                balance: number = 42
            }

            data Account = | Checking { id: uuid } | Savings

            /// A customer opens an account.
            feature OpenAccount for Bank {
                given "a verified owner"
                when  "the owner opens an account"
                then  "banking info is returned"
            }
        """.trimIndent()
    }
}
