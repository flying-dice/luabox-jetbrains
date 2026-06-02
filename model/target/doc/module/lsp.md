# lsp

## LanguageServer

`public component` · `lsp::LanguageServer`

The launcher LSP4IJ starts: it spawns `pds lsp` with the project root as the
working directory so the server resolves `pds.toml` and workspace FQNs.

**Relationships**

- _Parent_
  - for [lsp::LspBridge](lsp.md#lsp-LspBridge)
- _Outbound_
  - call [config::BinaryCheck](config.md#config-BinaryCheck) — isAvailable
  - call [config::BinaryCheck](config.md#config-BinaryCheck) — notFoundMessage
  - call [main::Pds](main.md#main-Pds) — lsp

**Scenarios**

- **MissingBinaryIsExplained**
  - _given_ the `pds` binary cannot be found on PATH
  - _when_ the language server is asked to start
  - _then_ startup fails with a clear setup message pointing at Settings

**Sequence — Start**

![Sequence — Start](../diagrams/lsp-LanguageServer-0.svg)

## LspBridge

`public container` · `lsp::LspBridge`

Wires the `pds lsp` language server into the IDE via LSP4IJ.

**Relationships**

- _Parent_
  - for [main::PseudoScriptPlugin](main.md#main-PseudoScriptPlugin)

**Component diagram**

![Component diagram](../diagrams/lsp-LspBridge-0.svg)

## SemanticTokens

`public component` · `lsp::SemanticTokens`

Maps `pds lsp` semantic tokens onto the plugin's colour keys: the lexer paints
the base layer, this refines identifiers (declaration vs call, role-aware).

**Relationships**

- _Parent_
  - for [lsp::LspBridge](lsp.md#lsp-LspBridge)

## ServerFactory

`public component` · `lsp::ServerFactory`

Hands LSP4IJ the connection provider for the `pseudoscript` server.

**Relationships**

- _Parent_
  - for [lsp::LspBridge](lsp.md#lsp-LspBridge)
- _Outbound_
  - call [main::Lsp4ij](main.md#main-Lsp4ij) — registerServer

**Sequence — Register**

![Sequence — Register](../diagrams/lsp-ServerFactory-0.svg)

