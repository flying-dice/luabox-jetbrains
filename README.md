# PseudoScript for JetBrains IDEs

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/32021?label=Marketplace&logo=jetbrains&color=000000)](https://plugins.jetbrains.com/plugin/32021-pseudoscript)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/32021?label=Downloads)](https://plugins.jetbrains.com/plugin/32021-pseudoscript)
[![Build](https://github.com/flying-dice/pseudoscript-jetbrains/actions/workflows/build.yml/badge.svg)](https://github.com/flying-dice/pseudoscript-jetbrains/actions/workflows/build.yml)

The IntelliJ-platform plugin for **PseudoScript** (`.pds`) — syntax & semantic
highlighting, diagnostics, navigation, hover, formatting, and a docs-generation
menu, all in your JetBrains IDE.

**[Install from the JetBrains Marketplace →](https://plugins.jetbrains.com/plugin/32021-pseudoscript)**

> **Looking for the language itself?** PseudoScript — the language, its
> specification, and the `pds` compiler / LSP / docs toolchain — lives in the
> main repository:
>
> ### → https://github.com/flying-dice/pseudoscript
>
> This repository is **only the editor plugin**. It talks to the `pds` binary
> from that project for everything beyond local syntax highlighting.

## Features

| Feature | Provided by |
| --- | --- |
| Syntax highlighting, comment toggling, brace matching, color-scheme page | bundled lexer (works with no `pds` installed) |
| Semantic highlighting — role-aware colors for types, params, fields, calls | `pds lsp` → `semanticTokens` |
| Diagnostics (errors & warnings) | `pds lsp` → `publishDiagnostics` |
| Hover, Go to definition, Find usages, Rename | `pds lsp` |
| Formatting | `pds lsp` → `formatting` |
| **Build / Serve Docs** menu on `pds.toml` | `pds doc` / `pds doc --serve` |

**Highlighting works offline.** The bundled lexer colors a file the moment it
opens — keywords, types, declarations, members, literals — even before (or
without) the language server. When `pds lsp` connects, its semantic tokens
refine the same colors with full role accuracy. Both layers draw from the one
**Settings → Editor → Color Scheme → PseudoScript** page, so the file looks the
same with or without the server (you only gain diagnostics, hover, and
navigation when it's running).

## Requirements

- A JetBrains IDE **2024.3 – 2026.1** (any flavor: IDEA Community/Ultimate,
  RustRover, WebStorm, …).
- The [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) plugin,
  **0.19.4 or newer** — a hard dependency installed automatically from the
  Marketplace. (The minimum is enforced because `.pds` files use LSP4IJ's
  semantic-token file view provider; on an older LSP4IJ that class is missing
  and the plugin fails to load.)
- The **`pds` binary on your `PATH`** — built and installed from the
  [PseudoScript repo](https://github.com/flying-dice/pseudoscript) (follow its
  README; typically `cargo install` from a clone). Not on `PATH`? Point the
  plugin at it in **Settings → Languages & Frameworks → PseudoScript**.

  Without `pds`, you still get the bundled syntax highlighting — but not the
  LSP-backed features (semantic colors, diagnostics, hover, navigation,
  formatting, docs menu).

## Install

**From the JetBrains Marketplace** (recommended) — the
[**PseudoScript** listing](https://plugins.jetbrains.com/plugin/32021-pseudoscript):
in your IDE, **Settings → Plugins → Marketplace**, search **PseudoScript**, click
**Install**, and restart when prompted. LSP4IJ is pulled in automatically as a
dependency.

<details>
<summary>Install from a zip (pre-release or local builds)</summary>

1. Build it: `./gradlew buildPlugin` → `build/distributions/pseudoscript-jetbrains-<version>.zip`
   (or grab a zip from the [Releases](https://github.com/flying-dice/pseudoscript-jetbrains/releases)).
2. In the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the zip.
   Install LSP4IJ from the Marketplace first (it isn't bundled in the zip).
3. Restart when prompted. Reinstalling the same version? Uninstall the old copy
   first, or the IDE skips the replacement.
</details>

## Usage

Open any `.pds` file — highlighting is immediate; the language server starts on
first open. Inspect its traffic in **View → Tool Windows → Language Servers**
(added by LSP4IJ).

**Generate the docs site.** Right-click a `pds.toml` (Project view, editor, or
tab) → **PseudoScript**:

| Item | Runs | Result |
| --- | --- | --- |
| **Build Docs** | `pds doc <dir>` | Generates the site once. |
| **Serve Docs** | `pds doc <dir> --serve` | Generates, then serves at `http://127.0.0.1:8000/`. |

`<dir>` is the folder holding the clicked `pds.toml`. Output streams to a
console tool window; its **stop** button kills the process — that is how you
stop a running `--serve`.

## Configuration

**Settings → Languages & Frameworks → PseudoScript** — the path to the `pds`
binary (default `pds`, resolved on `PATH`). Shared by the language server and
the docs menu.

---

## Contributing

This section is for working on the plugin itself.

```sh
./gradlew runIde          # launch a sandbox IDE with the plugin loaded
./gradlew buildPlugin     # produce build/distributions/pseudoscript-jetbrains-<version>.zip
./gradlew verifyPlugin    # run the JetBrains Plugin Verifier
./gradlew test            # run the lexer acceptance suite (no IDE needed)
```

The build needs **JDK 21** (the 2024.3+ platform requirement); Gradle resolves
it via the configured toolchain. Run configurations for the common tasks are
checked in under `.run/`.

**Tests.** Tokenisation has a BDD acceptance suite — Gherkin features run by the
Cucumber JUnit-Platform engine. Each scenario asserts the exact token category +
verbatim text, in source order; add a rule by adding a `Scenario` (no new step
code). Specs in `src/test/resources/features/`, steps in
`src/test/kotlin/dev/pseudoscript/lexer/steps/`.

**Layout.**

```
src/main/kotlin/dev/pseudoscript/
  lang/        Language, file type, icon, commenter, brace matcher
  lexer/       highlighting lexer + token types
  highlight/   syntax highlighter, factory, color settings page
  lsp/         LSP4IJ factory, pds-lsp launcher, semantic-token colors
  actions/     Build / Serve Docs context-menu actions
  settings/    pds binary path (persisted) + settings UI
src/main/resources/META-INF/plugin.xml
```

**Versions.** Pinned in `gradle.properties`: `platformVersion`, `lsp4ijVersion`,
`pluginSinceBuild`, `pluginUntilBuild`. Bump there to retarget IDE builds.
