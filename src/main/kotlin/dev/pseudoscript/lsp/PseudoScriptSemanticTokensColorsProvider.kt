package dev.pseudoscript.lsp

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider
import dev.pseudoscript.highlight.PseudoScriptSyntaxHighlighter as C

/**
 * Routes the `pds lsp` semantic tokens to the plugin's own colour keys, so the
 * server's role-aware highlighting (real user types, parameters, fields, calls)
 * shares the one **Color Scheme > PseudoScript** page with the bundled lexer.
 *
 * The two layers must agree, so each owns what it knows best. The lexer already
 * classifies every *leaf* token correctly — keywords, the three comment flavours
 * (`//` / `///` / `/* */`), string and number literals, and the whole `#[..]`
 * macro as one span — and runs first as the base layer. For those the provider
 * returns `null`: LSP4IJ then adds no overlay and the lexer's colour stands,
 * which avoids clashes (a `///` doc comment losing its doc colour, or a string
 * argument inside a macro breaking the uniform `#[..]` span). The server only
 * *refines* what the lexer can't determine without resolution — an identifier's
 * role — so only those token types map to a colour here.
 *
 * The token-type and modifier strings are the LSP standard names the server's
 * legend advertises (`semantic.rs`); `declaration` distinguishes a callable's
 * definition from a call site.
 */
class PseudoScriptSemanticTokensColorsProvider : SemanticTokensColorsProvider {
    override fun getTextAttributesKey(
        tokenType: String,
        tokenModifiers: List<String>,
        file: PsiFile,
    ): TextAttributesKey? = when (tokenType) {
        "namespace" -> C.NAMESPACE
        "type" -> C.TYPE
        "class" -> C.TYPE
        "parameter" -> C.PARAMETER
        "variable" -> C.LOCAL_VARIABLE
        "property" -> C.PROPERTY
        "enumMember" -> C.ENUM_MEMBER
        // A declared callable is its definition; a bare method token is a call.
        "method" -> if ("declaration" in tokenModifiers) C.FUNCTION else C.FUNCTION_CALL
        // keyword / comment / string / number / decorator: defer to the lexer's
        // base colour (see the class doc) by adding no overlay.
        else -> null
    }
}
