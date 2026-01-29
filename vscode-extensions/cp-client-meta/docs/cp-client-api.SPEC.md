# cp-client-api Specification

TypeScript/Node.js API client for Cloud Pipeline REST API, equivalent to `pipe-cli/src/api/`.

## Purpose

Centralized HTTP client replacing inline API calls across cp-client-* projects.

**Implementation Status:** See [FUNCTION_MAPPING.md#api-layer-cp-client-api](FUNCTION_MAPPING.md#api-layer-cp-client-api)

## Architecture

**Source Files:**
- [cp-client-api/src/base-api.ts](../../cp-client-api/src/base-api.ts) - `BaseAPI` (authentication, request/response handling)
- [cp-client-api/src/cluster-api.ts](../../cp-client-api/src/cluster-api.ts) - `ClusterAPI` + `Cluster` static helpers
- [cp-client-api/src/run-api.ts](../../cp-client-api/src/run-api.ts) - `RunAPI` + `Run` static helpers
- [cp-client-api/src/types.ts](../../cp-client-api/src/types.ts) - Type definitions (`APIOptions`, `PipelineRunModel`, `ClusterNodeModel`, `ClusterInstanceTypeModel`, etc.)

**Mirrors pipe-cli:**
- `ClusterAPI` → `pipe-cli/src/api/cluster.py:Cluster`
- `RunAPI` → `pipe-cli/src/api/pipeline_run.py:PipelineRun`

## Usage

```typescript
import { Cluster, Run } from "cp-client-api";

// Static methods (recommended)
const edgeUrl = await Cluster.getEdgeExternalUrl({ apiToken, region });
const run = await Run.get(runId, { apiToken });

// Instance-based
const api = new ClusterAPI({ apiToken, logger });
const nodes = await api.listNodes();
```

## Integration

- **cp-client-tunnel**: Uses `Cluster.getEdgeExternalUrl()` and `Run.get()` in [connection-info.ts](../../cp-client-tunnel/src/connection-info.ts)
- **Authentication**: `CP_API_TOKEN` or `CP_API_KEY` environment variables
- **Error handling**: All methods throw `Error` on failure

## References

- Implementation details: See source files above
- Status tracking: [FUNCTION_MAPPING.md](FUNCTION_MAPPING.md)
- pipe-cli reference: `pipe-cli/src/api/cluster.py`, `pipe-cli/src/api/pipeline_run.py`
