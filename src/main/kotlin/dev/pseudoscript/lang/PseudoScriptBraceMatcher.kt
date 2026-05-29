package dev.pseudoscript.lang

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import dev.pseudoscript.lexer.PseudoScriptTokenTypes as T

/**
 * Matches and auto-closes `{}`, `()`, and `[]` (highlight on hover, insertion of
 * the closer). Operates over the highlighting lexer's tokens — no PSI parser is
 * needed — so the open/close bracket token types must be distinct.
 */
class PseudoScriptBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbrace: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(T.LBRACE, T.RBRACE, true),
            BracePair(T.LPAREN, T.RPAREN, false),
            BracePair(T.LBRACKET, T.RBRACKET, false),
        )
    }
}
