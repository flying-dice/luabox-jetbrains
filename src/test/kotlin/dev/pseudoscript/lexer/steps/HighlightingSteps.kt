package dev.pseudoscript.lexer.steps

import com.intellij.psi.TokenType
import dev.pseudoscript.lexer.PseudoScriptLexer
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/** A lexed token as the feature tables describe it: category name + verbatim text. */
private data class Token(val type: String, val text: String)

/**
 * Cucumber step definitions for `highlighting.feature`. A fresh instance per
 * scenario (Cucumber's default), so the fields hold one scenario's state.
 */
class HighlightingSteps {
    private var source: String = ""
    private var tokens: List<Token> = emptyList()

    @Given("the PseudoScript source:")
    fun theSource(docString: String) {
        source = docString
    }

    @When("it is tokenised")
    fun itIsTokenised() {
        tokens = lex(source)
    }

    @Then("the tokens are:")
    fun theTokensAre(table: DataTable) {
        val expected = table.asLists().drop(1).map { Token(it[0], it[1]) }
        if (tokens != expected) {
            throw AssertionError(
                buildString {
                    appendLine("Token mismatch.")
                    appendLine("Expected:")
                    expected.forEach { appendLine("  ${it.type} ${quote(it.text)}") }
                    appendLine("Actual:")
                    tokens.forEach { appendLine("  ${it.type} ${quote(it.text)}") }
                },
            )
        }
    }

    /** Lexes `source`, dropping whitespace tokens the feature tables omit. */
    private fun lex(source: String): List<Token> {
        val lexer = PseudoScriptLexer()
        lexer.start(source, 0, source.length, 0)
        val out = mutableListOf<Token>()
        while (true) {
            val type = lexer.tokenType ?: break
            if (type != TokenType.WHITE_SPACE) {
                out += Token(type.toString(), source.substring(lexer.tokenStart, lexer.tokenEnd))
            }
            lexer.advance()
        }
        return out
    }

    private fun quote(text: String): String = "'" + text.replace("\n", "\\n") + "'"
}
