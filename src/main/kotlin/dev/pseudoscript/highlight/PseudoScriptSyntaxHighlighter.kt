package dev.pseudoscript.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import dev.pseudoscript.lexer.PseudoScriptLexer
import dev.pseudoscript.lexer.PseudoScriptTokenTypes as T

/** Maps lexer tokens to editor colors, anchored to the IDE's default scheme. */
class PseudoScriptSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = PseudoScriptLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        KEYS[tokenType]?.let { arrayOf(it) } ?: EMPTY

    companion object {
        val KEYWORD = key("PDS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val TYPE = key("PDS_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
        val FUNCTION = key("PDS_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val IDENTIFIER = key("PDS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)

        // Semantic categories — produced only by the `pds lsp` semantic-token
        // overlay (role-aware), never by the bundled lexer. The lexer paints the
        // instant fallback; these refine identifiers once the server responds.
        val NAMESPACE = key("PDS_NAMESPACE", DefaultLanguageHighlighterColors.CLASS_NAME)
        // An operation *call* falls back to the operation-declaration colour, not
        // DefaultLanguageHighlighterColors.FUNCTION_CALL (plain foreground in most
        // schemes). Otherwise the LSP `method` overlay would visibly downgrade a
        // call the lexer had already painted as FUNCTION — see docs/bugs/0001.
        // Still its own key, so calls can be recoloured distinctly if desired.
        val FUNCTION_CALL = key("PDS_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val PARAMETER = key("PDS_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER)
        val LOCAL_VARIABLE = key("PDS_LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
        val PROPERTY = key("PDS_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val ENUM_MEMBER = key("PDS_ENUM_MEMBER", DefaultLanguageHighlighterColors.STATIC_FIELD)
        val STRING = key("PDS_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER = key("PDS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val LINE_COMMENT = key("PDS_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val BLOCK_COMMENT = key("PDS_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val DOC_COMMENT = key("PDS_DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT)
        val MACRO = key("PDS_MACRO", DefaultLanguageHighlighterColors.METADATA)
        val OPERATOR = key("PDS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACES = key("PDS_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val PARENS = key("PDS_PARENS", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS = key("PDS_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val SEMICOLON = key("PDS_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val COMMA = key("PDS_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val DOT = key("PDS_DOT", DefaultLanguageHighlighterColors.DOT)
        val BAD_CHARACTER = key("PDS_BAD_CHARACTER", com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES)

        private val EMPTY = emptyArray<TextAttributesKey>()

        private fun key(name: String, fallback: TextAttributesKey): TextAttributesKey =
            createTextAttributesKey(name, fallback)

        private val KEYS: Map<IElementType, TextAttributesKey> = mapOf(
            T.KEYWORD to KEYWORD,
            T.TYPE to TYPE,
            T.FUNCTION to FUNCTION,
            T.IDENTIFIER to IDENTIFIER,
            // Lexer-inferred roles → the same colour keys the LSP overlay uses,
            // so offline highlighting matches the server (LANG.md §6/§8).
            T.NAMESPACE to NAMESPACE,
            T.PROPERTY to PROPERTY,
            T.ENUM_MEMBER to ENUM_MEMBER,
            T.FUNCTION_CALL to FUNCTION_CALL,
            T.VARIABLE to LOCAL_VARIABLE,
            T.STRING to STRING,
            T.NUMBER to NUMBER,
            T.LINE_COMMENT to LINE_COMMENT,
            T.BLOCK_COMMENT to BLOCK_COMMENT,
            T.DOC_COMMENT to DOC_COMMENT,
            T.INNER_DOC_COMMENT to DOC_COMMENT,
            T.MACRO to MACRO,
            T.OPERATOR to OPERATOR,
            T.LBRACE to BRACES,
            T.RBRACE to BRACES,
            T.LPAREN to PARENS,
            T.RPAREN to PARENS,
            T.LBRACKET to BRACKETS,
            T.RBRACKET to BRACKETS,
            T.SEMICOLON to SEMICOLON,
            T.COMMA to COMMA,
            T.DOT to DOT,
            T.BAD_CHARACTER to BAD_CHARACTER,
        )
    }
}
