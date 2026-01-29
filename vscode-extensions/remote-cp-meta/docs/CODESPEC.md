# Code Specification (remote-cp family)

- Language: English only; keep docs minimal and linked.
- Location: Specs under `cp-clientmeta/docs/` using `<project>.<component|command>.SPEC.md`; shared rules here.
  - **Exception**: pipe-cli reference specs use `cp-client-meta/docs/pipe-cli/<function-or-command>.SPEC.md` (e.g., `tunnel.SPEC.md`, `create_tunnel.SPEC.md`).
- **Implementation Tracking**: 
  - All features in `cp-client`, `cp-client-tunnel`, `cp-client-common` must be catalogued in `MAPPING.md`
  - **Before implementing**: Check MAPPING.md for pipe-cli function mappings and current status
  - **After completing**: Update status from ❌ (Missing) or ⚠️ (Partial) to ✅ (Implemented)
  - **When design changes**: Document workarounds or design differences in the mapping table notes
- Function Mapping Reference: See `cp-client-meta/docs/MAPPING.md` for cross-reference between pipe-cli and cp-client-tunnel implementations when implementing feature parity.
- Scope: `remote-cp` and `remote-cp-helper`; VS Code extensions.
- Prompts: Project prompts reside in `.vscode/prompts` per workspace configuration.
- Tunnel clients: Support hybrid selection between Python CLI and Node.js `cp-client-tunnel`; honor settings that choose command-line, internal, or both.
- VS Code specifics: Keep UI-dependent pieces (like OutputLogger) here; do not push VS Code APIs into `cp-client-*` projects.
- References: For tunnel behavior parity see `cp-client-meta/docs/pipe-cli/tunnel.SPEC.md`.
- Duplication guard: When editing instructions or docs, check for duplicate content/spec files and consolidate into a single referenced source; prefer linking over copying.

## File Naming Conventions

- TypeScript files: Use **kebab-case** (e.g., `tunnel-manager.ts`, `process-discovery.ts`).
- React components: Use **PascalCase** (e.g., `TunnelView.tsx`, `ConnectionPanel.tsx`).
- Exception: Configuration and declaration files may follow their ecosystem conventions (e.g., `tsconfig.json`, `vscode.d.ts`).
