# config

## BinaryCheck

`public component` · `config::BinaryCheck`

"Can we reach `pds`?" — the single check the LSP launcher, the banner, and the
diagram CLI wrapper all lean on, so the user gets one consistent answer.

**Relationships**

- _Parent_
  - for [config::Configuration](config.md#config-Configuration)
- _Inbound_
  - call [config::MissingBinaryBanner](config.md#config-MissingBinaryBanner) — isAvailable
  - call [config::MissingBinaryBanner](config.md#config-MissingBinaryBanner) — notFoundMessage
  - call [lsp::LanguageServer](lsp.md#lsp-LanguageServer) — isAvailable
  - call [lsp::LanguageServer](lsp.md#lsp-LanguageServer) — notFoundMessage

## Configuration

`public container` · `config::Configuration`

Settings, binary detection, and user-facing setup guidance.

**Relationships**

- _Parent_
  - for [main::PseudoScriptPlugin](main.md#main-PseudoScriptPlugin)

**Scenarios**

- **MissingBinaryBannerShown**
  - _given_ the configured `pds` path does not resolve
  - _when_ a `.pds` or `pds.toml` file is open
  - _then_ a warning banner offers to open Settings and the install page
  - _and_ the banner clears once a valid path is set

**Component diagram**

![Component diagram](../diagrams/config-Configuration-0.svg)

## MissingBinaryBanner

`public component` · `config::MissingBinaryBanner`

Warns atop `.pds` / `pds.toml` files when the binary is missing — highlighting
still works, but diagnostics, diagrams, and docs need it.

**Relationships**

- _Parent_
  - for [config::Configuration](config.md#config-Configuration)
- _Outbound_
  - call [config::BinaryCheck](config.md#config-BinaryCheck) — isAvailable
  - call [config::BinaryCheck](config.md#config-BinaryCheck) — notFoundMessage

**Sequence — Render**

![Sequence — Render](../diagrams/config-MissingBinaryBanner-0.svg)

## Settings

`public component` · `config::Settings`

Application-wide persistent store of the `pds` binary path. A blank value
falls back to the bare name `pds`, resolved on PATH.

**Relationships**

- _Parent_
  - for [config::Configuration](config.md#config-Configuration)
- _Inbound_
  - call [config::SettingsForm](config.md#config-SettingsForm) — setPdsPath

## SettingsForm

`public component` · `config::SettingsForm`

The settings page (Settings > Languages & Frameworks > PseudoScript).

**Relationships**

- _Parent_
  - for [config::Configuration](config.md#config-Configuration)
- _Outbound_
  - call [config::Settings](config.md#config-Settings) — setPdsPath
  - call [main::Intellij](main.md#main-Intellij) — refreshNotifications

**Sequence — ApplySettings**

![Sequence — ApplySettings](../diagrams/config-SettingsForm-0.svg)

