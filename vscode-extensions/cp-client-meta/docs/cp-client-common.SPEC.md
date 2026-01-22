# cp-client-common Specification

- Purpose: Shared utilities and types for tunnel-related projects.
- Interfaces: Provide `IDisposable`, `ILogger`, `LoggerBase`, `FileLogger`, `disposeAll` and keep them framework-agnostic; do not include VS Code-specific `OutputLogger`.
- Utilities: Port helpers (`ports.ts`), file helpers (`files.ts` set), CLI option types (e.g., `GlobalOptions`, `TunnelStartOptions`, `TunnelStopOptions`, `TunnelListOptions`).
- Exports: Make types and helpers consumable by `cp-client` and `cp-client-tunnel` via workspace dependency.
- Style: No VS Code imports; keep ASCII; surface minimal API required by consumers.
- References: For tunnel option parity, align with `cp-client-meta/docs/pipe-cli/tunnel.SPEC.md`.
