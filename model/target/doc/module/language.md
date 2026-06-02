# language

## Highlighter

`public component` · `language::Highlighter`

Maps lexer tokens to IDE colour keys, anchored to the active theme's defaults.

**Relationships**

- _Parent_
  - for [language::LanguageSupport](language.md#language-LanguageSupport)
- _Outbound_
  - call [language::Lexer](language.md#language-Lexer) — tokenize

**Sequence — Highlight**

![Sequence — Highlight](../diagrams/language-Highlighter-0.svg)

## LanguageSupport

`public container` · `language::LanguageSupport`

Registers `.pds` as a language and paints it from the lexer alone — the one
capability that does not depend on the external toolchain.

**Relationships**

- _Parent_
  - for [main::PseudoScriptPlugin](main.md#main-PseudoScriptPlugin)

**Scenarios**

- **OfflineHighlighting**
  - _given_ the `pds` binary is not installed
  - _when_ the developer opens a `.pds` file
  - _then_ keywords, types, macros, and comments are still syntax-highlighted

**Component diagram**

![Component diagram](../diagrams/language-LanguageSupport-0.svg)

## Lexer

`public component` · `language::Lexer`

Hand-written, stateless lexer. Tokenises `.pds` and infers each identifier's
same-line semantic role (namespace / type / function / property) by lookback,
so the offline colours line up with what `pds lsp` later refines.

**Relationships**

- _Parent_
  - for [language::LanguageSupport](language.md#language-LanguageSupport)
- _Inbound_
  - call [language::Highlighter](language.md#language-Highlighter) — tokenize

