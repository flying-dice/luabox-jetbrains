# diagrams

## Cli

`public component` · `diagrams::Cli`

Thin wrapper over the `pds` CLI for diagram data. Runs the configured binary
with the workspace as the working directory; every call returns a value for
the UI or a `CliError`, and is killed after `diagrams::TIMEOUT_MS` (off the
EDT — these block).

**Relationships**

- _Parent_
  - for [diagrams::Diagrams](diagrams.md#diagrams-Diagrams)
- _Inbound_
  - call [diagrams::StructureTree](diagrams.md#diagrams-StructureTree) — workspaces
  - call [diagrams::StructureTree](diagrams.md#diagrams-StructureTree) — outline
  - call [diagrams::DiagramEditor](diagrams.md#diagrams-DiagramEditor) — symbolSvg
  - call [diagrams::DiagramEditor](diagrams.md#diagrams-DiagramEditor) — contextSvg
  - call [diagrams::DiagramAction](diagrams.md#diagrams-DiagramAction) — outline
- _Outbound_
  - call [main::Pds](main.md#main-Pds) — list
  - from `diagrams::string`
  - from `diagrams::string`
  - call [main::Pds](main.md#main-Pds) — outline
  - call [main::Pds](main.md#main-Pds) — symbolSvg
  - call [main::Pds](main.md#main-Pds) — viewSvg
  - from `diagrams::Result`
  - from `diagrams::Result`
  - from `diagrams::Result`
  - from `diagrams::Result`
  - from `diagrams::Result`

## DiagramAction

`public component` · `diagrams::DiagramAction`

Right-click → Open Diagram on a `.pds` source file: resolve the symbol under
the caret and render it, falling back to the context view.

**Relationships**

- _Parent_
  - for [diagrams::Diagrams](diagrams.md#diagrams-Diagrams)
- _Outbound_
  - call [diagrams::Cli](diagrams.md#diagrams-Cli) — outline
  - from `diagrams::Option`
  - call [diagrams::DiagramService](diagrams.md#diagrams-DiagramService) — showContext
  - call [diagrams::DiagramService](diagrams.md#diagrams-DiagramService) — show

**Sequence — OpenDiagram**

![Sequence — OpenDiagram](../diagrams/diagrams-DiagramAction-0.svg)

## DiagramEditor

`public component` · `diagrams::DiagramEditor`

The zoomable, pannable SVG canvas: renders `pds svg` output with zoom / pan
and SVG / PNG export, following the IDE's light / dark theme.

**Relationships**

- _Parent_
  - for [diagrams::Diagrams](diagrams.md#diagrams-Diagrams)
- _Inbound_
  - call [diagrams::DiagramService](diagrams.md#diagrams-DiagramService) — RenderSymbol
  - call [diagrams::DiagramService](diagrams.md#diagrams-DiagramService) — RenderContext
- _Outbound_
  - from `diagrams::string`
  - call [diagrams::Cli](diagrams.md#diagrams-Cli) — symbolSvg
  - from `diagrams::string`
  - call [diagrams::Cli](diagrams.md#diagrams-Cli) — contextSvg
  - from `diagrams::bool`

**Scenarios**

- **SelectingANodeRenders**
  - _given_ the structure tree is populated
  - _when_ the developer opens a symbol's diagram
  - _then_ a triggered flow renders as a sequence diagram
  - _and_ a structural node renders as its C4 view
  - _and_ a data type renders as its entity view
  - _and_ a feature renders as its flow diagram

**Flow — SelectingANodeRenders**

![Flow — SelectingANodeRenders](../diagrams/diagrams-DiagramEditor-flow-0.svg)

**Sequence — Export**

![Sequence — Export](../diagrams/diagrams-DiagramEditor-0.svg)

## DiagramService

`public component` · `diagrams::DiagramService`

Opens diagrams in the main editor area, reusing one tab per project so
successive picks refresh the same view rather than spawning tabs.

**Relationships**

- _Parent_
  - for [diagrams::Diagrams](diagrams.md#diagrams-Diagrams)
- _Inbound_
  - call [diagrams::StructureTree](diagrams.md#diagrams-StructureTree) — show
  - call [diagrams::DiagramAction](diagrams.md#diagrams-DiagramAction) — showContext
  - call [diagrams::DiagramAction](diagrams.md#diagrams-DiagramAction) — show
- _Outbound_
  - call [diagrams::DiagramEditor](diagrams.md#diagrams-DiagramEditor) — RenderSymbol
  - call [main::Intellij](main.md#main-Intellij) — openEditor
  - call [diagrams::DiagramEditor](diagrams.md#diagrams-DiagramEditor) — RenderContext
  - call [main::Intellij](main.md#main-Intellij) — openEditor

## Diagrams

`public container` · `diagrams::Diagrams`

The Structure tree and the SVG diagram editor, plus the right-click action,
the Help button, and the CLI wrapper that backs them.

**Relationships**

- _Parent_
  - for [main::PseudoScriptPlugin](main.md#main-PseudoScriptPlugin)

**Component diagram**

![Component diagram](../diagrams/diagrams-Diagrams-0.svg)

## Help

`public component` · `diagrams::Help`

The Help button on the tool window: a quick PseudoProgramming intro and a
paste-ready prompt that has any coding agent install the authoring skill
(`pds skill`) and write the model.

**Relationships**

- _Parent_
  - for [diagrams::Diagrams](diagrams.md#diagrams-Diagrams)

**Sequence — ShowHelp**

![Sequence — ShowHelp](../diagrams/diagrams-Help-0.svg)

## StructureTree

`public component` · `diagrams::StructureTree`

The Structure tab: discovers workspaces, outlines each, and draws the tree —
symbols nested by parent, sorted by kind then name, each with its kind icon
(features included; triggered callables marked as flows). Each workspace
leads with a "Context overview" entry (hidden while filtering); several
workspaces group under headers, a single one renders flat. A search box
filters the tree live.

**Relationships**

- _Parent_
  - for [diagrams::Diagrams](diagrams.md#diagrams-Diagrams)
- _Outbound_
  - call [diagrams::Cli](diagrams.md#diagrams-Cli) — workspaces
  - call [diagrams::Cli](diagrams.md#diagrams-Cli) — outline
  - from `diagrams::bool`
  - from `diagrams::bool`
  - from `diagrams::bool`
  - call [diagrams::DiagramService](diagrams.md#diagrams-DiagramService) — show
  - from `diagrams::string`
  - call [main::Intellij](main.md#main-Intellij) — openEditor

**Scenarios**

- **StructureTreeListsWorkspaces**
  - _given_ a repository holding one or more `pds.toml` workspaces
  - _when_ the Structure tab refreshes
  - _then_ each workspace's symbols appear, nested by their parent
  - _and_ features appear under their target with their own icon
  - _and_ flow entry points are marked distinctly from structural nodes
- **SearchFiltersTheTree**
  - _given_ a populated structure tree
  - _when_ the developer types in the search box
  - _then_ only nodes matching by name, FQN, or kind remain
  - _and_ the ancestors of every match stay visible

**Flow — StructureTreeListsWorkspaces**

![Flow — StructureTreeListsWorkspaces](../diagrams/diagrams-StructureTree-flow-0.svg)

**Flow — SearchFiltersTheTree**

![Flow — SearchFiltersTheTree](../diagrams/diagrams-StructureTree-flow-1.svg)

**Sequence — Matches**

![Sequence — Matches](../diagrams/diagrams-StructureTree-0.svg)

**Sequence — OpenEntry**

![Sequence — OpenEntry](../diagrams/diagrams-StructureTree-1.svg)

**Sequence — OpenSource**

![Sequence — OpenSource](../diagrams/diagrams-StructureTree-2.svg)

**Sequence — Refresh**

![Sequence — Refresh](../diagrams/diagrams-StructureTree-3.svg)

