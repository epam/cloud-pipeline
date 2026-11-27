# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)
- [Fallback instance types for a future-dated capacity reservation](#fallback-instance-types-for-a-future-dated-capacity-reservation)
- [A node pool's own run parameters for a run bound to it](#a-node-pools-own-run-parameters-for-a-run-bound-to-it)
- [Parallel download into the disk buffer of a storage mount](#parallel-download-into-the-disk-buffer-of-a-storage-mount)
- [Launch capabilities: restrict a capability to certain users or roles](#launch-capabilities-restrict-a-capability-to-certain-users-or-roles)

***

- [Notable Bug fixes](#notable-bug-fixes)
    - [Unavailable cluster nodes are not detected](#unavailable-cluster-nodes-are-not-detected)
    - [`pipe` CLI for macOS ARM needs Rosetta](#pipe-cli-for-macos-arm-needs-rosetta)

***

## Migration to Java 21

In the current version, main platform's Java-based services (`api`, `elasticsearch-agent`, `vm-monitor`, `monitoring-service`, `billing-report-agent`, `notifier`, `docker-comp-scan` and `jwt-generator`) moved from Java **8** to Java **21**.

The root build moved from Gradle **4.10** to Gradle **8.7**, and the Docker images of the affected services now bundle a Java **21** runtime.

Additionally, Spring Boot moved from **1.5**/**2** to Spring Boot **3.3**.

***

## Capacity block offering search over several instance types

Searching for a capacity block offering can now ask about several candidate instance types - each with its own count - in a single request, rather than one search per type, so a user can compare a primary instance type against whatever fallback types they would also accept before deciding which offering to buy. The response merges every candidate's offerings into one list, sorted by start date, nearest first. See [Browsing capacity block offerings](../../manual/09_Manage_Cluster_nodes/9.1._Hot_node_pools.md#browsing-capacity-block-offerings).

How many candidates one request may carry is bounded by the `cluster.capacity.block.offering.candidates.max.count` preference - **5** by default, `-1` to disable the limit.

***

## Fallback instance types for a future-dated capacity reservation

A future-dated reservation's request can now also carry `fallbackCandidates` - other instance types the platform tries, biggest first, once the main instance type's own window is exhausted everywhere. Duplicates are merged away rather than rejected, and the list is sorted once, at creation, by GPU count, then vCPU count, then memory, all descending. All of them are approved together, once, at creation, so a candidate switch never asks an administrator again. See [Pools backed by a capacity reservation](../../manual/09_Manage_Cluster_nodes/9.1._Hot_node_pools.md#pools-backed-by-a-capacity-reservation).

How many fallback candidates one request may carry is bounded by the `cluster.capacity.reservation.fallback.candidates.max.count` preference - **5** by default, `-1` to disable the limit.

A fallback candidate whose instance type has no price catalogue entry in the pool's region is now rejected at creation, rather than silently accepted and sorted last among the candidates.

***

## A node pool's own run parameters for a run bound to it

A shareable node pool's `launchConfig` may carry `additionalParameters` - run parameters for a run that names that pool. Previously they were applied only to a run that picked the pool in the launch form and reserved CPU, GPU or RAM from it. A run that names the pool through the `CP_CAP_POOL_ID` parameter instead - from the CLI, from an API call, or from a pipeline's own `config.json` - now receives them as well.

They arrive as defaults on this path: a parameter the launch request already sets keeps the value the request gave it, and an entry with no value is skipped. One limit: a `CP_CAP_CUSTOM_*` flag applied this way does not pull in that custom capability's own declared parameters, as capabilities are expanded before the pool is consulted - the capability's commands still run, and every other `CP_CAP_*` parameter is unaffected. See [Guaranteeing a run's capacity](../../manual/09_Manage_Cluster_nodes/9.1._Hot_node_pools.md#guaranteeing-a-runs-capacity).

***

## Parallel download into the disk buffer of a storage mount

A storage mounted with a disk buffer (the `read-disk-buffer-path` option of `pipe-fuse`, e.g. `-c read-disk-buffer-path=/pipe-storage-buffer/<id>` in `CP_PIPE_FUSE_OPTIONS` or `CP_CAP_MOUNT_OPTIONS_<storage id>`) downloads each file it reads into that buffer once, and then serves the reads from the local disk. Previously, it downloaded a file in 512 MB ranges, one after another, so the first read of a large file waited for the whole sequential download.

In the current version, the disk buffer downloads the ranges of a file in parallel:

- `read-disk-buffer-download-workers` (`CP_PIPE_FUSE_READ_DISK_BUFFER_DOWNLOAD_WORKERS`) is the number of ranges downloaded at the same time. The default is `8`. `1` downloads them one after another, as before
- `read-disk-buffer-read-ahead-size` (`CP_PIPE_FUSE_READ_DISK_BUFFER_READ_AHEAD_SIZE`) is the size of a range, in bytes. It no longer has a default: when it is not set, a file is split into as many equal ranges as there are workers. With one worker and no size, the ranges are 512 MB, as before

Each range is written straight to the local disk, into a partial file next to the buffer, so a download needs no more memory than before, whatever the size of the file. The partial file becomes the buffer only once the whole file is there. Previously, a mount that was killed during a download left a part of the file as its buffer, and the next mount with the same buffer path served that part as the whole file, until the buffer expired (`read-disk-buffer-ttl`). When a range fails, the other ranges of the file stop too, and the read fails. Also, an S3 storage mount now keeps up to 50 connections to S3 instead of 10, so the parallel ranges do not wait for each other.

## Launch capabilities: restrict a capability to certain users or roles

[#4955](https://github.com/epam/cloud-pipeline/issues/4955)

A custom capability in the **`launch.capabilities`** preference can now be shown only to some users: add an optional `permissions` list to its entry, with `"principal": true` for a user name and `"principal": false` for a role or group name. A capability with no `permissions`, or an empty list, is still shown to everyone, as before. This restricts the **Run capabilities** field of the launch form only - a run started via `pipe` or the API is not checked against it.

```json
{
  "cap1": {
    "name": "Capability 1",
    "cloud": "AWS:1",
    "permissions": [
      { "name": "ROLE_ADMIN", "principal": false },
      { "name": "USER1", "principal": true }
    ]
  },
  "cap2": {
    "name": "Capability 2",
    "cloud": "AWS:1"
  }
}
```

Here `cap1` is shown only to the members of `ROLE_ADMIN` and to `USER1`, while `cap2` stays visible to everyone.
See details in [10.9. Run capabilities](../../manual/10_Manage_Tools/10.9._Run_capabilities.md#custom-capabilities).

***

## Notable Bug fixes

### Unavailable cluster nodes are not detected

[#4647](https://github.com/epam/cloud-pipeline/issues/4647)

Previously, the platform checked only the first Kubernetes condition of a node to find out whether the node is unavailable. On clusters with `Flannel`, the first condition is `NetworkUnavailable`, which does not change when a node stops reporting its status. So such nodes were never detected as unavailable: their runs did not get the `NODE_UNAVAILABLE` tag, and the nodes were not terminated after the grace period.
Now, a node is unavailable when its `Ready` condition is `Unknown`, and the grace period (**`cluster.node.unavailable.grace.period.minutes`**) is counted from the moment Kubernetes marked the node unavailable.
Also, a free node is reused for a new run only if its `Ready` condition is `True`. Before, a node that was not ready, or not reachable at all, could be reused, and the new run could get stuck on it.
Please note: after the update, nodes that have already been unavailable for longer than the grace period are terminated during the first autoscaler cycle, and their runs fail.

### `pipe` CLI for macOS ARM needs Rosetta

[#4953](https://github.com/epam/cloud-pipeline/issues/4953)

Previously, the `pipe` CLI distribution for Mac devices with ARM-based chips (Apple Silicon, the **MacOS-ARM** installation option, `pipe-osx-arm.tar.gz`) was built for Intel (`x86_64`) processors, both `pipe` and the bundled `pipe-fuse`. So it could run only through Rosetta.
Now, it is a native `arm64` build, and it runs without Rosetta.
