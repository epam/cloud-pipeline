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

| Variable | Default | Meaning |
|----------|---------|---------|
| `CP_HTTP_VERIFY` | `true` | Set to `false` to skip TLS verification (not recommended). |
| `ALLOW_ORIGINS` | `*` | Comma-separated CORS `allow_origins` for the MCP mount. |

Health check: `GET /health` on the same host/port as the app root (not under `/mcp`).

The server adapts tool registration to your `mcp` SDK (constructor `on_*` handlers vs `@server.list_tools()` / `@server.call_tool()`). Streamable HTTP is always served via `StreamableHTTPSessionManager` + `StreamableHTTPASGIApp`, matching the official SDK wiring.
