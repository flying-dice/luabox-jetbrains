# docs

## DocActions

`public component` · `docs::DocActions`

The `pds.toml` context-menu actions, run with a console + stop button.

**Relationships**

- _Parent_
  - for [docs::Docs](docs.md#docs-Docs)
- _Outbound_
  - call [main::Pds](main.md#main-Pds) — doc
  - call [main::Pds](main.md#main-Pds) — doc

**Sequence — BuildDocs**

![Sequence — BuildDocs](../diagrams/docs-DocActions-0.svg)

**Sequence — ServeDocs**

![Sequence — ServeDocs](../diagrams/docs-DocActions-1.svg)

## Docs

`public container` · `docs::Docs`

The Docs tab (live preview) and the Build / Serve Docs actions.

**Relationships**

- _Parent_
  - for [main::PseudoScriptPlugin](main.md#main-PseudoScriptPlugin)

**Component diagram**

![Component diagram](../diagrams/docs-Docs-0.svg)

## DocsPanel

`public component` · `docs::DocsPanel`

Embedded live preview: runs `pds doc --watch`, loads the served site in a JCEF
browser, and auto-reloads as the model changes.

**Relationships**

- _Parent_
  - for [docs::Docs](docs.md#docs-Docs)
- _Outbound_
  - call [main::Pds](main.md#main-Pds) — doc

**Scenarios**

- **LiveDocsPreview**
  - _given_ a workspace with a `pds.toml`
  - _when_ the developer runs Build & Serve Docs
  - _then_ `pds doc` serves the site locally
  - _and_ it loads in an embedded browser and reloads as files change

**Sequence — Serve**

![Sequence — Serve](../diagrams/docs-DocsPanel-0.svg)

