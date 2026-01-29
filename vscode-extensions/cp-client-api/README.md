# cp-client-api

Cloud Pipeline API client library for TypeScript/Node.js.

## Overview

This library provides TypeScript equivalents of `pipe-cli` Python API classes and methods. It enables Node.js applications to interact with Cloud Pipeline REST API for cluster management, run information, and other platform services.

## Features

- **Cluster API**: Get edge external URLs, list nodes, instance types
- **Run API**: Fetch pipeline run details and connection information  
- **Type-safe**: Full TypeScript support with interfaces and type definitions
- **Promise-based**: Modern async/await API

## Usage

```typescript
import { ClusterAPI, RunAPI } from "cp-client-api";

// Get edge external URL for proxy connections
const edgeUrl = await ClusterAPI.getEdgeExternalUrl({
  platformUrl: "https://aws.cloud-pipeline.com",
  apiToken: process.env.CP_API_TOKEN,
  region: "us-east-1"
});

// Get run details
const run = await RunAPI.getRun(12345, {
  platformUrl: "https://aws.cloud-pipeline.com",
  apiToken: process.env.CP_API_TOKEN
});
```

## API Documentation

### ClusterAPI

Corresponds to `pipe-cli` `src/api/cluster.py:Cluster` class.

- `getEdgeExternalUrl(options)` - Get EDGE service external URL (corresponds to `Cluster.get_edge_external_url()`)

### RunAPI  

Corresponds to `pipe-cli` `src/api/pipeline_run.py:PipelineRun` class.

- `getRun(runId, options)` - Get pipeline run details (corresponds to `PipelineRun.get()`)

## Dependencies

- `cp-client-common` - Shared types and utilities

## Development

```bash
npm run build       # Build the library
npm run check       # Type-check and lint
npm run clean       # Clean build artifacts
```

## See Also

- [pipe-cli API reference](../cp-client-meta/docs/CODESPEC.md)
- [cp-client-tunnel](../cp-client-tunnel) - Tunnel management library
- [cp-client-common](../cp-client-common) - Common types and utilities
