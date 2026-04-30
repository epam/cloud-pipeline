# Cloud Pipeline MCP (remote)

Stateless **Streamable HTTP** MCP server. Each Cursor request must include:

- `Authorization: Bearer <JWT>` — same token as `~/.pipe/config.json` `access_key`
- `X-Cloud-Pipeline-Api-Base: <url>` — same as `api` (e.g. `https://host/pipeline/restapi`, no trailing slash)

The server forwards those credentials to the Cloud Pipeline REST API and returns JSON tool results as text.

## Run

```bash
cd cloud-pipeline-mcp-remote
pip install -e .
cloud-pipeline-mcp-remote --host 0.0.0.0 --port 8080
```

If `pip install -e .` still complains about setuptools, your `pip` is too old to use `pyproject.toml` alone; upgrading `pip`, `setuptools`, and `wheel` (as above) fixes that. The repo includes a small `setup.py` shim for editable installs.

Cursor `~/.cursor/mcp.json` entry (written by the **Cloud Pipeline Remote** extension when configured):

```json
{
  "mcpServers": {
    "cloud-pipeline": {
      "url": "https://your-mcp-host/mcp",
      "headers": {
        "Authorization": "Bearer <jwt>",
        "X-Cloud-Pipeline-Api-Base": "https://cp-host/pipeline/restapi"
      }
    }
  }
}
```

## Environment

All configuration is via environment variables (no CLI flags besides `--host`/`--port`/`--log-level`):

| Variable | Default | Meaning |
|----------|---------|---------|
| `CP_MCP_SERVER_NAME` | `cloud-pipeline-mcp-remote` | Internal server identifier. Reported as `Server(name=...)` to MCP clients and used as `service` in `/health`. |
| `CP_MCP_BRAND` | `Cloud Pipeline` | Human-readable product name. Embedded in the server's `instructions` and into every tool description so agents always know which platform the tools target. |
| `CP_MCP_SERVER_INSTRUCTIONS` | *(auto)* | Override the default `Server(instructions=...)` text. When unset, instructions are auto-generated from `CP_MCP_BRAND`. |
| `CP_MCP_ADMIN_SAFEGUARD` | `true` | When `true`, destructive operations are refused for users with `ROLE_ADMIN` or any scoped `ROLE_*_ADMIN` role (see "Admin safeguard" below). Set to `false` only for emergency / automation accounts. |
| `CP_HTTP_VERIFY` | `true` | Set to `false` to skip TLS verification (not recommended). |
| `ALLOW_ORIGINS` | `*` | Comma-separated CORS `allow_origins` for the MCP mount. |

## Admin safeguard

To keep an admin's JWT from accidentally firing a fleet-wide stop/delete via an LLM agent, the
server inspects the JWT-bound user (via `/whoami`) before destructive operations and refuses
them when any of the following is true:

- the user has `ROLE_ADMIN`, or
- the user has any scoped `ROLE_*_ADMIN` role (e.g. `ROLE_RUN_ADMIN`, `ROLE_STORAGE_ADMIN`, `ROLE_BILLING_ADMIN`), or
- the `/whoami` payload reports `admin: true`.

The following calls are treated as destructive:

- `cp_run_stop` (always).
- `cp_api_request` with `method = DELETE` (always).
- `cp_api_request` with `method = POST` against `run/{id}/status` (the stop-run endpoint).
- `cp_api_request` with `method = POST` against any path ending in `/terminate`, `/stop`, `/abort`, `/cancel`, `/kill`.

Read-only tools (`cp_whoami`, `cp_run_filter`, `cp_run_get`, `cp_run_get_tasks`, `cp_data_storage_list`,
permission/preference lookups, etc.) and `cp_run_start` are always allowed.

The check **fails closed**: if `/whoami` cannot be reached or returns an unexpected payload, the
destructive call is refused. Set `CP_MCP_ADMIN_SAFEGUARD=false` to disable the safeguard
entirely (e.g. for a dedicated automation node where the operator already gates access at a
different layer).

White-label example:

```bash
export CP_MCP_BRAND="Acme Compute Cloud"
export CP_MCP_SERVER_NAME="acme-compute-mcp"
cloud-pipeline-mcp-remote --host 0.0.0.0 --port 8080
```

With the above, MCP clients see a server named `acme-compute-mcp`, and tool descriptions read e.g. *"List all Acme Compute Cloud data storages..."* instead of the default branding.

Health check: `GET /health` on the same host/port as the app root (not under `/mcp`).

The server adapts tool registration to your `mcp` SDK (constructor `on_*` handlers vs `@server.list_tools()` / `@server.call_tool()`). Streamable HTTP is always served via `StreamableHTTPSessionManager` + `StreamableHTTPASGIApp`, matching the official SDK wiring.
