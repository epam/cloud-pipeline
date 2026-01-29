# cp-client-common Specification

Shared utilities and types for cp-client-* projects.

## Source Files

- [src/logger.ts](../../cp-client-common/src/logger.ts) - `ILogger`, `LoggerBase`, `FileLogger` (framework-agnostic)
- [src/disposable.ts](../../cp-client-common/src/disposable.ts) - `IDisposable`, `disposeAll`
- [src/ports.ts](../../cp-client-common/src/ports.ts) - Port validation and helpers
- [src/files.ts](../../cp-client-common/src/files.ts) - File system utilities
- [src/types/](../../cp-client-common/src/types/) - CLI option types (`GlobalOptions`, `TunnelStartOptions`, etc.)

## Architecture

- **Framework-agnostic**: No VS Code dependencies
- **Workspace consumption**: Used by `cp-client`, `cp-client-tunnel`, `cp-client-api`
- **Minimal API**: Only exports required by consumers

## References

- Tunnel option types align with [pipe-cli/tunnel.SPEC.md](pipe-cli/tunnel.SPEC.md)
