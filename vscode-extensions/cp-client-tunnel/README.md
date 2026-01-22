# cp-client-tunnel

Pure TypeScript/Node.js tunnel library for Cloud Pipeline.

Establishes proxy tunnel connections through HTTP CONNECT protocol and manages tunnel lifecycle independently from Python CLI.

## Architecture

- **HTTP CONNECT Proxy**: Two-step connection (TCP → HTTP CONNECT handshake → raw socket)
- **Process Discovery**: Iterates running processes to find existing tunnels
- **Stream API**: Returns Duplex stream post-CONNECT for caller to manage SSH or direct use

## Core API

### TunnelManager

Main entry point for tunnel operations.

```typescript
import { TunnelManager } from "cp-client-tunnel";

const manager = new TunnelManager({
  proxyHost: "proxy.example.com",
  proxyPort: 443,
  connectionTimeout: 30,
});

// Start tunnel
const tunnel = await manager.startTunnel(12345, {
  remotePort: 22,
});

// Get stream for SSH handshake
const stream = await tunnel.getStream();

// List all tunnels
const tunnels = await manager.listTunnels();

// Stop tunnel
await manager.stopTunnel(12345);
```

### ITunnelConnection

Represents active tunnel with access to proxy stream.

- `runId` - Cloud Pipeline run ID
- `localPort` - Listening local port (optional)
- `remotePort` - Target remote port
- `getStream()` - Returns Duplex after HTTP CONNECT (pre-SSH)
- `dispose()` - Cleanup

## Error Types

- `TunnelError` - Base tunnel error
- `TunnelTimeoutError` - Connection timeout
- `TunnelPortOccupiedError` - Port already in use
- `TunnelOwnerMismatchError` - Permission issue
- `TunnelConnectionError` - Connection failed
- `TunnelProxyError` - Proxy handshake failed

## References

- Python pipe-cli implementation: See `cp-client-meta/docs/pipe-cli/tunnel.SPEC.md`
- See [cp-client-meta/docs/cp-client-tunnel.SPEC.md](../cp-client-meta/docs/cp-client-tunnel.SPEC.md) for full specification
