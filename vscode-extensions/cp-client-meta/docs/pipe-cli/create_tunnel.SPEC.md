# pipe-cli `create_tunnel` Function Specification

**Source:** `pipe-cli/src/utilities/ssh_operations.py:483-520`

**Purpose:** Main entry point for creating SSH tunnels to Cloud Pipeline runs or arbitrary hosts.

## Function Signature

```python
def create_tunnel(host_id, local_ports_str, remote_ports_str, connection_timeout,
                  ssh, ssh_path, ssh_host, ssh_users, ssh_keep, direct, log_file, log_level,
                  timeout, timeout_stop, foreground,
                  keep_existing, keep_same, replace_existing, replace_different, ignore_owner, ignore_existing,
                  retries, region, parse_tunnel_args)
```

## Parameters

- `host_id`: Run ID or host identifier
- `local_ports_str`: Local port(s) string (e.g., "8080" or "8080-8082")
- `remote_ports_str`: Remote port(s) string (e.g., "8080" or "8080-8082")
- `connection_timeout`: Timeout for establishing connections
- `ssh`: Enable SSH configuration mode
- `ssh_path`, `ssh_host`, `ssh_users`: SSH configuration parameters
- `ssh_keep`: Keep SSH keys after tunnel stops
- `direct`: Use direct connection (bypass HTTP proxy)
- `log_file`, `log_level`: Logging configuration
- `timeout`, `timeout_stop`: Operation timeouts
- `foreground`: Run tunnel in foreground vs. background
- `keep_existing`, `keep_same`, `replace_existing`, `replace_different`: Conflict resolution flags
- `ignore_owner`, `ignore_existing`: Validation bypass flags
- `retries`: Number of connection retry attempts
- `region`: Cloud region identifier
- `parse_tunnel_args`: Function to parse tunnel process arguments

## Execution Flow

### 1. Logging Setup
```python
logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
# DEFAULT_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'
```

### 2. Parameter Validation
- Validates at least one of `local_ports_str` or `remote_ports_str` is specified
- Parses port strings via `parse_ports()` → returns list or range
- Validates port count matching between local and remote
- Validates SSH-specific constraints (single port for SSH mode)

### 3. Run Identifier Parsing
```python
run_id = parse_run_identifier(host_id)
# Detects if host_id is pipeline run ID (integer or "pipeline-123" format)
```

### 4. Existing Tunnel Checks (unless `ignore_existing`)

#### 4.1 Check Existing Tunnels
```python
check_existing_tunnels(host_id, local_ports, remote_ports, ...)
```
- Calls `find_tunnels(parse_tunnel_args)` → **Logs: "Searching for tunnel processes..."**
- Iterates through existing tunnel processes
- Compares configurations via `TunnelArgs.compare()`
- Handles conflicts based on flags:
  - `replace_existing`: Kill and replace any existing tunnel
  - `keep_existing`: Skip if tunnel exists
  - `replace_different`: Replace only if configuration differs
  - `keep_same`: Skip if same tunnel exists
  - Default: Error if tunnel exists

#### 4.2 Check Local Ports
```python
check_local_ports(local_ports)
```
- Calls `get_procs_by_local_ports()`:
  - `find_serving_procs()` → **Logs: "Searching for processes listening local ports..."**
  - Returns dict of processes occupying ports
- Calls `find_local_ports_which_cannot_be_occupied()` → **Logs: "Trying to occupy local ports..."**
  - Attempts to bind to each port
  - Yields ports that cannot be occupied
- Raises `TunnelError` if ports are occupied or unavailable

### 5. Tunnel Creation

Routes to appropriate handler:
- If `run_id` exists → `create_tunnel_to_run()`
- Else → `create_tunnel_to_host()`

Both handlers ultimately call `create_foreground_tunnel()`:
- **Logs: "Initializing tunnel {localPort}:{remoteHost}:{remotePort}..."**
- Binds to local ports via `serve_local_ports()`
- **Logs: "Serving tunnel..."**
- Enters main event loop:
  - **Logs: "Waiting for connections..."** (in loop)
  - Accepts client connections
  - Establishes tunnel connections (direct or via HTTP proxy)
  - Forwards data bidirectionally

## Key Logging Messages (Execution Order)

1. `"Searching for tunnel processes..."` (from `find_tunnels`)
2. `"Searching for processes listening local ports..."` (from `find_serving_procs`)
3. `"Trying to occupy local ports..."` (from `find_local_ports_which_cannot_be_occupied`)
4. `"Initializing tunnel {localPort}:{remoteHost}:{remotePort}..."` (from `create_foreground_tunnel`)
5. `"Serving tunnel..."` (from `create_foreground_tunnel`)
6. `"Waiting for connections..."` (from `create_foreground_tunnel` main loop)

## Call Tree

```
create_tunnel
├── logging.basicConfig()
├── parse_ports(local_ports_str)
├── parse_ports(remote_ports_str)
├── parse_run_identifier(host_id)
├── check_existing_tunnels()
│   └── find_tunnels(parse_tunnel_args)
│       └── LOG: "Searching for tunnel processes..."
├── check_local_ports(local_ports)
│   ├── get_procs_by_local_ports(local_ports)
│   │   └── find_serving_procs(local_ports)
│   │       └── LOG: "Searching for processes listening local ports..."
│   └── find_local_ports_which_cannot_be_occupied(local_ports)
│       └── LOG: "Trying to occupy local ports..."
└── create_tunnel_to_run() / create_tunnel_to_host()
    └── create_foreground_tunnel()
        ├── LOG: "Initializing tunnel..."
        ├── serve_local_ports(local_ports)
        ├── LOG: "Serving tunnel..."
        └── [main loop]
            └── LOG: "Waiting for connections..."
```

## cp-client-tunnel Equivalent

**Primary Function:** `TunnelManager.createTunnel()` (`cp-client-tunnel/src/tunnel-manager.ts`)

**Status:** Partial implementation

**Function-Level Mapping:** See [MAPPING.md](../MAPPING.md) for complete equivalence table between pipe-cli and cp-client-tunnel functions with implementation status.

**Key Implementations:**
- ✅ Core tunnel creation (`TunnelManager.createTunnel()`)
- ✅ Connection info resolution (`getRunConnectionInfo()`) - see [get_conn_info.SPEC.md](get_conn_info.SPEC.md)
- ✅ HTTP CONNECT proxy (`httpProxyTunnelConnect()`)
- ✅ Process discovery (`findExistingTunnels()`)
- ⚠️ Partial: Port ranges, validation, conflict resolution
- ❌ Missing: Conflict strategies, port availability checks

## Proxy Connection Architecture

### HTTP CONNECT Proxy Tunnel Establishment

**Function:** `http_proxy_tunnel_connect(proxy, target, timeout=None, retries=None)`

**Source:** [ssh_operations.py:212-254](https://github.com/cloud-pipeline/cloud-pipeline/blob/develop/pipe-cli/src/utilities/ssh_operations.py#L212)

**Purpose:** Creates HTTP CONNECT tunnel through proxy server to establish connection to remote endpoint.

#### Connection Flow

1. **Raw Socket Creation** (line 220)
   ```python
   sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
   sock.connect(proxy)  # Connect to proxy server (host, port)
   ```

2. **HTTP CONNECT Request** (lines 224-228)
   ```python
   auth_token = base64ify('username:api_key')  # Proxy authentication
   headers = {'proxy-authorization': 'Basic ' + auth_token}
   cmd_connect = "CONNECT %s:%d HTTP/1.0\r\n" % target
   sock.sendall(cmd_connect.encode('UTF-8'))  # Send CONNECT to target endpoint
   ```

3. **Proxy Response Validation** (lines 237-241)
   ```python
   if "200 connection established" not in response.lower():
       raise RuntimeError("Unable to establish HTTP-Tunnel: %s" % repr(response))
   ```
   Returns raw socket for direct bidirectional data forwarding.

### Proxy Endpoint Resolution

**Source:** [ssh_operations.py:260-280](https://github.com/cloud-pipeline/cloud-pipeline/blob/develop/pipe-cli/src/utilities/ssh_operations.py#L260)

**Function:** `get_conn_info(run_id, region=None)`

**Purpose:** Retrieves complete connection information for establishing tunnel to a run, including:
- Proxy endpoint (EDGE service URL)
- SSH endpoint (pod IP address)
- SSH credentials and metadata

**Implementation:**

```python
def get_conn_info(run_id, region=None):
    run_model = PipelineRun.get(run_id)  # Fetch run from API
    if not run_model.is_initialized:
        raise RuntimeError('The specified Run ID #{} is not initialized...'.format(run_id))
    
    # Get proxy endpoint from cluster config
    proxy_url = Cluster.get_edge_external_url(region)
    proxy_url_parts = urlparse(proxy_url)
    ssh_proxy_host = proxy_url_parts.hostname
    ssh_proxy_port = proxy_url_parts.port or (80 if scheme=='http' else 443)
    
    # Return connection info namedtuple
    return run_conn_info(
        ssh_proxy=(ssh_proxy_host, ssh_proxy_port),
        ssh_endpoint=(run_model.pod_ip, DEFAULT_SSH_PORT),
        ssh_pass=run_model.ssh_pass,
        owner=run_model.owner,
        sensitive=run_model.sensitive,
        platform=run_model.platform,
        parameters={parameter.name: parameter.value for parameter in run_model.parameters}
    )
```

**cp-client-tunnel Equivalent:** `getRunConnectionInfo(runId, region)` in [connection-info.ts](../../cp-client-tunnel/src/connection-info.ts)

- Fetches run details via Cloud Pipeline REST API (`/restapi/run/{runId}`)
- Validates run is initialized
- Resolves proxy endpoint via EDGE API (`/restapi/cluster/edge/external`)
- Returns typed `RunConnectionInfo` object with same fields as pipe-cli namedtuple

**See detailed specification:** [get_conn_info.SPEC.md](get_conn_info.SPEC.md)

### Tunnel Creation Through Proxy

**Source:** [ssh_operations.py:997-1048](https://github.com/cloud-pipeline/cloud-pipeline/blob/develop/pipe-cli/src/utilities/ssh_operations.py#L997)

**Function:** `create_foreground_tunnel(run_id, local_ports, remote_ports, connection_timeout, conn_info, ...)`

#### Key Lines: Proxy Usage

```python
# Lines 1021-1027: Resolve target and proxy endpoints
target_endpoint = (conn_info.ssh_endpoint[0], remote_port)
proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                  int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))

# Lines 1044-1048: Establish tunnel connection through proxy
if direct:
    tunnel_socket = direct_connect(target_endpoint, timeout=connection_timeout, retries=retries)
else:  # DEFAULT: use proxy
    tunnel_socket = http_proxy_tunnel_connect(proxy_endpoint, target_endpoint,
                                              timeout=connection_timeout,
                                              retries=retries)

# Lines 1050-1053: Register bidirectional forwarding
inputs.append(client_socket)
inputs.append(tunnel_socket)
channel[client_socket] = tunnel_socket  # client → tunnel
channel[tunnel_socket] = client_socket  # tunnel → client
```

#### Event Loop Data Forwarding

Lines 1060-1080+ implement `select`-based multiplexing:
```python
while True:
    inputs_ready, _, _ = select.select(inputs, [], [])
    for sock in inputs_ready:
        if sock in channel:
            chunk = sock.recv(chunk_size)  # Read from one side
            channel[sock].sendall(chunk)   # Write to other side
```

### Environment Override

**Override Points:**
- `CP_CLI_TUNNEL_PROXY_HOST`: Override resolved proxy hostname
- `CP_CLI_TUNNEL_PROXY_PORT`: Override resolved proxy port
- Use case: When proxy topology differs from cluster config

## Implementation Status & Missing Features

**Complete Status:** See [MAPPING.md](../MAPPING.md) for:
- Detailed function-by-function implementation status
- Missing features prioritization (HIGH/MEDIUM/LOW)
- Implementation recommendations
- Gap analysis between pipe-cli and cp-client-tunnel

**Summary:**
- ✅ **Implemented:** Core tunnel creation, connection info resolution, HTTP CONNECT proxy, process discovery
- ⚠️ **Partial:** Port handling (no ranges), event loop (basic forwarding), validation
- ❌ **Missing:** Conflict resolution strategies, port availability checks, SSH key management
