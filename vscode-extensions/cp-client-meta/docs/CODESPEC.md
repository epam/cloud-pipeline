# Code Specification

- Language: English only; keep docs minimal and link out to specifics rather than inlining everything.
- Location: Place specifications under `docs/` using the pattern `<project>.<component|command|other_sub>.SPEC.md`; shared rules live here in `CODESPEC.md`.
- Scope: This meta project governs `cp-client`, `cp-client-common`, `cp-client-tunnel`, and read-only reference notes for `pipe-cli`.
- Source of truth: If behavior is unspecified, follow the existing Python `pipe-cli` tunnel implementation patterns (HTTP CONNECT first, then SSH over the obtained socket).
- Proxy/SSH: Use two-step connect (TCP → HTTP CONNECT → raw socket) and pass the socket to SSH tooling; do not shortcut unless explicitly specified.
- Process discovery: Prefer process iteration (e.g., `ps-list`) with arg parsing to detect tunnels; only fall back to port scans if explicitly requested.
- Stream API: `getStream()` should return a Duplex right after HTTP CONNECT (pre-SSH) for compatibility with callers that manage SSH themselves.
- Dependencies: Use npm workspaces (`"workspace:*"`) between `cp-client-*` projects; avoid VS Code-specific APIs here.
- Docs rule: `pipe-cli` is read-only; descriptions live under `docs/pipe-cli/` (e.g., `docs/pipe-cli/tunnel.SPEC.md`) and are for reference only.
- Duplication guard: When editing instructions or docs, check for duplicate content/spec files and consolidate into a single referenced source; prefer linking over copying.
 - Colocation: Place function/type declarations as close as possible to their usage. If a helper is only used by a single CLI command, keep it in that command (or a sibling helper in `src/cli/`). Only promote utilities to `src/index.ts` if they serve external, reusable programmatic use.

## Code Organization

- Colocation: Keep function and type declarations as close as possible to where they are used. CLI-only helpers should live under `src/cli/` or inside the command file itself.
- Library surface: Limit `src/index.ts` to reusable programmatic APIs intended for external consumption. Do not export CLI-only helpers without added value.
- Minimal cross-layer coupling: CLI commands may depend on `cp-client-common` types and `cp-client-tunnel` primitives directly. Avoid pass-through wrappers unless they enforce validation or add behavior.
- Single source of truth: Shared guidelines live in this meta repo (`cp-client-meta/docs`). Individual package READMEs should link here instead of duplicating content.
- AI contributor note: When adding or changing general guidelines, first search this meta project for existing documents and update them here. If similar guidance exists elsewhere, consolidate it into `cp-client-meta` and replace duplicates with links.
