package dev.pseudoscript.lexer

import com.intellij.psi.tree.IElementType
import dev.pseudoscript.lang.PseudoScriptLanguage

/** A token class emitted by [PseudoScriptLexer]. */
class PseudoScriptTokenType(debugName: String) : IElementType(debugName, PseudoScriptLanguage)

/**
 * The token classes the highlighting lexer emits. These are highlighting
 * categories, not the full parser token set — keywords and primitive type
 * names are grouped, punctuation is split only where it earns a distinct color.
 *
 * Mirrors the taxonomy in `CONFORMANCE/lexical/README.md` (LANG.md §2).
 */
object PseudoScriptTokenTypes {
    @JvmField val KEYWORD = PseudoScriptTokenType("KEYWORD")
    @JvmField val TYPE = PseudoScriptTokenType("TYPE")

    /** A callable name — an "operation" (LANG.md §5.1): an identifier directly followed by `(`. */
    @JvmField val FUNCTION = PseudoScriptTokenType("FUNCTION")
    @JvmField val IDENTIFIER = PseudoScriptTokenType("IDENTIFIER")

    // Role categories the lexer infers from local context to mirror the `pds lsp`
    // semantic tokens offline (LANG.md §6/§8). They map to the same colour keys
    // the LSP semantic provider uses, so the file looks the same with or without
    // the server (minus diagnostics/hover/navigation).
    @JvmField val NAMESPACE = PseudoScriptTokenType("NAMESPACE") // node/feature/alias name, `for` parent
    @JvmField val PROPERTY = PseudoScriptTokenType("PROPERTY")   // `.field` access
    @JvmField val ENUM_MEMBER = PseudoScriptTokenType("ENUM_MEMBER") // `| Variant`
    @JvmField val FUNCTION_CALL = PseudoScriptTokenType("FUNCTION_CALL") // `.method(` call

    @JvmField val STRING = PseudoScriptTokenType("STRING")
    @JvmField val NUMBER = PseudoScriptTokenType("NUMBER")

    @JvmField val LINE_COMMENT = PseudoScriptTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = PseudoScriptTokenType("BLOCK_COMMENT")
    @JvmField val DOC_COMMENT = PseudoScriptTokenType("DOC_COMMENT")
    @JvmField val INNER_DOC_COMMENT = PseudoScriptTokenType("INNER_DOC_COMMENT")

    @JvmField val MACRO = PseudoScriptTokenType("MACRO")
    @JvmField val OPERATOR = PseudoScriptTokenType("OPERATOR")

    // Brackets carry distinct open/close types so the brace matcher can pair
    // them; the highlighter still colours each pair uniformly.
    @JvmField val LBRACE = PseudoScriptTokenType("LBRACE")
    @JvmField val RBRACE = PseudoScriptTokenType("RBRACE")
    @JvmField val LPAREN = PseudoScriptTokenType("LPAREN")
    @JvmField val RPAREN = PseudoScriptTokenType("RPAREN")
    @JvmField val LBRACKET = PseudoScriptTokenType("LBRACKET")
    @JvmField val RBRACKET = PseudoScriptTokenType("RBRACKET")

    @JvmField val SEMICOLON = PseudoScriptTokenType("SEMICOLON")
    @JvmField val COMMA = PseudoScriptTokenType("COMMA")
    @JvmField val DOT = PseudoScriptTokenType("DOT")

    @JvmField val BAD_CHARACTER = PseudoScriptTokenType("BAD_CHARACTER")
}
