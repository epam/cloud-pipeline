# cp-client CLI Specification

TypeScript/Node.js CLI reimplementation of `pipe-cli` tunnel commands.

**Implementation Status:** See [FUNCTION_MAPPING.md](FUNCTION_MAPPING.md)

## Source Files

- [src/cli.ts](../../cp-client/src/cli.ts) - Entry point, Commander.js setup
- [src/cli/commands/](../../cp-client/src/cli/commands/) - Command handlers (`tunnel-start.ts`, `tunnel-list.ts`, `tunnel-stop.ts`)
- [src/cli/utils.ts](../../cp-client/src/cli/utils.ts) - Background spawning, port checking
- [src/index.ts](../../cp-client/src/index.ts) - Library API (`pipeTunnelStart`, `pipeTunnelList`, `pipeTunnelStop`)

## Commands

```bash
npx pipe tunnel start <run_id> [options]  # Start tunnel
npx pipe tunnel list                       # List active tunnels
npx pipe tunnel stop [run_id]              # Stop tunnel(s)
```

## Architecture

- **Delegates to**: `cp-client-tunnel` library for tunnel operations
- **Background mode**: Spawns detached process, waits for port listening
- **Foreground mode**: Runs in current process with `-f|--foreground`
- **Flags**: Align with [pipe-cli/tunnel.SPEC.md](pipe-cli/tunnel.SPEC.md)

## References

- pipe-cli reference: [pipe-cli.SPEC.md](pipe-cli/pipe-cli.SPEC.md)
- Function mapping: [FUNCTION_MAPPING.md](FUNCTION_MAPPING.md)
