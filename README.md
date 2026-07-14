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
| Dependency management — discover, install, outdated/update/remove | `luabox` CLI (`search`, `outdated`, `add`, `remove`, `update`) |

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

1. Grab `luabox-jetbrains-<version>.zip` from a release (or build it yourself — see below).
2. Install **LSP4IJ** from the Marketplace first (it isn't bundled in the zip).
3. In the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the
   zip. Restart when prompted.
4. Ensure `luabox` is installed (see Requirements).

## Usage

Open any `.lua` file — base highlighting is immediate; the language server
starts on first open when `luabox` is available. Inspect its traffic in
**View → Tool Windows → Language Servers** (added by LSP4IJ).

## Dependency management

The **luabox Packages** tool window (right-anchored — **View → Tool Windows →
luabox Packages**) is an npm-like GUI over the luabox CLI's GitHub-as-registry
commands. It shells out to the same `luabox` binary the language server uses, in
your project's base directory, and renders the JSON it prints.

- **Discover.** Type a query and press Enter to run `luabox search`. Each result
  is a card — package name, `owner/repo`, ★ stars, description, and latest tag —
  with an **Install** button that runs `luabox add <name> --git <url> --tag
  <latest>` to add it as a dependency.
- **Installed.** Every dependency from `luabox outdated` is listed with its
  current pin. Git deps that have a newer tag show an **outdated: current →
  latest** indicator and an **Update** button (`luabox update <name>`, re-pins to
  the latest tag); **Remove** runs `luabox remove <name>`. Non-git deps
  (path/workspace/registry) are read-only. The toolbar has **Refresh** and an
  outdated-count label.
- The Installed view refreshes automatically after any install/update/remove and
  whenever `luabox.toml` changes on disk.

Edge states are handled: no `luabox.toml` → a "run `luabox new`" hint; the
`luabox` binary not found → a configure/install prompt; a GitHub rate-limit (403)
→ a notification linking to the token setting.

**Requires the `luabox` CLI ≥ 0.1.3** (the version that ships the `search`,
`outdated`, `add`, `remove`, and `update` commands with `--format json`).

## Configuration

**Settings → Languages & Frameworks → luabox**:

- **Path to the luabox binary** — a bare name (default `luabox`) is resolved on
  `PATH`, then in `~/.luabox/bin`. The server is launched as `<path> lsp`, and
  the package-management commands run the same binary.
- **GitHub token** (optional) — passed to the CLI as `LUABOX_GITHUB_TOKEN` when
  searching and resolving package versions, for higher GitHub API rate limits.
  Set one if Discover reports a rate limit.

---

## Contributing

This section is for working on the plugin itself.

```sh
./gradlew runIde          # launch a sandbox IDE with the plugin loaded
./gradlew buildPlugin     # produce build/distributions/luabox-jetbrains-<version>.zip
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
  packages/      package-management tool window + luabox-CLI runner
  settings/      luabox binary path + GitHub token (persisted), resolution + settings UI
  notification/  missing-binary editor banner
src/main/resources/META-INF/plugin.xml
```

**Versions.** Pinned in `gradle.properties`: `platformVersion`, `lsp4ijVersion`,
`pluginSinceBuild`, `pluginUntilBuild`. Bump there to retarget IDE builds.

## Releasing

Push a `v*` tag (e.g. `v0.1.0`) matching `pluginVersion` in `gradle.properties`.
The [release workflow](.github/workflows/release.yml) runs a hardened,
verify-then-latest pipeline in one run:

1. **create-release** — publishes the GitHub Release for the tag, *not* marked
   latest yet (`--latest=false`).
2. **publish** — builds the plugin (`buildPlugin`), generates `SHA256SUMS`, and
   uploads the zip + `SHA256SUMS` as release assets.
3. **verify** — independently re-downloads those assets, runs `sha256sum -c`,
   then unzips the plugin zip and structurally asserts the descriptor
   (`META-INF/plugin.xml` with `<id>com.luabox</id>` and a `<version>` matching
   the tag).
4. **mark-latest** — only if verify passes, `gh release edit --latest` takes the
   release live. A broken artifact can never become the latest release.

**This plugin is never auto-published.** There is no `publishPlugin`, no
signing, and no Marketplace token anywhere in the build or workflows. Uploading
a release to the [JetBrains Marketplace](https://plugins.jetbrains.com/) is a
deliberate manual step performed by the maintainer with the downloaded release
artifact.

---
