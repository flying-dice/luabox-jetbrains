Feature: PseudoScript syntax-highlighting tokenisation
  The highlighting lexer (LANG.md §2) splits source into colour categories.
  These are highlighting tokens, not the full parser token set: keywords are
  grouped, primitive type names and Result are types, and a whole macro
  attribute is a single span.

  Whitespace tokens are omitted from the expected tables; every other token is
  asserted in source order as TYPE / verbatim TEXT.

  Scenario: Keywords, identifiers and braces
    Given the PseudoScript source:
      """
      system Bank { }
      """
    When it is tokenised
    Then the tokens are:
      | type       | text   |
      | KEYWORD    | system |
      | NAMESPACE  | Bank   |
      | LBRACE     | {      |
      | RBRACE     | }      |

  Scenario: An operation (callable) name highlights distinctly from an identifier
    Given the PseudoScript source:
      """
      component AccountService for Mainframe {
        fetch(id)
      }
      """
    When it is tokenised
    Then the tokens are:
      | type       | text           |
      | KEYWORD    | component      |
      | NAMESPACE  | AccountService |
      | KEYWORD    | for            |
      | NAMESPACE  | Mainframe      |
      | LBRACE     | {              |
      | FUNCTION   | fetch          |
      | LPAREN     | (              |
      | IDENTIFIER | id             |
      | RPAREN     | )              |
      | RBRACE     | }              |

  Scenario: Primitive type names and Result highlight as types
    Given the PseudoScript source:
      """
      total(): Result<number, string>
      """
    When it is tokenised
    Then the tokens are:
      | type       | text   |
      | FUNCTION   | total  |
      | LPAREN     | (      |
      | RPAREN     | )      |
      | OPERATOR   | :      |
      | TYPE       | Result |
      | OPERATOR   | <      |
      | TYPE       | number |
      | COMMA      | ,      |
      | TYPE       | string |
      | OPERATOR   | >      |

  Scenario: Option type and its Some/None constructors (§6.2)
    Given the PseudoScript source:
      """
      find(): Option<Person> {
        return Some(None)
      }
      """
    When it is tokenised
    Then the tokens are:
      | type       | text   |
      | FUNCTION   | find   |
      | LPAREN     | (      |
      | RPAREN     | )      |
      | OPERATOR   | :      |
      | TYPE       | Option |
      | OPERATOR   | <      |
      | IDENTIFIER | Person |
      | OPERATOR   | >      |
      | LBRACE     | {      |
      | KEYWORD    | return |
      | KEYWORD    | Some   |
      | LPAREN     | (      |
      | KEYWORD    | None   |
      | RPAREN     | )      |
      | RBRACE     | }      |

  Scenario: Array type suffix
    Given the PseudoScript source:
      """
      tags: string[]
      """
    When it is tokenised
    Then the tokens are:
      | type       | text   |
      | IDENTIFIER | tags   |
      | OPERATOR   | :      |
      | TYPE       | string |
      | LBRACKET   | [      |
      | RBRACKET   | ]      |

  Scenario: String and number literals
    Given the PseudoScript source:
      """
      x = 42 "hi"
      """
    When it is tokenised
    Then the tokens are:
      | type       | text |
      | IDENTIFIER | x    |
      | OPERATOR   | =    |
      | NUMBER     | 42   |
      | STRING     | "hi" |

  Scenario: A decimal number is a single token (ADR-013)
    Given the PseudoScript source:
      """
      pi = 3.14
      """
    When it is tokenised
    Then the tokens are:
      | type       | text |
      | IDENTIFIER | pi   |
      | OPERATOR   | =    |
      | NUMBER     | 3.14 |

  Scenario: A dot not followed by a digit is member access, not a decimal
    Given the PseudoScript source:
      """
      3.field
      """
    When it is tokenised
    Then the tokens are:
      | type       | text  |
      | NUMBER     | 3     |
      | DOT        | .     |
      | PROPERTY   | field |

  Scenario: Line, block, doc and inner-doc comments
    Given the PseudoScript source:
      """
      // line
      /* block */
      /// doc
      //! inner
      """
    When it is tokenised
    Then the tokens are:
      | type              | text        |
      | LINE_COMMENT      | // line     |
      | BLOCK_COMMENT     | /* block */ |
      | DOC_COMMENT       | /// doc     |
      | INNER_DOC_COMMENT | //! inner   |

  Scenario: A macro attribute is a single span
    Given the PseudoScript source:
      """
      #[diagram(c4)]
      """
    When it is tokenised
    Then the tokens are:
      | type  | text          |
      | MACRO | #[diagram(c4)] |

  Scenario: A macro attribute ignores brackets inside a string argument
    Given the PseudoScript source:
      """
      #[label("a]b")]
      """
    When it is tokenised
    Then the tokens are:
      | type  | text            |
      | MACRO | #[label("a]b")] |

  Scenario: A macro attribute tracks nested brackets
    Given the PseudoScript source:
      """
      #[a([])]
      """
    When it is tokenised
    Then the tokens are:
      | type  | text     |
      | MACRO | #[a([])] |

  Scenario: A macro attribute precedes a declaration
    Given the PseudoScript source:
      """
      #[c4]
      system S;
      """
    When it is tokenised
    Then the tokens are:
      | type       | text   |
      | MACRO      | #[c4]  |
      | KEYWORD    | system |
      | NAMESPACE  | S      |
      | SEMICOLON  | ;      |

  Scenario: An unterminated macro attribute runs to end of input
    Given the PseudoScript source:
      """
      #[oops
      """
    When it is tokenised
    Then the tokens are:
      | type  | text   |
      | MACRO | #[oops |

  Scenario: A lone hash is a bad character, not a macro
    Given the PseudoScript source:
      """
      # x
      """
    When it is tokenised
    Then the tokens are:
      | type          | text |
      | BAD_CHARACTER | #    |
      | IDENTIFIER    | x    |

  Scenario: Tags inside a doc line stay part of the doc comment
    Given the PseudoScript source:
      """
      /// see #actor here
      """
    When it is tokenised
    Then the tokens are:
      | type        | text                |
      | DOC_COMMENT | /// see #actor here |

  Scenario: Path separator and member chaining
    Given the PseudoScript source:
      """
      Repo::fetch.value
      """
    When it is tokenised
    Then the tokens are:
      | type       | text  |
      | IDENTIFIER | Repo  |
      | OPERATOR   | ::    |
      | IDENTIFIER | fetch |
      | DOT        | .     |
      | PROPERTY   | value |

  Scenario: An unterminated string stops at the line break
    Given the PseudoScript source:
      """
      x = "oops
      y = 1
      """
    When it is tokenised
    Then the tokens are:
      | type       | text  |
      | IDENTIFIER | x     |
      | OPERATOR   | =     |
      | STRING     | "oops |
      | IDENTIFIER | y     |
      | OPERATOR   | =     |
      | NUMBER     | 1     |

  Scenario: A trailing backslash does not let an unterminated string swallow the next line
    Given the PseudoScript source:
      """
      x = "oops\
      y = 1
      """
    When it is tokenised
    Then the tokens are:
      | type       | text   |
      | IDENTIFIER | x      |
      | OPERATOR   | =      |
      | STRING     | "oops\ |
      | IDENTIFIER | y      |
      | OPERATOR   | =      |
      | NUMBER     | 1      |

  Scenario: Result markers and self chaining in a body
    Given the PseudoScript source:
      """
      return Ok(self.allocate(name))
      """
    When it is tokenised
    Then the tokens are:
      | type          | text     |
      | KEYWORD       | return   |
      | KEYWORD       | Ok       |
      | LPAREN        | (        |
      | KEYWORD       | self     |
      | DOT           | .        |
      | FUNCTION_CALL | allocate |
      | LPAREN        | (        |
      | IDENTIFIER    | name     |
      | RPAREN        | )        |
      | RPAREN        | )        |

  Scenario: A qualified cross-module member call colours the call name
    Given the PseudoScript source:
      """
      cli::DocCmd.run(path)
      """
    When it is tokenised
    Then the tokens are:
      | type          | text   |
      | IDENTIFIER    | cli    |
      | OPERATOR      | ::     |
      | IDENTIFIER    | DocCmd |
      | DOT           | .      |
      | FUNCTION_CALL | run    |
      | LPAREN        | (      |
      | IDENTIFIER    | path   |
      | RPAREN        | )      |

  Scenario: A feature construct with Gherkin step keywords (§5.2)
    Given the PseudoScript source:
      """
      feature OpenAccount for Mainframe {
        given "a verified owner"
        and   "no existing account"
        when  "the owner opens an account"
        then  "banking info is returned"
        but   "no card is issued"
      }
      """
    When it is tokenised
    Then the tokens are:
      | type       | text                       |
      | KEYWORD    | feature                    |
      | NAMESPACE  | OpenAccount                |
      | KEYWORD    | for                        |
      | NAMESPACE  | Mainframe                  |
      | LBRACE     | {                          |
      | KEYWORD    | given                      |
      | STRING     | "a verified owner"         |
      | KEYWORD    | and                        |
      | STRING     | "no existing account"      |
      | KEYWORD    | when                       |
      | STRING     | "the owner opens an account" |
      | KEYWORD    | then                       |
      | STRING     | "banking info is returned" |
      | KEYWORD    | but                        |
      | STRING     | "no card is issued"        |
      | RBRACE     | }                          |

  Scenario: A constant declaration names a value (§2.3, §3.6)
    Given the PseudoScript source:
      """
      public constant PI = 3.14
      constants = PI
      """
    When it is tokenised
    Then the tokens are:
      | type       | text      |
      | KEYWORD    | public    |
      | KEYWORD    | constant  |
      | VARIABLE   | PI        |
      | OPERATOR   | =         |
      | NUMBER     | 3.14      |
      | IDENTIFIER | constants |
      | OPERATOR   | =         |
      | IDENTIFIER | PI        |

  Scenario: Each operator lexes as a distinct token; two-character operators win (§7.5)
    Given the PseudoScript source:
      """
      a = b + c - d * e / f % g
      h = i == j != k < l > m <= n >= o
      t = !u
      v = -w
      """
    When it is tokenised
    Then the tokens are:
      | type       | text |
      | IDENTIFIER | a    |
      | OPERATOR   | =    |
      | IDENTIFIER | b    |
      | OPERATOR   | +    |
      | IDENTIFIER | c    |
      | OPERATOR   | -    |
      | IDENTIFIER | d    |
      | OPERATOR   | *    |
      | IDENTIFIER | e    |
      | OPERATOR   | /    |
      | IDENTIFIER | f    |
      | OPERATOR   | %    |
      | IDENTIFIER | g    |
      | IDENTIFIER | h    |
      | OPERATOR   | =    |
      | IDENTIFIER | i    |
      | OPERATOR   | ==   |
      | IDENTIFIER | j    |
      | OPERATOR   | !=   |
      | IDENTIFIER | k    |
      | OPERATOR   | <    |
      | IDENTIFIER | l    |
      | OPERATOR   | >    |
      | IDENTIFIER | m    |
      | OPERATOR   | <=   |
      | IDENTIFIER | n    |
      | OPERATOR   | >=   |
      | IDENTIFIER | o    |
      | IDENTIFIER | t    |
      | OPERATOR   | =    |
      | OPERATOR   | !    |
      | IDENTIFIER | u    |
      | IDENTIFIER | v    |
      | OPERATOR   | =    |
      | OPERATOR   | -    |
      | IDENTIFIER | w    |

  Scenario: A boolean-or operand is not a union variant (§7.5)
    Given the PseudoScript source:
      """
      p = q && r || s
      """
    When it is tokenised
    Then the tokens are:
      | type       | text |
      | IDENTIFIER | p    |
      | OPERATOR   | =    |
      | IDENTIFIER | q    |
      | OPERATOR   | &&   |
      | IDENTIFIER | r    |
      | OPERATOR   | \|\| |
      | IDENTIFIER | s    |

  Scenario: A lone ampersand is a bad character; only && is an operator (§7.5)
    Given the PseudoScript source:
      """
      a & b
      """
    When it is tokenised
    Then the tokens are:
      | type          | text |
      | IDENTIFIER    | a    |
      | BAD_CHARACTER | &    |
      | IDENTIFIER    | b    |

  Scenario: Declaration, variant and type roles mirror the LSP offline (§6, §8)
    Given the PseudoScript source:
      """
      data Account =
        | Open { owner: Person }
      """
    When it is tokenised
    Then the tokens are:
      | type        | text    |
      | KEYWORD     | data    |
      | TYPE        | Account |
      | OPERATOR    | =       |
      | OPERATOR    | \|      |
      | ENUM_MEMBER | Open    |
      | LBRACE      | {       |
      | IDENTIFIER  | owner   |
      | OPERATOR    | :       |
      | TYPE        | Person  |
      | RBRACE      | }       |
