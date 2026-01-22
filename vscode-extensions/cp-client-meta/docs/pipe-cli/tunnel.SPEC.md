# pipe-cli Tunnel Reference (Read-Only)

- Status: Reference only. Do not modify `pipe-cli`. Use this spec to mirror behavior.
- Key files/functions (Python):
  - `pipe.py` tunnel commands around lines 1765-1900 (start/stop/list CLI entrypoints).
  - `src/utilities/ssh_operations.py`:
    - `create_foreground_tunnel()` – select-based socket relay loop.
    - `http_proxy_tunnel_connect()` – HTTP CONNECT handshake to edge proxy.
    - `setup_paramiko_transport()` / `setup_authenticated_paramiko_transport()` – SSH over proxy socket.
    - `find_tunnels()` – process discovery and argument parsing.
    - `TunnelArgs.compare()` – config diff for conflict strategies.
    - `serve_local_ports()` – bind/listen on local ports; `parse_ports()` – parse single/range inputs.
- Algorithms to mirror: TCP → HTTP CONNECT → socket → SSH; process iteration for tunnel discovery; conflict strategies (keep-same, replace-existing, replace-different, ignore-existing, ignore-owner); background readiness checks; port range validation.
- Options parity: Maintain flag behavior and defaults consistent with Python unless explicitly overridden in project specs.
