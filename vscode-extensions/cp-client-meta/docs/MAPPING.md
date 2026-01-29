# Mapping: pipe-cli ↔ cp-client-* Ecosystem

Comprehensive cross-reference between pipe-cli Python implementation and all cp-client-* TypeScript/Node.js library projects.

**Main Reference:** See [pipe-cli/create_tunnel.SPEC.md](pipe-cli/create_tunnel.SPEC.md) for full call tree and detailed behavior.

This document governs implementation of pipe-cli features across all components:
- **cp-client-api** - Cloud Pipeline API client (async HTTP endpoints)
- **cp-client-tunnel** - SSH tunnel management and connection
- **cp-client-common** - Shared types, logger, and utilities
- **cp-client** - CLI application interface
- Future projects implementing pipe-cli functionality

## 🎯 SINGLE SOURCE OF TRUTH

**⚠️ CRITICAL**: This is the **ONLY** authoritative location for ALL pipe-cli implementation across the ecosystem:

- Function equivalences between pipe-cli and all cp-client-* projects
- Implementation status tracking (✅/⚠️/❌) per component
- API method coverage and pipeline stage status
- Data type mappings between pipe-cli models and TypeScript interfaces
- Missing features inventory
- Design decisions deviating from pipe-cli

**❌ DO NOT** duplicate status tables in other spec files. Use them for:
- Design details and implementation hints
- Usage examples and API signatures
- Architecture diagrams

Reference this file as the source of truth in code reviews, checklists, and status tracking.

### Link Pattern from Other Spec Files

```markdown
# CORRECT: Link to MAPPING for status

For ClusterAPI implementation status, see [MAPPING.md#api-cluster](MAPPING.md#api-cluster).

# In cp-client-api.SPEC.md, don't repeat the table from here. 
# Instead, provide implementation details and link back.
```

### Problematic Patterns to Avoid

❌ Copying function status tables into individual project specs  
❌ Maintaining separate "implementation status" lists in different files  
❌ Documenting API methods in both cp-client-api.SPEC.md AND MAPPING.md as sources of truth  
❌ Updating status in code comments instead of here

---

**Examples of correct references from other files:**
```markdown
✅ GOOD: See [MAPPING.md#connection-management](MAPPING.md#connection-management)
✅ GOOD: For implementation status, see MAPPING.md
❌ BAD:  Copying the table below into create_tunnel.SPEC.md
❌ BAD:  Maintaining separate status list in cp-client-tunnel.SPEC.md
```

## 📋 IMPLEMENTATION GUIDE

### How to Use This Document

1. **Before implementing a feature**: Find it in the table below and check its current status
2. **During implementation**: Reference the pipe-cli function specification and cp-client counterpart
3. **After completing implementation**: 
   - Change status from ❌ or ⚠️ to ✅ **IN THIS FILE ONLY**
   - Add implementation notes in the row (e.g., file path, design differences)
   - Do NOT update status anywhere else
4. **When design differs from pipe-cli**: Document the difference in parentheses after status
5. **For partial implementations**: Keep ⚠️ and note what's missing between pipe-cli and current state

### Status Legend

- ✅ **Implemented** - Fully working, matches pipe-cli behavior (or documented alternative)
- ⚠️ **Partial** - Partially working; see notes for what's missing
- ❌ **Missing** - Not implemented; required for pipe-cli feature parity
- 🔄 **In Progress** - Currently being worked on

### How to Update This Table

When updating status, format notes as:
```
✅ Implemented (path: cp-client-tunnel/src/tunnel-manager.ts | note about design if needed)
```

---

## 📊 Data Type Mapping

Maps between pipe-cli Python model classes and cp-client TypeScript interfaces.

| pipe-cli Model | Location | cp-client TypeScript | Location | Status |
|---|---|---|---|---|
| **PipelineRunModel** | `src/model/pipeline_run_model.py` | `PipelineRunModel` | `cp-client-api/src/types.ts` | ✅ Mapped |
| **ClusterNodeModel** | `src/model/cluster_node_model.py` | `ClusterNodeModel` | `cp-client-api/src/types.ts` | ✅ Mapped |
| **ClusterInstanceTypeModel** | `src/model/cluster_instance_type_model.py` | `ClusterInstanceTypeModel` | `cp-client-api/src/types.ts` | ✅ Mapped |
| **RunParameter** | `src/model/pipeline_run_model.py` | `RunParameter` | `cp-client-api/src/types.ts` | ✅ Mapped |
| **APIResponse** | `src/api/base.py` (API wrapper) | `APIResponse<T>` | `cp-client-api/src/types.ts` | ✅ Mapped |
| **TunnelInfo** | `src/model/tunnel_info.py` | `TunnelInfo` | `cp-client-tunnel/src/connection-info.ts` | ✅ Mapped |
| **RunConnectionInfo** | `src/utilities/ssh_operations.py` | `RunConnectionInfo` | `cp-client-tunnel/src/connection-info.ts` | ✅ Mapped |
| **Endpoint** (run host/port) | `(str, int)` tuple | `Endpoint` | `cp-client-tunnel/src/interfaces.ts` | ✅ Mapped |

### Data Field Correspondence

#### PipelineRunModel

| pipe-cli Field | Type | cp-client Field | Type | Notes |
|---|---|---|---|---|
| `id` | `int` | `id` | `number` | Run identifier |
| `status` | `str` | `status` | `string` | e.g., "RUNNING", "SUCCESS" |
| `owner` | `str` | `owner` | `string` | Run initiator |
| `pod_ip` | `str` | `podIP` | `string` | Kubernetes pod IP |
| `ssh_password` | `str` | `sshPassword?` | `string` | SSH credentials |
| `initialized` | `bool` | `initialized` | `boolean` | SSH readiness flag |
| `sensitive` | `bool` | `sensitive` | `boolean` | Contains sensitive data |
| `platform` | `str` | `platform` | `string` | OS type (linux/windows) |
| `pipeline_run_parameters` | `list[RunParameter]` | `pipelineRunParameters?` | `RunParameter[]` | Run parameters |

#### ClusterNodeModel

| pipe-cli Field | Type | cp-client Field | Type | Notes |
|---|---|---|---|---|
| `name` | `str` | `name` | `string` | Node identifier |
| `pipeline_run` | `PipelineRunModel?` | `pipelineRun?` | `PipelineRunModel` | Associated run |
| `created` | `str` | `created?` | `string` | Creation timestamp |
| `labels` | `dict[str, str]` | `labels?` | `Record<string, string>` | Node labels |

#### ClusterInstanceTypeModel

| pipe-cli Field | Type | cp-client Field | Type | Notes |
|---|---|---|---|---|
| `name` | `str` | `name` | `string` | Instance type name (e.g., "m5.large") |
| `vcpu` | `int` | `vcpu` | `number` | Virtual CPU count |
| `gpu` | `int?` | `gpu?` | `number` | GPU count |
| `memory` | `int?` | `memory?` | `number` | Memory in MB |

---

## 🔄 Entry Points

| Module | pipe-cli | cp-client-tunnel | cp-client | Notes |
|--------|----------|------------------|-----------|-------|
| **Main Entry** | `create_tunnel()` | `TunnelManager.createTunnel()` | `tunnelStartAction()` (CLI wrapper) | Initiates tunnel creation process |

## 🔌 API Layer (cp-client-api)

Maps to `pipe-cli/src/api/*.py` classes. Implements HTTP client for Cloud Pipeline REST API.

### Cluster API

Source: [pipe-cli/src/api/cluster.py](../../../pipe-cli/src/api/cluster.py)

| Method | pipe-cli | cp-client-api | cp-client-tunnel | cp-client | Status |
|--------|----------|---|---|---|---|
| **get_edge_external_url(region)** | cluster.py:71-76 | `ClusterAPI.getEdgeExternalUrl()` | ✅ Uses API | — | ✅ Implemented (cp-client-api/src/cluster-api.ts) |
| **list()** | cluster.py:26-34 | `ClusterAPI.listNodes()` | — | — | ✅ Implemented (cp-client-api/src/cluster-api.ts) |
| **get(name)** | cluster.py:36-40 | `ClusterAPI.getNode()` | — | — | ✅ Implemented (cp-client-api/src/cluster-api.ts) |
| **terminate_node(name)** | cluster.py:42-45 | `ClusterAPI.terminateNode()` | — | — | ✅ Implemented (cp-client-api/src/cluster-api.ts) |
| **list_instance_types()** | cluster.py:47-56 | `ClusterAPI.listInstanceTypes()` | — | — | ✅ Implemented (cp-client-api/src/cluster-api.ts) |
| **download_usage_report()** | cluster.py:58-62 | *Not implemented* | — | — | ❌ Not required (file download not needed for tunnel) |

### Run API

Source: [pipe-cli/src/api/pipeline_run.py](../../../pipe-cli/src/api/pipeline_run.py)

| Method | pipe-cli | cp-client-api | cp-client-tunnel | Status |
|--------|----------|---|---|---|
| **get(run_id)** | pipeline_run.py | `RunAPI.getRun()` | ✅ Uses API | ✅ Implemented (cp-client-api/src/run-api.ts) |
| **is_initialized** check | Property | `RunAPI.isRunInitialized()` | ✅ Uses API | ✅ Implemented (cp-client-api/src/run-api.ts) |

## 📡 Tunnel Management Layer (cp-client-tunnel)

Source: [pipe-cli/src/utilities/ssh_operations.py](../../../pipe-cli/src/utilities/ssh_operations.py)

### Connection Info Resolution

| Purpose | pipe-cli | cp-client-tunnel | Status |
|---------|----------|---|---|
| **get_conn_info()** | ssh_operations.py:260-280 | `getRunConnectionInfo()` (connection-info.ts) → **now uses cp-client-api** | ✅ Implemented (connection-info.ts, delegates to Cluster/Run APIs) |
| **get_custom_conn_info()** | ssh_operations.py:283-297 | `getCustomConnectionInfo()` (connection-info.ts) → **now uses cp-client-api** | ✅ Implemented |

### Configuration & Parsing

| Purpose | pipe-cli | cp-client-tunnel | cp-client | Status |
|---------|----------|---|---|---|
| **Port Parsing** | `parse_ports()` with range support | Internal (no range support) | CLI option parsing | ⚠️ Ranges not supported in library |
| **Run ID Parsing** | `parse_run_identifier()` | Assumed numeric in config | CLI validation | ⚠️ Minimal validation |
| **Tunnel Arg Parsing** | `parse_tunnel_proc_args()`, `TunnelArgs.from_args()` | `parseTunnelArgs()` (process-discovery.ts) | Limited | ✅ Similar logic |

### Discovery & Validation

| Purpose | pipe-cli | cp-client-tunnel | cp-client | Status |
|---------|----------|---|---|---|
| **Find Existing Tunnels** | `find_tunnels()` | `findExistingTunnels()` (tunnel-manager.ts) | CLI reuses tunnel list | ✅ Implemented |
| **Check for Conflicts** | `check_existing_tunnels()` | *Not implemented* | *Skipped* | ❌ Missing critical feature |
| **Check Port Availability** | `check_local_ports()` | *Not implemented* | *Skipped* | ❌ Missing critical feature |
| **Find Serving Processes** | `find_serving_procs()` + OS variants | Integrated in `findExistingTunnels()` (ps-list) | *Skipped* | ⚠️ Limited implementation |
| **Process Details** | `get_proc_details()` | Partial metadata in process objects | *Not implemented* | ⚠️ Limited |

### Connection Management

| Purpose | pipe-cli | cp-client-tunnel | cp-client | Status |
|---------|----------|---|---|---|
| **Route Tunnel** | `create_tunnel_to_run()` / `create_tunnel_to_host()` | Implicit in `TunnelConnection` setup | `startTunnelForeground()` / `startTunnelBackground()` | ✅ Implemented |
| **Create Connection** | `create_foreground_tunnel()` | `TunnelConnection.getStream()` | Wrapper around `TunnelManager` | ⚠️ Partial (no event loop management in library) |
| **Bind Local Ports** | `serve_local_ports()` | `TcpForwarder.start()` (tcp-forwarder.ts) | Uses library version | ✅ Implemented |
| **HTTP Proxy Connect** | `http_proxy_tunnel_connect()` | `httpProxyTunnelConnect()` (proxy.ts) | Uses library version | ✅ Implemented |
| **Direct Connect** | `direct_connect()` | Custom logic or via proxy | Uses library version | ⚠️ Partial (no direct connection fallback) |

### Lifecycle Management

| Purpose | pipe-cli | cp-client-tunnel | cp-client | Status |
|---------|----------|---|---|---|
| **Create Tunnel** | `create_tunnel()` | `TunnelManager.createTunnel()` | `tunnelStartAction()` | ✅ Implemented |
| **Stop Tunnel** | `kill_tunnel()` | `TunnelManager.stopTunnel()` | `tunnelStopAction()` | ✅ Implemented |
| **List Tunnels** | `list_tunnels()` | `TunnelManager.listTunnels()` | `tunnelListAction()` | ✅ Implemented |
| **Background Spawn** | `create_background_tunnel()` with subprocess | *Not implemented* | `startTunnelBackground()` (spawn detached) | ⚠️ CLI only, not in library |
| **Process Cleanup** | Signal handlers, graceful shutdown | `dispose()` on TunnelConnection | Managed by CLI | ⚠️ Partial graceful shutdown |

## 🔐 SSH Support Layer

Source: [pipe-cli/src/utilities/ssh_operations.py](../../../pipe-cli/src/utilities/ssh_operations.py)

| Purpose | pipe-cli | cp-client-tunnel | cp-client | Status |
|---------|----------|---|---|---|
| **SSH Config** | `configure_ssh()` + Windows/Linux variants | *Not implemented* | Delegated to `--ssh` flag in tunnel start | ❌ Not in library |
| **Key Generation** | `generate_remote_openssh_and_putty_keys()` | *Not implemented* | External (not implemented) | ❌ Missing |
| **Key Management** | `copy_remote_putty_and_private_keys()`, `remove_ssh_keys()` | *Not implemented* | External | ❌ Missing |

## 🛠️ Common Types & Utilities (cp-client-common)

| Purpose | pipe-cli | cp-client-common | Status |
|---------|----------|---|---|
| **ILogger** | logging.Logger | `ILogger` interface | ✅ Implemented (cp-client-common/src/logger.ts) |
| **Endpoint** | tuple(str, int) | `Endpoint` interface | ✅ Implemented (cp-client-tunnel/src/interfaces.ts) |
| **Platform Detection** | `is_windows()`, `is_mac()`, `is_linux()` | `getPlatform()` (platform.ts) | ✅ Implemented (cp-client-common/src/platform.ts) |
| **User Detection** | `get_current_user()`, `has_different_owner()` | *Not implemented* | ⚠️ Skipped |
| **Port Utilities** | `stringify_ports()`, find_local_ports...() | Internal port checks | ⚠️ Partial |
| **Error Types** | `TunnelError` exceptions | `TunnelError` classes | ✅ Similar coverage |

---

## 🚀 Implementation Roadmap

### Phase 1: Foundation (✅ COMPLETE)

- ✅ cp-client-api: Cluster.getEdgeExternalUrl()
- ✅ cp-client-api: Run.get() and isInitialized()
- ✅ cp-client-tunnel: Integration with cp-client-api
- ✅ cp-client-tunnel: getRunConnectionInfo() → uses cp-client-api
- ✅ cp-client-tunnel: getCustomConnectionInfo() → uses cp-client-api
- ✅ cp-client-common: ILogger, getPlatform(), Endpoint types
- ✅ Data type mappings complete (TypeScript interfaces for all core models)

### Phase 2: High Priority (UPCOMING)

1. **Conflict Resolution** (`check_existing_tunnels()`)
   - Implement in cp-client-tunnel
   - Add flags: `--keep-existing`, `--keep-same`, `--replace-existing`, `--replace-different`
   - Test port collision prevention

2. **Port Validation** (`check_local_ports()`)
   - Test port availability before tunnel creation
   - Handle occupied port errors gracefully

3. **Port Range Support** (`parse_ports()`)
   - Support ranges like "8080-8085"
   - Auto-align local/remote port counts

### Phase 3: Medium Priority (FUTURE)

4. **Background Mode** (`create_background_tunnel()`)
   - Spawn detached processes in cp-client-tunnel
   - Implement readiness polling

5. **Foreground Event Loop** (`create_foreground_tunnel()`)
   - Implement bidirectional data forwarding
   - Handle connection lifecycle in library vs CLI

### Phase 4: Low Priority (NICE TO HAVE)

6. **SSH Integration**
   - Key generation and management
   - Passwordless SSH setup

7. **Advanced Process Management**
   - Owner verification
   - Process metadata collection

---

## 📝 Update Instructions for Developers

### When to Update This File

Update MAPPING.md whenever you:
1. Implement or complete a pipe-cli function
2. Mark a feature as partial instead of missing
3. Add a new API method to cp-client-api
4. Add a new data type/interface mapping
5. Add a new component/layer to the ecosystem

### How to Update Status

**Format**: Keep tables concise, use notes only for important details.

**Example 1: New API method in cp-client-api**
```markdown
| **new_method()** | pipe-cli/file.py:LINE | `APIClass.newMethod()` | — | — | — | ✅ Implemented (cp-client-api/src/api-file.ts:LINE) |
```

**Example 2: Library method using API**
```markdown
| **Feature X** | class.method() | `libraryFunction()` (source-file.ts) →  uses cp-client-api | — | — | ✅ Uses library | ✅ Implemented (cp-client-tunnel/src/module.ts | delegates to api) |
```

**Example 3: Partial implementation with notes**
```markdown
⚠️ Partial (cp-client-tunnel/src/file.ts | missing: graceful shutdown, signal handling)
```

### Change Log Pattern

When updating multiple related functions, use this format:

```markdown
## 2026-01-28: cp-client-api Phase 1 Complete

### New Implementations

- ✅ **Cluster.getEdgeExternalUrl()** (cp-client-api/src/cluster-api.ts)
  - Returns EDGE service URL for tunnel proxy
  - Used by cp-client-tunnel/src/connection-info.ts

- ✅ **Run.get() & Run.isInitialized()** (cp-client-api/src/run-api.ts)
  - Fetches pipeline run details
  - Validates SSH initialization status

### Refactoring

- ✅ **cp-client-tunnel**: Migrated from inline fetch to cp-client-api
  - `getRunConnectionInfo()` now delegates to Run + Cluster APIs
  - Reduces code duplication, improves maintainability

### Data Type Mappings

- ✅ **PipelineRunModel** (cp-client-api/src/types.ts)
  - Maps to pipe-cli.src.model.pipeline_run_model.PipelineRunModel
  - Includes run parameters, SSH credentials, and platform info

- ✅ **ClusterNodeModel** (cp-client-api/src/types.ts)
  - Maps to pipe-cli.src.model.cluster_node_model.ClusterNodeModel
  - Includes node labels and associated pipeline run

- ✅ **ClusterInstanceTypeModel** (cp-client-api/src/types.ts)
  - Maps to pipe-cli.src.model.cluster_instance_type_model.ClusterInstanceTypeModel
  - Includes vCPU, GPU, and memory specifications
```

### Validation Checklist

Before marking a function ✅:

- [ ] Feature fully implemented (no TODOs in code)
- [ ] Behavior matches pipe-cli specification (or documented alternative)
- [ ] Unit/integration tests added
- [ ] Error handling implemented
- [ ] Logging statements in place
- [ ] Code documentation updated
- [ ] MAPPING.md row updated WITH LOCATION
- [ ] Related spec files link here (not duplicate content)

---

## 📚 References

- [pipe-cli cluster.py](../../../pipe-cli/src/api/cluster.py) - Cluster API source
- [pipe-cli pipeline_run.py](../../../pipe-cli/src/api/pipeline_run.py) - Run API source
- [pipe-cli ssh_operations.py](../../../pipe-cli/src/utilities/ssh_operations.py) - Tunnel operations
- [cp-client-api.SPEC.md](cp-client-api.SPEC.md) - API library design and usage
- [cp-client-tunnel.SPEC.md](cp-client-tunnel.SPEC.md) - Tunnel library architecture
- [cp-client-cli.SPEC.md](cp-client-cli.SPEC.md) - CLI application specification
- [create_tunnel.SPEC.md](pipe-cli/create_tunnel.SPEC.md) - Full pipe-cli tunnel behavior reference
