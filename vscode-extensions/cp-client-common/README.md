# cp-client-common

Shared utilities and types for Cloud Pipeline tunnel projects.

## Exports

### Disposable & Disposal

- `IDisposable` - Interface for disposable resources
- `Disposable` - Base class for resource lifecycle management
- `disposeAll()` - Utility to dispose array of resources

### Logging

- `ILogger` - Core logger interface
- `LoggerBase` - Base implementation (framework-agnostic)
- `FileLogger` - File-based logger with timestamp and level prefixing
- `LogLevel` - Enum for log levels (error, warn, info, debug, trace)

### Port Utilities

- `findRandomPort()` - Find random available port
- `findFreePort()` - Find free port with retries
- `findFreePortFaster()` - Faster port search using listen

### File Utilities

- `exists()` - Check if file exists
- `untildify()` - Expand ~ to home directory
- `normalizeToSlash()` - Convert backslashes to forward slashes

### Platform Detection

- `isWindows`, `isMacintosh`, `isLinux` - Platform checks

### Types

- `ITunnelInfo` - Tunnel metadata (PID, owner, ports, etc.)
- `ITunnelConfig` - Tunnel configuration
- `GlobalOptions` - Shared CLI options
- `TunnelStartOptions` - Tunnel start command options
- `TunnelStopOptions` - Tunnel stop command options
- `TunnelListOptions` - Tunnel list command options

## Usage

```typescript
import { 
  FileLogger, 
  findRandomPort, 
  TunnelStartOptions 
} from "cp-client-common";

const logger = new FileLogger("/tmp/tunnel.log", "info");
const port = await findRandomPort();
```

## Code Organization

Shared code-organization guidelines live in the meta project. See cp-client-meta/docs/CODESPEC.md#code-organization

Note for AI contributors: general coding instructions should be maintained in `cp-client-meta`. Prefer updating the meta spec and linking to it here instead of duplicating content.
