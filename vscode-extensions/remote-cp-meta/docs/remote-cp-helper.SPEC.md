# remote-cp-helper Specification

- Purpose: Support functionality shared with `remote-cp` extension; keep helper logic VS Code aware where needed.
- Logging/UI: Use OutputChannel-bound loggers here; do not migrate VS Code APIs into `cp-client-*` projects.
- Docs rule: Keep helper-specific notes minimal; reference `remote-cp.tunnel.SPEC.md` for tunnel-related behavior shared across the extensions.
