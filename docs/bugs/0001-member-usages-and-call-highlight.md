# Bug 0001 — Member operations: no "Find Usages" and call name looks unhighlighted

**Status:** resolved
**Severity:** medium (navigation/DX; no crash or data loss)
**Component:** LSP integration (`pds lsp` via LSP4IJ) + semantic-token colours

## Resolution (2026-05-29)

Both hypotheses confirmed by driving `pds lsp` directly against a two-module
workspace (`pds.toml` + `cli.pds` + `context.pds`):

1. **Find Usages — server-side, stale binary.** The *current* `pds 0.1.0`
   handles it: `textDocument/references` on the `run` declaration returns **both**
   the declaration and the cross-module call site in `context.pds`, and it does
   so even when only `cli.pds` is open (the server loads the whole workspace from
   `rootUri`/`pds.toml`, not just open documents). So empty Find Usages means the
   IDE launched an **older `pds`** — fix is operational: `cargo install --path
   crates/pseudoscript`, then restart the server (Language Servers tool window).
   No plugin change.

2. **Call looks unhighlighted — fixed in the plugin.** `PDS_FUNCTION_CALL` now
   falls back to `FUNCTION_DECLARATION` instead of `FUNCTION_CALL` (plain
   foreground), so the LSP `method` overlay no longer downgrades a call the lexer
   already painted as `FUNCTION`. It remains a separate colour key, so calls can
   still be recoloured distinctly on the **Color Scheme → PseudoScript** page.
   (`PseudoScriptSyntaxHighlighter.kt`.)

## Summary

Cmd-clicking a callable (operation) declaration offers no usages, and a member
call such as `cli::DocCmd.run(path)` does not appear highlighted as a call.
Navigation to/from operations and their highlight feel second-class compared to
node/`data` declarations.

## Environment

- IDE: RustRover `RR-261.23567.140` (2026.1)
- Plugin: PseudoScript `0.1.0`
- LSP4IJ: `0.19.4`
- `pds`: build under test — **record `pds --version` / install path when reproducing** (see Hypothesis 1)
- OS: macOS (darwin)

## Steps to reproduce

1. Open a workspace (`pds.toml` at root) with two modules:

   `cli.pds`
   ```
   //! cli
   public container Cli;
   public component DocCmd for Cli {
     /// Generate the documentation site.
     public run(path: string): Result<void, IoError> { ... }
     write(site: doc::Site, outDir: string): Result<void, IoError>;
   }
   ```

   `context.pds`
   ```
   //! context
   public person Developer {
     public renderDocs(path: string): void {
       cli::DocCmd.run(path)
     }
   }
   ```
2. In `cli.pds`, put the caret on the `run` **declaration** and invoke
   Cmd-click (Go to / Show usages) or Find Usages (Alt+F7).
3. In `context.pds`, look at the `run` in `cli::DocCmd.run(path)`.

## Expected

- Step 2: Find Usages lists `cli::DocCmd.run(path)` (and any `self.run(...)`).
- Step 3: `run` is coloured as an operation call.

## Actual

- Step 2: no usages offered / empty.
- Step 3: `run` reads as plain text (no distinct colour).

## Investigation

The language layer is correct and test-pinned (see the `pseudoscript-lsp`
crate in the spec repo):

- **Cross-module member resolution works.** `cli::DocCmd.run(path)` from
  `context` resolves to `DocCmd.run` in `cli`
  (`resolve.rs::qualified_component_member_call_resolves_cross_module`), and the
  declaration itself resolves (`callable_declaration_resolves_to_itself`).
- **References span files.** Find-references from the `run` declaration includes
  the cross-module call site
  (`refs.rs::cross_module_operation_references_span_files`).
- **The server emits a semantic token for the call.** `run` in `X.run(args)` is
  a `method` token (`semantic.rs::member_call_is_a_method_token`).

The plugin layer is also wired correctly:

- The fallback lexer colours `run(` as `FUNCTION` even before the server
  responds — proven by `highlighting.feature` ("A qualified cross-module member
  call colours the call name"; "Result markers and self chaining").
- `PseudoScriptSemanticTokensColorsProvider` maps `method` →
  `FUNCTION` (declaration) / `FUNCTION_CALL` (call).
- `FUNCTION_CALL` falls back to `DefaultLanguageHighlighterColors.FUNCTION_CALL`
  and is registered on the **Color Scheme → PseudoScript** page.

So neither the resolver nor the plugin mapping is missing. That points the live
symptom at one of the following.

## Hypotheses (most likely first)

1. **Stale `pds` binary.** Member resolution / references shipped recently; the
   running server is likely an older `pds` that lacks them. The plugin launches
   whatever `pds` is on `PATH` (or the configured path), so an un-reinstalled
   binary explains the empty Find Usages entirely.
   - **Check:** `pds --version`; confirm the path the plugin launches (Language
     Servers tool window). Reinstall: `cargo install --path crates/pseudoscript`,
     then restart the server.

2. **`FUNCTION_CALL` is visually indistinct in the active scheme.** A call maps
   to `method` (no `declaration` modifier) → `FUNCTION_CALL`, whose default in
   most schemes is the plain foreground. So once the semantic overlay lands it
   *replaces* the lexer's `FUNCTION` (declaration) colour with a colour that
   looks like no highlight — the call appears to "lose" highlighting.
   - **Check:** Color Scheme → PseudoScript → "Semantic / Operation call"; give
     it a distinct colour and confirm the call recolours.

## Next steps

- Reproduce against a freshly `cargo install`-ed `pds`; capture `pds --version`
  and the Language Servers traffic (does a `textDocument/references` request go
  out on Find Usages? does `semanticTokens/full` return a `method` token for the
  call?).
- If usages still empty with a current binary: capture the `references` request
  params and response and attach here — that would be a real server/LSP4IJ bug.
- Consider giving "Semantic / Operation call" a non-default bundled colour so
  member calls are visibly highlighted out of the box, matching the lexer layer.

## Notes

- The resolver matches *call expressions* (`.run(...)`), not trigger/macro
  wiring. A `public` entry point invoked only via a trigger has no in-model call
  site, so Find Usages correctly returns nothing — verify the operation under
  test actually has a `.run(...)` caller before treating empty usages as a bug.
