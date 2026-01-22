# cp-client-tunnel Specification

- Purpose: Pure TypeScript/Node.js tunnel library; no Python CLI calls.
- Architecture: Two-step connect (TCP → HTTP CONNECT → raw socket) then handoff to SSH tooling; mirror Python `pipe-cli` semantics unless explicit override.
- API:
  - `startTunnel(runId, options): Promise<TunnelConnection>`
  - `listTunnels(): Promise<ITunnelInfo[]>`
  - `stopTunnel(runId?, localPort?, options?): Promise<void>`
  - `TunnelConnection.getStream(): Promise<Duplex>` returns socket post-HTTP CONNECT (pre-SSH) for authResolver compatibility.
  - `TunnelConnection` exposes metadata: runId, localPort, remotePort, pid, owner, and `dispose()`.
- Options: Support full flag set from `pipe-cli tunnel start/stop/list` (see `docs/pipe-cli/tunnel.SPEC.md`). Implement conflict strategies: keep-same, replace-existing, replace-different, ignore-existing, ignore-owner.
- Process discovery: Iterate processes (e.g., `ps-list`) and parse args to find tunnels; include owner/runId/ports mapping.
- Background mode: Spawn child process; wait for readiness by checking bound ports and/or health probe; support timeouts.
- Port handling: Accept single ports and ranges; ensure local/remote counts align; forbid ranges with SSH direct mode if required by spec.
- Error model: Provide specific errors (timeout, port occupied, owner mismatch, sensitive run rejection if implemented) matching Python behavior when applicable.
- Dependencies: Import shared types/helpers from `cp-client-common` via workspace dependency.
- Tests: Mirror remote-cp tunnel tests where applicable; add cases for HTTP CONNECT failure, port conflict, and getStream without local port.
