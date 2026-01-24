# cp-client

Cloud Pipeline CLI tool for tunnel management.

Provides `pipe tunnel` command with full option support for starting, stopping, and listing tunnels.

## Installation

```bash
npm install -g cp-client
```

Or use directly with npx:

```bash
npx pipe tunnel list
```

## Commands

### pipe tunnel list

List all active tunnel connections.

```bash
pipe tunnel list [options]
```

Options:
- `-v, --log-level <level>` - Log level (ERROR, WARNING, INFO, DEBUG)
- `-u, --user <user>` - User name (admin only)
- `--debug` - Enable debug logging
- `--trace` - Enable trace logging

### pipe tunnel start

Start a tunnel to a Cloud Pipeline run.

```bash
pipe tunnel start <run_id> [options]
```

Options:
- `-lp, --local-port <port>` - Local port (single or range 4567-4569)
- `-rp, --remote-port <port>` - Remote port (default 22)
- `-f, --foreground` - Run tunnel in foreground
- `-s, --ssh` - Configure passwordless SSH
- `-d, --direct` - Direct connection without proxy
- `-ks, --keep-same` - Skip if same config already exists
- `-re, --replace-existing` - Replace existing tunnel
- `--ignore-existing` - Ignore existing tunnels
- And many more (see spec)

### pipe tunnel stop

Stop a running tunnel.

```bash
pipe tunnel stop [run_id] [options]
```

Options:
- `-lp, --local-port <port>` - Local port to stop
- `-f, --force` - Force stop (SIGKILL)
- `-ts, --timeout-stop <seconds>` - Timeout for graceful stop

## Examples

```bash
# List active tunnels
pipe tunnel list

# Start tunnel to run 12345, listen on port 4567
pipe tunnel start 12345 --local-port 4567 --remote-port 22

# Start tunnel in foreground
pipe tunnel start 12345 -f

# Stop tunnel for run 12345
pipe tunnel stop 12345

# Stop tunnel on specific port
pipe tunnel stop --local-port 4567
```

## Configuration

Set `CP_PLATFORM_URL` environment variable to override default proxy:

```bash
export CP_PLATFORM_URL=https://my-edge.cloud-pipeline.com
pipe tunnel list
```

Logs are saved to `~/.pipe/logs/tunnel.log`

## Library API

For programmatic use, the package exports `TunnelManager` for direct tunnel operations. CLI-specific helpers are kept internal to command handlers.

```typescript
import { TunnelManager } from "cp-client";

const manager = new TunnelManager({ 
  proxyHost: "proxy.example.com", 
  proxyPort: 443 
});

// Start a tunnel
const tunnel = await manager.startTunnel(12345, { 
  runId: 12345, 
  remotePort: 22 
});

// List tunnels
const tunnels = await manager.listTunnels();

// Stop a tunnel
await manager.stopTunnel(12345);

manager.dispose();
```

## Project Structure

```
src/
├── cli.ts                    # Main CLI entry point
├── index.ts                  # Library API exports (reusable helpers only)
└── cli/
    ├── types.ts              # CLI type definitions
    ├── utils.ts              # Utilities (config, port checking, background spawn)
    └── commands/
        ├── index.ts          # Command exports
        ├── tunnel-list.ts    # List command handler
        ├── tunnel-start.ts   # Start command handler
        └── tunnel-stop.ts    # Stop command handler
```

## Code Organization

Shared code-organization guidelines live in the meta project. See cp-client-meta/docs/CODESPEC.md#code-organization

Note for AI contributors: general coding instructions should be maintained in `cp-client-meta`. Prefer updating the meta spec and linking to it here instead of duplicating content.


## How It Works

### Background Mode (default)

When starting a tunnel without `--foreground`:
1. Parent process spawns detached child process
2. Child process runs with `--foreground --ignore-existing` flags
3. Parent waits for port to be listening
4. Parent exits, leaving child running as daemon
5. Logs written to `~/.pipe/logs/tunnel-<runId>.log`

### Foreground Mode

With `--foreground` flag:
- Process stays alive and keeps tunnel open
- Ctrl+C to stop
- Output goes to console

## References

- See [cp-client-meta/docs/cp-client-cli.SPEC.md](../cp-client-meta/docs/cp-client-cli.SPEC.md) for full specification
