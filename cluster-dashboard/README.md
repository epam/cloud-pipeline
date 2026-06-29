# Cloud Pipeline — Cluster Utilization Dashboard

A lightweight Flask web application that periodically collects cluster-wide
CPU, Memory and GPU metrics from the Cloud Pipeline REST API and displays them
as interactive line charts.

## Quick start

```bash
cd cluster-dashboard
pip install -r requirements.txt

export CP_API_URL=https://your-cp-host/pipeline/restapi   # same as the `pipe` CLI api value
export CP_API_TOKEN=your-bearer-token

python app.py
# open http://localhost:5000
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `CP_API_URL` | *(required)* | Cloud Pipeline REST API base URL, e.g. `https://cp.example.com/pipeline/restapi` |
| `CP_API_TOKEN` | *(required)* | Bearer token for API authentication |
| `COLLECT_INTERVAL_MINUTES` | `60` | How often to collect a snapshot (minutes) |
| `DB_PATH` | `<app-dir>/data/metrics.db` | Path to the SQLite database file |
| `PORT` | `5000` | HTTP port |

## How it works

On startup the scheduler runs an immediate collection, then repeats every
`COLLECT_INTERVAL_MINUTES`. Each collection:

1. Calls `GET /cluster/node/loadAll` — gets all worker nodes with their
   Kubernetes `capacity` and `allocatable` resource fields.
2. For each worker node, calls `GET /cluster/node/{name}/usage` — retrieves
   actual CPU and memory utilization from the Heapster → Elasticsearch
   monitoring pipeline that Cloud Pipeline already operates.
3. For GPU-equipped nodes, calls `GET /cluster/node/{name}/usage/gpus` — reads
   active GPU counts written by `cp-monitoring-srv`.
4. Aggregates all values cluster-wide and writes one row to SQLite.
5. Purges rows older than 7 days.

The dashboard frontend queries `/api/metrics?from=…&to=…` (max 7-day range,
default last 24 h) and renders three Chart.js line charts.

## Interpreting the charts

Each chart shows three series over the selected time range.

### Capacity *(dashed gray)*

Raw physical resources reported by the Kubernetes node object
(`status.capacity`). This is what the hardware actually has — total CPU cores,
total RAM installed, total GPUs present. It does not change unless nodes are
added or removed from the cluster.

### Allocatable *(solid blue / cyan)*

The subset of capacity that Kubernetes is allowed to schedule workloads onto
(`status.allocatable`). It equals Capacity minus resources explicitly reserved
for the OS, kubelet, and other system daemons. On a typical Cloud Pipeline node
this reservation is a small fraction (a few hundred millicores, a few GiB), so
the Allocatable line sits just below Capacity. The gap between the two is not
wasted — it is intentionally held back to keep the node OS healthy under high
load.

### Used *(filled green area)*

Actual real-time resource consumption, summed across all worker nodes:

- **CPU Used** — `cpuUsage.load × numberOfCores` per node (cores actively
  executing work).
- **Memory Used** — `memoryUsage.usage` per node (working set in bytes, i.e.
  memory that cannot be swapped out).
- **GPU Active** — `gpuUsage.activeGpus.mean` per GPU node (GPUs that had
  non-zero utilization during the collection window).

Because Used data comes from Heapster/Elasticsearch, the series will show gaps
if that monitoring stack has no data for a node in a given window.

### Practical reading guide

| Situation | What you see |
|---|---|
| Cluster is idle | Used ≈ 0, well below Allocatable |
| Cluster is under normal load | Used rises toward Allocatable |
| Allocatable is saturated but nodes have headroom | Used ≈ Allocatable; gap to Capacity is the OS reservation |
| Node was added or removed | Step change in the Capacity and Allocatable lines |
| Monitoring stack is down | Used series has gaps; Capacity/Allocatable still render |

> **The most actionable signal is the gap between Used and Allocatable** —
> that is the true free capacity available to new pipeline runs.

## Summary tiles

The four tiles at the top of the page show values from the **latest collected
snapshot** (not an average over the selected range).

**Worker Nodes** — number of non-master Kubernetes nodes in the cluster at last
collection.

**CPU Used** — total CPU cores actively busy cluster-wide. The progress bar
shows `Used / Capacity`. Displays `—` if Heapster data is unavailable.

**Memory Used** — total GiB of RAM in active use (working set) cluster-wide.
Same caveats as CPU Used.

**GPU Active** — total GPUs with non-zero utilization at last collection.
Shows `N/A` if the cluster has no GPU nodes.

## File structure

```
cluster-dashboard/
├── app.py          # Flask app, APScheduler, REST API endpoints
├── collector.py    # Cloud Pipeline API client, per-node aggregation
├── database.py     # SQLite schema, insert/query/purge
├── requirements.txt
├── data/           # SQLite database lives here (created on first run)
└── static/
    └── index.html  # Single-page dashboard (Chart.js, no build step)
```
