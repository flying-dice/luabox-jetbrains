# luabox for JetBrains IDEs

[![Build](https://github.com/flying-dice/luabox-jetbrains/actions/workflows/build.yml/badge.svg)](https://github.com/flying-dice/luabox-jetbrains/actions/workflows/build.yml)

The IntelliJ-platform plugin for **Lua**, powered by the
[**luabox**](https://github.com/flying-dice/luabox) toolchain — a unified Lua
typechecker, linter, formatter, and package manager. It registers `.lua` as a
first-class file type with bundled base highlighting, and connects the
`luabox lsp` language server for diagnostics, hover, completion, navigation,
formatting, and semantic highlighting — all in your JetBrains IDE.

> **Looking for the toolchain itself?** luabox — the CLI, typechecker, and
> `luabox lsp` language server — lives in the main repository:
>
> ### → https://github.com/flying-dice/luabox
>
> This repository is **only the editor plugin**. It talks to the `luabox`
> binary from that project for everything beyond local base highlighting.

## Features

| Feature | Provided by |
| --- | --- |
| Base syntax highlighting — keywords, strings, numbers, comments | bundled lexer (works with no `luabox` installed) |
| Semantic highlighting — role-aware colors | `luabox lsp` → `semanticTokens` |
| Diagnostics with quick-fixes | `luabox lsp` → `publishDiagnostics` |
| Hover, completion, go-to-definition, document symbols | `luabox lsp` |
| Formatting | `luabox lsp` → `formatting` |

**Base highlighting works offline.** The bundled lexer colors a `.lua` file the
moment it opens — even before (or without) the language server. When
`luabox lsp` connects, its semantic tokens refine the colors with full role
accuracy, and you gain diagnostics, hover, completion, navigation, and
formatting.

## Requirements

- A JetBrains IDE **2025.1 – 2026.1** (any flavor: IDEA Community/Ultimate,
  RustRover, WebStorm, …). LSP4IJ makes this work on Community editions too.
- The [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) plugin,
  **0.19.4 or newer** — a hard dependency installed automatically from the
  Marketplace.
- The **`luabox` binary**, from the
  [luabox releases](https://github.com/flying-dice/luabox/releases). The install
  scripts drop it in `~/.luabox/bin`; the plugin resolves `luabox` on your
  `PATH` and in `~/.luabox/bin` automatically. If it lives elsewhere, point the
  plugin at it in **Settings → Languages & Frameworks → luabox**.

  Without `luabox`, you still get the bundled base highlighting — but not the
  LSP-backed features.

## Install

**From a zip** (this repo's [Releases](https://github.com/flying-dice/luabox-jetbrains/releases)):

1. Grab `luabox-<version>.zip` from a release (or build it yourself — see below).
2. Install **LSP4IJ** from the Marketplace first (it isn't bundled in the zip).
3. In the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the
   zip. Restart when prompted.
4. Ensure `luabox` is installed (see Requirements).

## Usage

Open any `.lua` file — base highlighting is immediate; the language server
starts on first open when `luabox` is available. Inspect its traffic in
**View → Tool Windows → Language Servers** (added by LSP4IJ).

## Configuration

**Settings → Languages & Frameworks → luabox** — the path to the `luabox`
binary. A bare name (default `luabox`) is resolved on `PATH`, then in
`~/.luabox/bin`. The server is launched as `<path> lsp`.

---

## Contributing

This section is for working on the plugin itself.

```sh
./gradlew runIde          # launch a sandbox IDE with the plugin loaded
./gradlew buildPlugin     # produce build/distributions/luabox-<version>.zip
./gradlew verifyPlugin    # run the JetBrains Plugin Verifier
```

The build needs **JDK 21** (the 2025.1+ platform requirement); Gradle resolves
it via the configured toolchain. Run configurations for the common tasks are
checked in under `.run/`.

**Layout.**

```
src/main/kotlin/com/luabox/
  lang/          Lua Language, file type, icon, commenter
  highlight/     base-highlighting lexer + syntax highlighter + factory
  lsp/           LSP4IJ factory + luabox-lsp launcher
  settings/      luabox binary path (persisted), resolution + settings UI
  notification/  missing-binary editor banner
src/main/resources/META-INF/plugin.xml
```

**Versions.** Pinned in `gradle.properties`: `platformVersion`, `lsp4ijVersion`,
`pluginSinceBuild`, `pluginUntilBuild`. Bump there to retarget IDE builds.

## Releasing

Push a `v*` tag (e.g. `v0.1.0`) matching `pluginVersion` in `gradle.properties`.
The [release workflow](.github/workflows/release.yml) verifies and builds the
plugin, then creates a GitHub Release with the plugin zip and a `SHA256SUMS`
file attached. Marketplace publishing is not wired up (it needs a `JB_TOKEN`
secret and a pre-existing Marketplace listing).

---

*Forked from [flying-dice/pseudoscript-jetbrains](https://github.com/flying-dice/pseudoscript-jetbrains).*
