# cp-client-tunnel Specification

TypeScript/Node.js tunnel library (not a CLI). Imported by `cp-client` and other projects.

**Implementation Status:** See [FUNCTION_MAPPING.md](FUNCTION_MAPPING.md)

## Source Files

- [src/connection-info.ts](../../cp-client-tunnel/src/connection-info.ts) - Connection info resolution
- [src/proxy.ts](../../cp-client-tunnel/src/proxy.ts) - HTTP CONNECT proxy
- [src/tcp-forwarder.ts](../../cp-client-tunnel/src/tcp-forwarder.ts) - Port forwarding
- [src/process-discovery.ts](../../cp-client-tunnel/src/process-discovery.ts) - Tunnel process discovery
- [src/interfaces.ts](../../cp-client-tunnel/src/interfaces.ts) - `TunnelConnection`, `TunnelConfig`

## Architecture

**Two-step connection:**
1. TCP → HTTP CONNECT → raw socket
2. Socket handoff to SSH tooling

**Key features:**
- Proxy connection with endpoint resolution (see [pipe-cli/create_tunnel.SPEC.md#proxy-connection-architecture](pipe-cli/create_tunnel.SPEC.md#proxy-connection-architecture))
- Process discovery via process iteration
- Conflict resolution strategies (`keep-same`, `replace-existing`, etc.)
- Port handling (single ports and ranges)

**API:**
- `TunnelConnection.getStream()` - Returns socket post-HTTP CONNECT (pre-SSH)
- `TunnelConnection` metadata: runId, localPort, remotePort, pid, owner, `dispose()`

## References

- pipe-cli reference: [pipe-cli/create_tunnel.SPEC.md](pipe-cli/create_tunnel.SPEC.md)
- Function mapping: [FUNCTION_MAPPING.md](FUNCTION_MAPPING.md)
- Options reference: [pipe-cli/tunnel.SPEC.md](pipe-cli/tunnel.SPEC.md)
