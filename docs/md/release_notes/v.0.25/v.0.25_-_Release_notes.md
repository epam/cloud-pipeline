# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)
- [ML Block mode for a hot node pool](#ml-block-mode-for-a-hot-node-pool)
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

## ML Block mode for a hot node pool

Creating a hot node pool now offers a third **Mode**, **ML Block**, beside **Default** and **Future dated**: a pool backed by an AWS Capacity Block for ML, bought whole for a fixed window AWS currently has for sale, rather than searched for over a date range the way a **Future dated** reservation is.

In this mode, the **Search from** / **Search until** / **Duration (days)** fields (the last with a 1-day minimum, rather than 14, and **Search until** optional, unlike a **Future dated** reservation's) are joined by a **Search in region** field and one or more candidate instance types, each with a nodes count - the region moves into this section because it is itself a search criterion, and the pool's own **Region** field becomes read-only. As the candidates, the window, the duration or the region change, the platform searches AWS for offerings matching each candidate and shows them as cards, each listing the offering's availability zone, instance type, window and up-front price, in that order - or **No offerings** once a search has settled and found none. Each offering is its own card to pick, whether a row shows one or several. Picking one fills the pool's **Instance type** and **Nodes count** read-only from that exact offering, and **CREATE** is refused until one is picked. Changing **Search in region** clears the candidates, the search results and the pick.

An offering's window, and a purchased block's own granted window, are now shown converted to the viewer's own local time - an earlier release rendered the backend's UTC wall-clock values as if they were already local, understating a window's start and end by the viewer's own offset from UTC.

Picking a day for **Search from** / **Search until** - in either a **Future dated** or an **ML Block** reservation - now sends that day's true start and end instant, computed in the viewer's own zone, rather than the day's wall-clock boundary sent as if the viewer were already in UTC; a viewer elsewhere could otherwise search, or request, a window off by their own offset from the day they actually picked.

The up-front fee is non-refundable, and a purchased block cannot be cancelled - it simply ends at its fixed time. See [ML Block node pools](../../manual/09_Manage_Cluster_nodes/9.1._Hot_node_pools.md#ml-block-node-pools).

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
