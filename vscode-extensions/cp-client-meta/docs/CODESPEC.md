# Code Specification

- **Language: English ONLY** — All documentation, code comments, commit messages, and specifications MUST be in English, regardless of the language used in conversation or requests. This is non-negotiable.
- Location: Place specifications under `docs/` using the pattern `<project>.<component|command|other_sub>.SPEC.md`; shared rules live here in `CODESPEC.md`.
- Scope: This meta project governs `cp-client`, `cp-client-common`, `cp-client-tunnel`, and read-only reference notes for `pipe-cli`.
- Source of truth: If behavior is unspecified, follow the existing Python `pipe-cli` tunnel implementation patterns (HTTP CONNECT first, then SSH over the obtained socket). See [pipe-cli reference](pipe-cli/pipe-cli.SPEC.md) for overview.
- **Function Mapping (Single Source of Truth)**: [FUNCTION_MAPPING.md](FUNCTION_MAPPING.md) is the authoritative reference for pipe-cli ↔ cp-client-tunnel function equivalences and implementation status. Do NOT duplicate function mapping tables in other documents; link to FUNCTION_MAPPING.md instead.
- Proxy/SSH: Use two-step connect (TCP → HTTP CONNECT → raw socket) and pass the socket to SSH tooling; do not shortcut unless explicitly specified.
- Process discovery: Prefer process iteration (e.g., `ps-list`) with arg parsing to detect tunnels; only fall back to port scans if explicitly requested.
- Stream API: `getStream()` should return a Duplex right after HTTP CONNECT (pre-SSH) for compatibility with callers that manage SSH themselves.
- Dependencies: Use npm workspaces (`"workspace:*"`) between `cp-client-*` projects; avoid VS Code-specific APIs here.
- Docs rule: `pipe-cli` is read-only; descriptions live under `docs/pipe-cli/` (e.g., `docs/pipe-cli/tunnel.SPEC.md`) and are for reference only.

## Anti-Duplication Rule

### Implementation Status
[FUNCTION_MAPPING.md](FUNCTION_MAPPING.md) is the **single source of truth** for:
- Function mappings (pipe-cli ↔ cp-client-tunnel)
- Implementation status (✅/⚠️/❌)
- Missing features list

**❌ NEVER**: Duplicate these in other spec files.  
**✅ ALWAYS**: Link to FUNCTION_MAPPING.md instead. Example: "See [FUNCTION_MAPPING.md#connection-management](FUNCTION_MAPPING.md#connection-management)."

### Type Definitions and Code Signatures

**❌ NEVER**: Copy/paste type definitions, class signatures, or function declarations into specification files.  
**✅ ALWAYS**: Link to source code instead. Example: "See [types.ts](../../cp-client-api/src/types.ts)" or "Defined in [cluster-api.ts](../../cp-client-api/src/cluster-api.ts)."

**Specification Guidelines:**
- Keep specs **minimal** (target: 30-50 lines for simple libraries)
- Provide **architecture overview** and **usage examples** only
- Link to **source files** for type definitions and API signatures
- Reference **FUNCTION_MAPPING.md** for implementation status
- Avoid verbose code examples; one concise example is sufficient

**Example structure:**
```markdown
# Project Specification
Purpose: Brief description
Source Files: Links to key files
Usage: One minimal example
References: Links to FUNCTION_MAPPING.md and related specs
```

### Code Organization
- Colocation: Place function/type declarations as close as possible to their usage. If a helper is only used by a single CLI command, keep it in that command (or a sibling helper in `src/cli/`). Only promote utilities to `src/index.ts` if they serve external, reusable programmatic use.

## File Naming Conventions

- TypeScript files: Use **kebab-case** (e.g., `tunnel-manager.ts`, `process-discovery.ts`, `start-tunnel-forward.ts`).
- React components: Use **PascalCase** (e.g., `TunnelView.tsx`, `ConnectionPanel.tsx`).
- Exception: Configuration and declaration files may follow their ecosystem conventions (e.g., `tsconfig.json`, `index.ts`).

## Code Organization

- Colocation: Keep function and type declarations as close as possible to where they are used. CLI-only helpers should live under `src/cli/` or inside the command file itself.
- Library surface: Limit `src/index.ts` to reusable programmatic APIs intended for external consumption. Do not export CLI-only helpers without added value.
- Minimal cross-layer coupling: CLI commands may depend on `cp-client-common` types and `cp-client-tunnel` primitives directly. Avoid pass-through wrappers unless they enforce validation or add behavior.
- Single source of truth: Shared guidelines live in this meta repo (`cp-client-meta/docs`). Individual package READMEs should link here instead of duplicating content.
- AI contributor note: When adding or changing general guidelines, first search this meta project for existing documents and update them here. If similar guidance exists elsewhere, consolidate it into `cp-client-meta` and replace duplicates with links.
- **Debugging**: Common debugging issues and solutions are documented in [debug.NOTES.md](debug.NOTES.md) (e.g., source map configuration for VS Code debugger).
