# pipe-cli `get_conn_info` Function Specification

**Source:** `pipe-cli/src/utilities/ssh_operations.py:260-280`

**Purpose:** Retrieves complete connection information for establishing tunnel to a pipeline run, including proxy endpoint, SSH endpoint, and authentication credentials.

## Function Signature

```python
def get_conn_info(run_id, region=None)
```

## Parameters

- `run_id`: Pipeline run ID (integer)
- `region`: Optional cloud region identifier (to resolve region-specific EDGE service)

## Return Value

Returns `run_conn_info` namedtuple with following fields:

```python
run_conn_info = collections.namedtuple('conn_info', 
    'ssh_proxy ssh_endpoint ssh_pass owner sensitive platform parameters')
```

### Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `ssh_proxy` | tuple(str, int) | Proxy server address and port | `('edge.aws.cloud-pipeline.com', 443)` |
| `ssh_endpoint` | tuple(str, int) | Target SSH server address and port | `('10.244.78.133', 22)` |
| `ssh_pass` | str or None | SSH password for authentication | `'base64_encoded_password'` |
| `owner` | str | Run owner username | `'user@domain.com'` |
| `sensitive` | bool | Whether run contains sensitive data | `False` |
| `platform` | str | Operating system platform | `'linux'` or `'windows'` |
| `parameters` | dict | Run parameters as key-value pairs | `{'CP_CAP_SSH_MODE': 'root', ...}` |

## Implementation Flow

### 1. Fetch Run Model

```python
run_model = PipelineRun.get(run_id)
```

Calls Cloud Pipeline API: `GET /restapi/run/{run_id}`

### 2. Validate Run Status

```python
if not run_model.is_initialized:
    raise RuntimeError('The specified Run ID #{} is not initialized for the SSH session'.format(run_id))
```

### 3. Resolve Proxy Endpoint

```python
proxy_url = Cluster.get_edge_external_url(region)  # e.g., "https://edge.aws.cloud-pipeline.com"
proxy_url_parts = urlparse(proxy_url)
ssh_proxy_host = proxy_url_parts.hostname          # 'edge.aws.cloud-pipeline.com'
ssh_proxy_port = proxy_url_parts.port or (80 if proxy_url_parts.scheme == 'http' else 443)
```

Calls Cloud Pipeline API: `GET /restapi/cluster/edge/external?region={region}`

### 4. Build Connection Info

```python
return run_conn_info(
    ssh_proxy=(ssh_proxy_host, ssh_proxy_port),
    ssh_endpoint=(run_model.pod_ip, DEFAULT_SSH_PORT),  # DEFAULT_SSH_PORT = 22
    ssh_pass=run_model.ssh_pass,
    owner=run_model.owner,
    sensitive=run_model.sensitive,
    platform=run_model.platform,
    parameters={parameter.name: parameter.value for parameter in run_model.parameters}
)
```

## Usage in create_tunnel

```python
# In create_tunnel_to_run():
conn_info = get_conn_info(run_id, region)

# Used to build endpoints:
target_endpoint = (conn_info.ssh_endpoint[0], remote_port)
proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                  int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))

# Create tunnel:
tunnel_socket = http_proxy_tunnel_connect(proxy_endpoint, target_endpoint, ...)
```

## Environment Variable Overrides

- `CP_CLI_TUNNEL_PROXY_HOST`: Override resolved proxy hostname
- `CP_CLI_TUNNEL_PROXY_PORT`: Override resolved proxy port

Use case: When proxy topology differs from cluster configuration.

## cp-client-tunnel Equivalent

**Function:** `getRunConnectionInfo(runId, region?, platformUrl?, apiToken?, logger?)` 

**Source:** [connection-info.ts](../../cp-client-tunnel/src/connection-info.ts)

**Return Type:** `RunConnectionInfo` interface (TypeScript typed equivalent of namedtuple)

```typescript
export interface RunConnectionInfo {
  sshProxy: Endpoint;        // { host: string, port: number }
  sshEndpoint: Endpoint;      // { host: string, port: number }
  sshPass?: string;
  owner: string;
  sensitive: boolean;
  platform: string;
  parameters: Record<string, string>;
}
```

### Implementation Differences

1. **API Calls**: Uses native `fetch()` instead of Python requests library
2. **Authentication**: Uses Bearer token from `CP_API_TOKEN` environment variable
3. **Error Handling**: Throws TypeScript `Error` instead of Python `RuntimeError`
4. **Type Safety**: Returns typed interface instead of namedtuple
5. **Async/Await**: Returns `Promise<RunConnectionInfo>` (async)

### Example Usage

```typescript
import { getRunConnectionInfo } from "cp-client-tunnel";

const connInfo = await getRunConnectionInfo(
  85984,                              // runId
  undefined,                          // region (default)
  "https://aws.cloud-pipeline.com",  // platformUrl
  process.env.CP_API_TOKEN,          // apiToken
  logger
);

// Access typed fields:
console.log(`Proxy: ${connInfo.sshProxy.host}:${connInfo.sshProxy.port}`);
console.log(`Target: ${connInfo.sshEndpoint.host}:${connInfo.sshEndpoint.port}`);
```

## Related Functions

- [`get_custom_conn_info(host_id, region)`](ssh_operations.py:283-297) - Similar function for arbitrary hosts (not runs)
- [`http_proxy_tunnel_connect()`](create_tunnel.SPEC.md#http-connect-proxy-tunnel-establishment) - Uses connection info to create tunnel
- [`create_tunnel_to_run()`](create_tunnel.SPEC.md) - Main consumer of connection info

## Error Conditions

| Error | Condition | Message |
|-------|-----------|---------|
| RuntimeError | Run not initialized | `The specified Run ID #{run_id} is not initialized for the SSH session` |
| RuntimeError | Cannot fetch proxy URL | `Cannot retrieve EDGE service external url` |
| RuntimeError | Cannot parse proxy hostname | `Cannot resolve EDGE service hostname from its external url for the specified Run ID #{run_id}` |

## API Endpoints Referenced

1. **Get Run Details**: `GET /restapi/run/{run_id}`
   - Returns: Run model with `pod_ip`, `ssh_pass`, `owner`, `sensitive`, `platform`, `parameters`

2. **Get EDGE URL**: `GET /restapi/cluster/edge/external?region={region}`
   - Returns: Proxy URL string (e.g., `"https://edge.aws.cloud-pipeline.com"`)

## See Also

- [create_tunnel.SPEC.md](create_tunnel.SPEC.md) - Main tunnel creation specification
- [MAPPING.md](../MAPPING.md) - Function equivalence mapping
- [cp-client-tunnel connection-info.ts](../../cp-client-tunnel/src/connection-info.ts) - TypeScript implementation
