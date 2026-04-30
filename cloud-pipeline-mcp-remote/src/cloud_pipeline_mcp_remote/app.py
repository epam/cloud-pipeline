"""ASGI app: Streamable HTTP MCP + forwarded Cloud Pipeline credentials."""

from __future__ import annotations

import inspect
import json
import logging
import os
import re
from contextlib import asynccontextmanager
from contextvars import ContextVar
from typing import Any

import httpx
from mcp import types
from mcp.server import Server
from mcp.server.streamable_http_manager import StreamableHTTPSessionManager

try:
    from mcp.server.streamable_http_manager import StreamableHTTPASGIApp  # type: ignore[attr-defined]
except ImportError:
    StreamableHTTPASGIApp = None  # type: ignore[assignment]
from starlette.applications import Starlette
from starlette.middleware.cors import CORSMiddleware
from starlette.responses import JSONResponse
from starlette.routing import Route

logger = logging.getLogger(__name__)

_cp_bearer: ContextVar[str | None] = ContextVar("cp_bearer", default=None)
_cp_api_base: ContextVar[str | None] = ContextVar("cp_api_base", default=None)

API_BASE_HEADER = "x-cloud-pipeline-api-base"

DEFAULT_BRAND = "Cloud Pipeline"
DEFAULT_SERVER_NAME = "cloud-pipeline-mcp-remote"


def _env_brand() -> str:
    return (os.environ.get("CP_MCP_BRAND") or "").strip() or DEFAULT_BRAND


def _env_server_name() -> str:
    return (os.environ.get("CP_MCP_SERVER_NAME") or "").strip() or DEFAULT_SERVER_NAME


def _env_server_instructions(brand: str) -> str:
    custom = (os.environ.get("CP_MCP_SERVER_INSTRUCTIONS") or "").strip()
    if custom:
        return custom
    return (
        f"Tools to operate {brand}: list/start/stop runs, browse data storages, tools, "
        f"docker registries, cloud regions, preferences and ACL permissions. Each request "
        f"must carry the user's JWT (Authorization: Bearer) and the {brand} REST base URL "
        f"(X-Cloud-Pipeline-Api-Base) — these are forwarded as-is. "
        "Safeguard: destructive operations (stop/terminate/delete) are refused for users "
        "carrying ROLE_ADMIN or any scoped *_ADMIN role to prevent fleet-wide impact; "
        "read-only tools and starting new runs remain available."
    )


def _http_verify() -> bool:
    return os.environ.get("CP_HTTP_VERIFY", "true").lower() in ("1", "true", "yes")


def _cors_origins() -> list[str]:
    raw = os.environ.get("ALLOW_ORIGINS", "*")
    return [o.strip() for o in raw.split(",") if o.strip()]


# --- Admin safeguard ---------------------------------------------------------

# Matches ROLE_ADMIN and any scoped role with the *_ADMIN suffix (e.g. ROLE_RUN_ADMIN,
# ROLE_STORAGE_ADMIN, ROLE_BILLING_ADMIN, ...).
_ADMIN_ROLE_RE = re.compile(r"^ROLE_(?:ADMIN|.+_ADMIN)$")

# Tools that mutate or destroy state and are always considered destructive.
_DESTRUCTIVE_TOOL_NAMES: set[str] = {"cp_run_stop"}


def _role_is_admin_scope(role_name: str) -> bool:
    return bool(_ADMIN_ROLE_RE.match((role_name or "").strip().upper()))


def _admin_safeguard_enabled() -> bool:
    val = (os.environ.get("CP_MCP_ADMIN_SAFEGUARD") or "true").strip().lower()
    return val in ("1", "true", "yes", "on")


def _api_call_is_destructive(method: str, path: str) -> bool:
    """Heuristic deny-list for cp_api_request: DELETE-anything plus known stop endpoints."""
    m = (method or "").upper()
    p = (path or "").lstrip("/")
    if m == "DELETE":
        return True
    if m == "POST" and re.match(r"^run/\d+/status$", p):
        return True
    if m == "POST" and re.search(r"/(terminate|stop|abort|cancel|kill)(?:/.*)?$", p, re.IGNORECASE):
        return True
    return False


async def _fetch_user_admin_status() -> tuple[bool, list[str], str | None]:
    """Look up the JWT-bound user via /whoami. Returns (is_admin, role_names, error)."""
    base = (_cp_api_base.get() or "").strip().rstrip("/")
    bearer = (_cp_bearer.get() or "").strip()
    if not base or not bearer:
        return False, [], "missing forwarded credentials"
    headers = {
        "Authorization": f"Bearer {bearer}",
        "Accept": "application/json",
    }
    try:
        async with httpx.AsyncClient(timeout=30.0, verify=_http_verify()) as client:
            r = await client.get(f"{base}/whoami", headers=headers)
    except httpx.RequestError as e:
        return False, [], f"whoami request failed: {e}"
    try:
        data = r.json()
    except json.JSONDecodeError:
        return False, [], f"whoami returned non-JSON HTTP {r.status_code}"
    if r.status_code != 200 or not isinstance(data, dict) or data.get("status") != "OK":
        return False, [], f"whoami non-OK HTTP {r.status_code}: {str(data)[:200]}"
    payload = data.get("payload") or {}
    role_names: list[str] = []
    for it in payload.get("roles") or []:
        if isinstance(it, dict):
            n = it.get("name")
            if n:
                role_names.append(str(n))
        elif isinstance(it, str):
            role_names.append(it)
    has_admin_role = any(_role_is_admin_scope(rn) for rn in role_names)
    is_admin_flag = bool(payload.get("admin"))
    return (has_admin_role or is_admin_flag), role_names, None


async def _admin_safeguard(
    tool: str,
    *,
    method: str | None = None,
    path: str | None = None,
) -> types.CallToolResult | None:
    """Return a CallToolResult error if the JWT-bound user is admin and the call is destructive.

    Fail-closed semantics: if we cannot determine the user's role we still refuse, because the
    safeguard exists precisely to keep an admin token from ever firing a fleet-wide stop/delete
    through the agent.
    """
    if not _admin_safeguard_enabled():
        return None

    if tool in _DESTRUCTIVE_TOOL_NAMES:
        destructive = True
    elif tool == "cp_api_request" and method is not None and path is not None:
        destructive = _api_call_is_destructive(method, path)
    else:
        destructive = False

    if not destructive:
        return None

    is_admin, roles, err = await _fetch_user_admin_status()
    brand = _env_brand()
    if err:
        return _tool_error(
            f"{brand} MCP admin safeguard: refusing destructive operation '{tool}' because the "
            f"current user's role could not be verified ({err}). The safeguard fails closed by "
            "design. Set CP_MCP_ADMIN_SAFEGUARD=false on the server to disable (not recommended)."
        )
    if is_admin:
        admin_roles = [rn for rn in roles if _role_is_admin_scope(rn)]
        marker = ", ".join(admin_roles) if admin_roles else "admin flag from /whoami"
        target = (
            f" ({(method or '').upper()} {path})" if tool == "cp_api_request" and path else ""
        )
        return _tool_error(
            f"{brand} MCP admin safeguard: refused destructive operation '{tool}'{target} for an "
            f"administrative user (matched: {marker}). Stop/delete-alike actions are blocked for "
            "users carrying ROLE_ADMIN or any scoped *_ADMIN role to prevent fleet-wide impact. "
            "Re-run with a non-admin account, or set CP_MCP_ADMIN_SAFEGUARD=false on the server "
            "(not recommended) to opt out."
        )
    return None


# -----------------------------------------------------------------------------


def _decode_scope_headers(scope: dict[str, Any]) -> dict[str, str]:
    out: dict[str, str] = {}
    for k, v in scope.get("headers") or []:
        try:
            out[k.decode("latin1").lower()] = v.decode("latin1")
        except Exception:
            continue
    return out


def _tool_text(payload: Any) -> types.CallToolResult:
    text = json.dumps(payload, indent=2, default=str)
    return types.CallToolResult(content=[types.TextContent(type="text", text=text)])


def _tool_error(message: str) -> types.CallToolResult:
    return types.CallToolResult(
        isError=True,
        content=[types.TextContent(type="text", text=message)],
    )


async def _cp_json(
    method: str,
    path: str,
    *,
    query: dict[str, Any] | None = None,
    body: Any | None = None,
) -> types.CallToolResult:
    base = (_cp_api_base.get() or "").strip().rstrip("/")
    bearer = (_cp_bearer.get() or "").strip()
    if not base or not bearer:
        brand = _env_brand()
        return _tool_error(
            "Missing upstream credentials. Send Authorization: Bearer <jwt> and "
            f"{API_BASE_HEADER}: <{brand} REST base URL> on every MCP HTTP request."
        )
    p = path if path.startswith("/") else f"/{path}"
    url = f"{base}{p}"
    headers = {
        "Authorization": f"Bearer {bearer}",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    req_kw: dict[str, Any] = {}
    if query is not None:
        req_kw["params"] = query
    if body is not None and method.upper() not in ("GET", "HEAD"):
        req_kw["json"] = body
    try:
        async with httpx.AsyncClient(timeout=120.0, verify=_http_verify()) as client:
            r = await client.request(method, url, headers=headers, **req_kw)
    except httpx.RequestError as e:
        logger.warning("Upstream request failed: %s", e)
        return _tool_error(f"Upstream request failed: {e}")

    try:
        data = r.json()
    except json.JSONDecodeError:
        snippet = (r.text or "")[:500]
        return _tool_error(f"Non-JSON response HTTP {r.status_code}: {snippet}")

    if r.status_code in (401, 403):
        return _tool_error(f"HTTP {r.status_code} (auth): {data!s}"[:2000])

    if r.status_code != 200:
        return _tool_error(f"HTTP {r.status_code}: {json.dumps(data, default=str)[:2000]}")

    if isinstance(data, dict) and data.get("status") != "OK":
        return _tool_error(data.get("message") or str(data.get("status")) or "API error")

    return _tool_text(data.get("payload", data))


def _build_tools(brand: str) -> list[types.Tool]:
    """Build tool list with brand-aware descriptions for better agent grounding."""
    return [
        types.Tool(
            name="cp_whoami",
            description=f"Return the current {brand} user profile (GET whoami).",
            inputSchema={"type": "object", "properties": {}, "additionalProperties": False},
        ),
        types.Tool(
            name="cp_preference_get",
            description=f"Load a {brand} system preference by name (GET preferences/{{name}}).",
            inputSchema={
                "type": "object",
                "required": ["name"],
                "properties": {"name": {"type": "string", "description": "Preference key"}},
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_run_filter",
            description=f"Filter {brand} pipeline runs by status, owner, run/pipeline ids, etc. (POST run/filter).",
            inputSchema={
                "type": "object",
                "description": "PagingRunFilterVO body",
                "properties": {
                    "page": {"type": "integer", "default": 1},
                    "pageSize": {"type": "integer", "default": 20},
                    "statuses": {"type": "array", "items": {"type": "string"}},
                    "owners": {"type": "array", "items": {"type": "string"}},
                    "runIds": {"type": "array", "items": {"type": "integer"}},
                    "pipelineIds": {"type": "array", "items": {"type": "integer"}},
                    "prettyUrl": {"type": "string"},
                    "partialParameters": {"type": "string"},
                },
                "additionalProperties": True,
            },
        ),
        types.Tool(
            name="cp_run_get",
            description=f"Get a single {brand} run by id (GET run/{{runId}}).",
            inputSchema={
                "type": "object",
                "required": ["runId"],
                "properties": {"runId": {"type": "integer"}},
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_run_get_tasks",
            description=f"List tasks of a {brand} run (GET run/{{runId}}/tasks).",
            inputSchema={
                "type": "object",
                "required": ["runId"],
                "properties": {"runId": {"type": "integer"}},
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_run_start",
            description=(
                f"Start a {brand} pipeline or tool run (POST run). Body matches "
                f"{brand} PipelineStart / tool launch payload."
            ),
            inputSchema={
                "type": "object",
                "required": ["payload"],
                "properties": {
                    "payload": {
                        "type": "object",
                        "description": f"JSON body sent to {brand} POST /run",
                        "additionalProperties": True,
                    }
                },
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_run_stop",
            description=(
                f"Stop a {brand} run (POST run/{{runId}}/status with STOPPED). "
                "Destructive: refused for users carrying ROLE_ADMIN or any scoped *_ADMIN role."
            ),
            inputSchema={
                "type": "object",
                "required": ["runId"],
                "properties": {"runId": {"type": "integer"}},
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_cluster_edge_external_url",
            description=f"Resolve {brand} EDGE external URL (GET cluster/edge/externalUrl).",
            inputSchema={
                "type": "object",
                "properties": {
                    "region": {"type": "string", "description": "Optional region query parameter"},
                },
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_docker_registry_load_tree",
            description=f"{brand} docker registries and tools tree (GET dockerRegistry/loadTree).",
            inputSchema={"type": "object", "properties": {}, "additionalProperties": False},
        ),
        types.Tool(
            name="cp_tool_info",
            description=f"{brand} tool metadata (GET tool/{{toolId}}/info).",
            inputSchema={
                "type": "object",
                "required": ["toolId"],
                "properties": {"toolId": {"type": "integer"}},
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_tool_settings",
            description=f"{brand} tool version settings (GET tool/{{toolId}}/settings).",
            inputSchema={
                "type": "object",
                "required": ["toolId"],
                "properties": {
                    "toolId": {"type": "integer"},
                    "version": {"type": "string", "description": "Optional tool version"},
                },
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_cloud_regions",
            description=f"List {brand} cloud regions (GET cloud/region).",
            inputSchema={"type": "object", "properties": {}, "additionalProperties": False},
        ),
        types.Tool(
            name="cp_cluster_instance_allowed",
            description=f"{brand} allowed instance and price types (GET cluster/instance/allowed).",
            inputSchema={
                "type": "object",
                "properties": {
                    "toolId": {"type": "integer"},
                    "regionId": {"type": "integer"},
                    "spot": {"type": "boolean"},
                },
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_data_storage_list",
            description=f"List all {brand} data storages (GET datastorage/loadAll).",
            inputSchema={"type": "object", "properties": {}, "additionalProperties": False},
        ),
        types.Tool(
            name="cp_permission_entity",
            description=f"{brand} entity-level permissions (GET permissions?id=&aclClass=).",
            inputSchema={
                "type": "object",
                "required": ["aclClass", "id"],
                "properties": {
                    "aclClass": {"type": "string", "description": "AclClass enum name"},
                    "id": {"type": "integer"},
                },
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_permission_grant_tree",
            description=f"{brand} ACL grant tree for an object (GET grant?id=&aclClass=).",
            inputSchema={
                "type": "object",
                "required": ["aclClass", "id"],
                "properties": {
                    "aclClass": {"type": "string"},
                    "id": {"type": "integer"},
                },
                "additionalProperties": False,
            },
        ),
        types.Tool(
            name="cp_api_request",
            description=(
                f"Low-level {brand} JSON call. method is GET/POST/PUT/DELETE; path is relative to "
                f"the {brand} REST base (e.g. run/123). Same authentication as other tools. "
                "Destructive variants (DELETE method, run/{id}/status, *terminate/stop/abort/cancel/kill "
                "endpoints) are refused for users carrying ROLE_ADMIN or any scoped *_ADMIN role; "
                "use a non-admin account for those."
            ),
            inputSchema={
                "type": "object",
                "required": ["method", "path"],
                "properties": {
                    "method": {"type": "string", "enum": ["GET", "POST", "PUT", "DELETE", "PATCH"]},
                    "path": {"type": "string"},
                    "query": {"type": "object", "additionalProperties": True},
                    "body": {
                        "anyOf": [
                            {"type": "object"},
                            {"type": "array"},
                            {"type": "string"},
                            {"type": "number"},
                            {"type": "boolean"},
                            {"type": "null"},
                        ]
                    },
                },
                "additionalProperties": False,
            },
        ),
    ]


def handle_list_tools_factory(tools: list[types.Tool]):
    async def _handler(
        _ctx: Any,
        _params: types.PaginatedRequestParams | None,
    ) -> types.ListToolsResult:
        return types.ListToolsResult(tools=tools)

    return _handler


async def execute_tool_call(name: str, args: dict[str, Any]) -> types.CallToolResult:
    if name == "cp_run_stop":
        block = await _admin_safeguard(name)
        if block is not None:
            return block
    elif name == "cp_api_request":
        block = await _admin_safeguard(
            name,
            method=str(args.get("method") or ""),
            path=str(args.get("path") or ""),
        )
        if block is not None:
            return block

    if name == "cp_whoami":
        return await _cp_json("GET", "whoami")
    if name == "cp_preference_get":
        n = str(args.get("name", "")).strip()
        if not n:
            return _tool_error("name is required")
        return await _cp_json("GET", f"preferences/{n}")
    if name == "cp_run_filter":
        return await _cp_json("POST", "run/filter", body=args)
    if name == "cp_run_get":
        rid = int(args["runId"])
        return await _cp_json("GET", f"run/{rid}")
    if name == "cp_run_get_tasks":
        rid = int(args["runId"])
        return await _cp_json("GET", f"run/{rid}/tasks")
    if name == "cp_run_start":
        return await _cp_json("POST", "run", body=args.get("payload"))
    if name == "cp_run_stop":
        rid = int(args["runId"])
        return await _cp_json("POST", f"run/{rid}/status", body={"status": "STOPPED"})
    if name == "cp_cluster_edge_external_url":
        region = args.get("region")
        path = "cluster/edge/externalUrl"
        q = {"region": region} if region else None
        return await _cp_json("GET", path, query=q)
    if name == "cp_docker_registry_load_tree":
        return await _cp_json("GET", "dockerRegistry/loadTree")
    if name == "cp_tool_info":
        tid = int(args["toolId"])
        return await _cp_json("GET", f"tool/{tid}/info")
    if name == "cp_tool_settings":
        tid = int(args["toolId"])
        ver = args.get("version")
        if ver:
            return await _cp_json("GET", f"tool/{tid}/settings", query={"version": str(ver)})
        return await _cp_json("GET", f"tool/{tid}/settings")
    if name == "cp_cloud_regions":
        return await _cp_json("GET", "cloud/region")
    if name == "cp_cluster_instance_allowed":
        q = {k: v for k, v in {"toolId": args.get("toolId"), "regionId": args.get("regionId"), "spot": args.get("spot")}.items() if v is not None}
        return await _cp_json("GET", "cluster/instance/allowed", query=q)
    if name == "cp_data_storage_list":
        return await _cp_json("GET", "datastorage/loadAll")
    if name == "cp_permission_entity":
        acl = str(args["aclClass"])
        sid = int(args["id"])
        return await _cp_json("GET", "permissions", query={"aclClass": acl, "id": sid})
    if name == "cp_permission_grant_tree":
        acl = str(args["aclClass"])
        sid = int(args["id"])
        return await _cp_json("GET", "grant", query={"aclClass": acl, "id": sid})
    if name == "cp_api_request":
        method = str(args["method"]).upper()
        path = str(args["path"]).lstrip("/")
        return await _cp_json(
            method,
            path,
            query=args.get("query"),
            body=args.get("body"),
        )

    return _tool_error(f"Unknown tool: {name}")


async def handle_call_tool(_ctx: Any, params: types.CallToolRequestParams) -> types.CallToolResult:
    return await execute_tool_call(params.name, params.arguments or {})


def _transport_security_relaxed() -> Any | None:
    """Allow non-localhost clients (Cursor) without DNS-rebinding blocks."""
    try:
        from mcp.server.transport_security import TransportSecuritySettings

        return TransportSecuritySettings(enable_dns_rebinding_protection=False)
    except Exception:
        return None


def _build_mcp_server(*, server_name: str, instructions: str, tools: list[types.Tool]) -> Any:
    """Return a configured low-level MCP Server (constructor or decorator API)."""
    sig = inspect.signature(Server.__init__)
    extra_kwargs: dict[str, Any] = {}
    if "instructions" in sig.parameters:
        extra_kwargs["instructions"] = instructions

    if "on_list_tools" in sig.parameters:
        return Server(
            server_name,
            on_list_tools=handle_list_tools_factory(tools),
            on_call_tool=handle_call_tool,
            **extra_kwargs,
        )

    srv = Server(server_name, **extra_kwargs)
    if not hasattr(srv, "list_tools") or not hasattr(srv, "call_tool"):
        raise RuntimeError(
            "The installed 'mcp' Server has no list_tools/call_tool registration API. "
            "Try: pip install -U 'mcp>=1.8,<3'"
        )

    @srv.list_tools()
    async def _list_tools_decorator() -> list[types.Tool]:
        return tools

    @srv.call_tool()
    async def _call_tool_decorator(name: str, arguments: dict[str, Any] | None) -> types.CallToolResult:
        return await execute_tool_call(name, arguments or {})

    return srv


class ForwardedUpstreamMiddleware:
    """Binds X-Cloud-Pipeline-Api-Base and Authorization to contextvars for the request."""

    def __init__(self, app: Any) -> None:
        self.app = app

    async def __call__(self, scope: dict[str, Any], receive: Any, send: Any) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        hdrs = _decode_scope_headers(scope)
        auth = hdrs.get("authorization", "")
        bearer = ""
        if auth.lower().startswith("bearer "):
            bearer = auth[7:].strip()
        api_base = hdrs.get(API_BASE_HEADER, "").strip()
        t1 = _cp_bearer.set(bearer)
        t2 = _cp_api_base.set(api_base)
        try:
            await self.app(scope, receive, send)
        finally:
            _cp_bearer.reset(t1)
            _cp_api_base.reset(t2)


def _health_factory(server_name: str, brand: str):
    async def _health(_: Any) -> JSONResponse:
        return JSONResponse({"status": "ok", "service": server_name, "brand": brand})

    return _health


def create_app() -> Starlette:
    """Streamable HTTP is wired via StreamableHTTPSessionManager (same as SDK streamable_http_app)."""
    brand = _env_brand()
    server_name = _env_server_name()
    instructions = _env_server_instructions(brand)
    tools = _build_tools(brand)
    logger.info("Starting MCP server name=%r brand=%r tools=%d", server_name, brand, len(tools))
    srv = _build_mcp_server(server_name=server_name, instructions=instructions, tools=tools)
    sm_kwargs: dict[str, Any] = {
        "app": srv,
        "event_store": None,
        "json_response": True,
        "stateless": True,
    }
    sm_sig = inspect.signature(StreamableHTTPSessionManager.__init__)
    if "security_settings" in sm_sig.parameters:
        sm_kwargs["security_settings"] = _transport_security_relaxed()
    session_manager = StreamableHTTPSessionManager(**sm_kwargs)
    if StreamableHTTPASGIApp is not None:
        mcp_asgi: Any = StreamableHTTPASGIApp(session_manager)
    else:
        class _SessionAsgiApp:
            def __init__(self, sm: StreamableHTTPSessionManager) -> None:
                self._sm = sm

            async def __call__(self, scope: Any, receive: Any, send: Any) -> None:
                await self._sm.handle_request(scope, receive, send)

        mcp_asgi = _SessionAsgiApp(session_manager)

    @asynccontextmanager
    async def lifespan(_: Starlette):
        async with session_manager.run():
            yield

    core = Starlette(
        debug=False,
        routes=[
            Route("/health", _health_factory(server_name, brand), methods=["GET"]),
            Route("/mcp", endpoint=mcp_asgi),
        ],
        lifespan=lifespan,
    )
    core = CORSMiddleware(
        core,
        allow_origins=_cors_origins(),
        allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
        allow_headers=["*"],
        expose_headers=["Mcp-Session-Id"],
    )
    return ForwardedUpstreamMiddleware(core)
