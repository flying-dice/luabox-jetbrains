# luabox for JetBrains IDEs

[![CI](https://github.com/flying-dice/luabox-jetbrains/actions/workflows/ci.yml/badge.svg)](https://github.com/flying-dice/luabox-jetbrains/actions/workflows/ci.yml)

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
| Dependency management — discover on luarocks.org, install, outdated/update/remove | `luabox` CLI (`search`, `outdated`, `add`, `remove`, `update`) |

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
luabox Packages**) is an npm-like GUI over the luabox CLI's
[luarocks.org](https://luarocks.org)-as-registry commands. It shells out to the
same `luabox` binary the language server uses, in your project's base
directory, and renders the JSON it prints. Registry search and install are
**anonymous** — no sign-in needed.

- **Discover.** Type a query and press Enter to run `luabox search` against
  luarocks.org. Each result is a card — rock name, latest version, version
  count, and description (when the registry has one) — with an **Install**
  button that runs a bare `luabox add <name>`, which resolves the highest
  available version and edits the project's rockspec (the package manifest).
- **Installed.** Every dependency from `luabox outdated` is listed with its
  current pin. Registry and git deps that have a newer version show an
  **outdated: current → latest** indicator and an **Update** button
  (`luabox update <name>`); path/workspace deps are used in place, and a `url`
  dep is pinned by sha256 (immutable) — neither has an Update button. **Remove**
  (`luabox remove <name>`) works for every dependency kind, wherever it's
  declared (rockspec or `luabox.toml`). The toolbar has **Refresh** and an
  outdated-count label.
- The Installed view refreshes automatically after any install/update/remove and
  whenever `luabox.toml` or the project's root `*.rockspec` changes on disk.

Edge states are handled: no `luabox.toml` → a "run `luabox new`" hint; the
`luabox` binary not found → a configure/install prompt; a GitHub rate-limit
(403) on a **git-source** dependency operation → a notification linking to
**Sign in with GitHub**.

**Requires a `luabox` CLI build with the luarocks.org registry pivot** (the
`search`/`outdated` JSON schemas this panel parses, and the rockspec-editing
`add`). Device-flow **sign-in** (below) needs `luabox` ≥ 0.1.4.

## Authentication (optional)

GitHub sign-in is **optional** and no longer fronts the Packages tool window:
the luarocks.org registry (search/install) is always anonymous. Signing in only
benefits **git-source** dependencies — it raises the GitHub API rate limit and
grants access to private repositories for `luabox outdated`/`luabox update`'s
release probing. The path is **device-flow sign-in**, in a footer auth bar
below Discover/Installed — no token to paste, nothing stored by the plugin.

- The luabox Packages tool window has a footer **auth bar**. When you're not
  signed in it shows **GitHub sign-in: optional (for git-source
  dependencies)**; there's also a `luabox.signInGithub` action (searchable via
  *Find Action*).
- Clicking **Sign in with GitHub** runs `luabox login`. A notification shows
  your one-time **user code** and the verification URL. **Copy code & open
  browser** copies the code and opens <https://github.com/login/device>; enter
  the code there and authorize.
- The plugin waits (the code is valid ~15 min; **Cancel** aborts). On success
  the auth bar shows **✓ GitHub: signed in as _you_** with a **Sign out**
  button.
- The **CLI stores the token in your OS keychain**, so afterwards git-source
  dependency operations authenticate automatically — **the plugin passes no
  token** for signed-in users. **Sign out** runs `luabox logout` (clears the
  keychain).
- Device-flow sign-in requires the **`luabox` CLI ≥ 0.1.4**. On an older CLI the
  sign-in reports that and links to the luabox releases.

The **GitHub token** setting (below) is an optional **PAT override**, also
git-source-only, for restricted orgs or GitHub Enterprise; when set it takes
precedence over the keychain sign-in. In that mode the auth bar shows *(token
override)*.

## Configuration

**Settings → Languages & Frameworks → luabox**:

- **Path to the luabox binary** — a bare name (default `luabox`) is resolved on
  `PATH`, then in `~/.luabox/bin`. The server is launched as `<path> lsp`, and
  the package-management commands run the same binary.
- **GitHub token override** (optional, git-source dependencies only) — a PAT
  passed to the CLI as `LUABOX_GITHUB_TOKEN`. Registry search/install
  (luarocks.org) never uses it; it only affects `luabox outdated`/`luabox
  update`'s GitHub release probing and private-repo access. Device-flow **Sign
  in with GitHub** (see [Authentication](#authentication-optional)) is the
  normal path; use this field only as an override for restricted orgs or GitHub
  Enterprise. When set it takes precedence over the keychain sign-in.

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
