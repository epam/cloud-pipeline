# Cloud Pipeline VS Code Extensions — Claude Entry Point

## Project Overview

TypeScript reimplementation of `pipe-cli` (Python) as Node.js libraries and CLI and VSCode extensions.

Goal: enable SSH tunnel management and Cloud Pipeline access from VS Code and CLI.

Two product families:
- **cp-client\*** — Node.js libraries + CLI: 
  `cp-client-api`, `cp-client-tunnel`, `cp-client-common`, `cp-client`
- **remote-cp\*** — VS Code extension + helper: 
  `remote-cp`, `remote-cp-helper`

## New Chat: Reading Order

1. Read `cp-client-meta/.vscode/prompts/init.md` — AI rules and mandate for cp-client family
2. Read `remote-cp-meta/.vscode/prompts/init.md` — AI rules for remote-cp family
3. Use the **Keyword Index** below to find specs for your specific task

## Documentation Hierarchy

```
CLAUDE.md  ← YOU ARE HERE (Claude auto-reads this)
│
├── cp-client-meta/.cursor/rules/start.md         ← Cursor AI entry point (language rules)
│
├── cp-client-meta/.vscode/prompts/init.md        ← AI rules: cp-client family
│   └── cp-client-meta/docs/CODESPEC.md           ← Coding standards, naming, anti-duplication
│       └── cp-client-meta/docs/MAPPING.md        ← ⚠️ SINGLE SOURCE OF TRUTH: implementation status
│           │                                          (pipe-cli ↔ cp-client-* function mapping)
│           ├── cp-client-meta/docs/INDEX.md       ← Keyword index: cp-client family
│           ├── cp-client-meta/docs/cp-client-api.SPEC.md
│           ├── cp-client-meta/docs/cp-client-tunnel.SPEC.md
│           ├── cp-client-meta/docs/cp-client-cli.SPEC.md
│           ├── cp-client-meta/docs/cp-client-common.SPEC.md
│           ├── cp-client-meta/docs/debug.NOTES.md
│           └── cp-client-meta/docs/pipe-cli/      ← pipe-cli Python reference (read-only)
│               ├── pipe-cli.SPEC.md               ← Overview + links to sub-specs
│               ├── create_tunnel.SPEC.md          ← Full call tree + proxy architecture
│               ├── tunnel.SPEC.md                 ← Tunnel command options
│               ├── tunnel-start-logs.SPEC.md      ← Expected log output
│               ├── timeouts.SPEC.md               ← API/tunnel timeout parameters
│               └── get_conn_info.SPEC.md          ← Connection info resolution
│
└── remote-cp-meta/.vscode/prompts/init.md        ← AI rules: remote-cp family
    └── remote-cp-meta/docs/CODESPEC.md           ← Coding standards for remote-cp
        └── remote-cp-meta/docs/INDEX.md          ← Keyword index: remote-cp family
            ├── remote-cp-meta/docs/remote-cp.tunnel.SPEC.md
            └── remote-cp-meta/docs/remote-cp-helper.SPEC.md
```

## Keyword Index

Use this table to find which files to read for your task.

| Topic / Keywords | Read These Files |
|---|---|
| **API, HTTP client, REST, endpoints, RunAPI, ClusterAPI** | `cp-client-meta/docs/cp-client-api.SPEC.md` |
| **Tunnel, SSH tunnel, connection, proxy, TCP forward** | `cp-client-meta/docs/cp-client-tunnel.SPEC.md` → `cp-client-meta/docs/pipe-cli/create_tunnel.SPEC.md` |
| **SSH, keys, configure_ssh, SSH config** | `cp-client-meta/docs/MAPPING.md#ssh-support-layer` → `cp-client-meta/docs/pipe-cli/create_tunnel.SPEC.md` |
| **proxy, HTTP CONNECT, SOCKS, http_proxy_tunnel_connect** | `cp-client-meta/docs/pipe-cli/create_tunnel.SPEC.md` → `cp-client-meta/docs/cp-client-tunnel.SPEC.md` |
| **Implementation status, what's done, what's missing, roadmap** | `cp-client-meta/docs/MAPPING.md` |
| **pipe-cli behavior, Python reference, parity** | `cp-client-meta/docs/pipe-cli/pipe-cli.SPEC.md` (index of all pipe-cli specs) |
| **get_conn_info, connection info, run ID, RunConnectionInfo** | `cp-client-meta/docs/pipe-cli/get_conn_info.SPEC.md` |
| **Timeouts, API timeout, data transfer timeout** | `cp-client-meta/docs/pipe-cli/timeouts.SPEC.md` |
| **Tunnel start logs, expected output, INFO logs** | `cp-client-meta/docs/pipe-cli/tunnel-start-logs.SPEC.md` |
| **CLI commands, cp-client CLI, tunnel start/stop/list** | `cp-client-meta/docs/cp-client-cli.SPEC.md` |
| **Shared types, utilities, ILogger, platform, Endpoint** | `cp-client-meta/docs/cp-client-common.SPEC.md` |
| **Coding standards, naming conventions, file structure** | `cp-client-meta/docs/CODESPEC.md` |
| **Debugging, source maps, launch.json, breakpoints** | `cp-client-meta/docs/debug.NOTES.md` |
| **VS Code extension, remote development, remote-cp** | `remote-cp-meta/.vscode/prompts/init.md` → `remote-cp-meta/docs/CODESPEC.md` |
| **remote-cp-helper, server-side helper** | `remote-cp-meta/docs/remote-cp-helper.SPEC.md` |
| **Tunnel UI flow, stream mode, tunnel mode selection** | `remote-cp-meta/docs/remote-cp.tunnel.SPEC.md` |
| **cp-client-* integration into remote-cp** | `remote-cp/docs/INTEGRATION.md` |
| **Data types, TypeScript interfaces, PipelineRunModel** | `cp-client-meta/docs/MAPPING.md#data-type-mapping` |

## Critical Rules (summary)

- **English only** — all code, docs, comments, commit messages
- **`cp-client-meta/docs/MAPPING.md`** is the ONLY place for implementation status — never duplicate
- Before creating specs, search `docs/` for existing content and update/link instead of duplicating
- When unspecified, mirror pipe-cli behavior: TCP → HTTP CONNECT → SSH
- After implementing a feature, update `MAPPING.md` status column (✅/⚠️/❌)