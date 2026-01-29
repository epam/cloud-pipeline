# pipe-cli Reference

**Status:** Read-only reference. Original Python implementation.

## Overview

`pipe-cli` is the Cloud Pipeline command-line utility for managing platform resources, including tunnel management, data storage operations, and job execution.

**The `cp-client` project** is a TypeScript/Node.js implementation of `pipe-cli`, initially focused on tunnel functionality.

## Components

- **[Timeouts](timeouts.SPEC.md)** — Timeout parameters for API, data transfer, and tunnels
- **[Tunnels](tunnel.SPEC.md)** — SSH-over-HTTP-proxy connections to running jobs
- **[Tunnel start logs](tunnel-start-logs.SPEC.md)** — Example INFO-level output from the original Python implementation

## cp-client Relation

The `cp-client` project implements `pipe-cli` functionality in TypeScript for VS Code and Node.js environments. See [cp-client-cli.SPEC.md](../cp-client-cli.SPEC.md) for implementation details.
