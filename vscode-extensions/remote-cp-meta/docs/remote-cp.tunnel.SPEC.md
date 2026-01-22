# remote-cp Tunnel Specification

- Goal: Allow both legacy Python CLI tunnels and new Node.js `cp-client-tunnel` implementation; selectable via setting `remote-cp.cp-client` (values: command line (download), nodejs (internal), both).
- Integration: Implement factory in tunnel layer to choose between CpClient (Python) and TunnelManagerClient (Node.js) while keeping the `PipeTunnelBase` interface.
- UI flow: `askUserForPipeTunnel` options should include:
  - Enter local port manually
  - Create tunnel within VSCode (on local port available) — default after 10s timeout, uses Node.js library with auto port selection
  - Create tunnel within VSCode (internal) — stream-based (no local port) via `getStream()` from Node.js tunnel
  - Discovered tunnels list (existing behavior)
  - Execute new tunnel (reusable)
  - Execute new tunnel (bound)
- Stream mode: For "internal" option, consume Duplex returned after HTTP CONNECT (pre-SSH) to feed authResolver proxyStream.
- Backward compatibility: Preserve existing Python CLI path; keep CpClient/CpClientBase in `remote-cp` only.
- Logging: OutputLogger remains here (VS Code LogOutputChannel binding); other loggers live in `cp-client-common`.
