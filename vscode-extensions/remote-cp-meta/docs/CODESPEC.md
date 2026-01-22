# Code Specification (remote-cp family)

- Language: English only; keep docs minimal and linked.
- Location: Specs under `docs/` using `<project>.<component|command>.SPEC.md`; shared rules here.
- Scope: `remote-cp` and `remote-cp-helper`; VS Code extensions.
- Prompts: Project prompts reside in `.vscode/prompts` per workspace configuration.
- Tunnel clients: Support hybrid selection between Python CLI and Node.js `cp-client-tunnel`; honor settings that choose command-line, internal, or both.
- VS Code specifics: Keep UI-dependent pieces (like OutputLogger) here; do not push VS Code APIs into `cp-client-*` projects.
- References: For tunnel behavior parity see `cp-client-meta/docs/pipe-cli/tunnel.SPEC.md`.
- Duplication guard: When editing instructions or docs, check for duplicate content/spec files and consolidate into a single referenced source; prefer linking over copying.
