# cp-metrics-collector

The replacement of heapster (`k8s.gcr.io/heapster-amd64:v1.5.4`, the `cp-heapster` deployment). It
reads each kubelet's `/stats/summary` and `/metrics/cadvisor` and writes the documents heapster
wrote into `cp-heapster-elk` (Elasticsearch 5), in the same `heapster-YYYY.MM.dd` indices. The
platform's readers of those indices need no change:

- the API's `dao/monitoring/metricrequester/`: the node and pod charts, the idle, network and overload
  run monitors, the free disk check before a commit or a pause;
- `cp-deployment-autoscaler` (`autoscaler/elastic.py`).

It works on Kubernetes 1.15 and on current releases (tested on 1.15.4 with Docker and 1.37.0 with
containerd). It uses only core v1 lists and the kubelet's own endpoints, over plain HTTPS: no
Kubernetes or Elasticsearch client library ties it to one version.

## What it writes

Every `CP_HEAPSTER_METRIC_RESOLUTION` (30 s), at the end of the window plus 5 s, one batch stamped
with the window's end, as heapster did.

- One document per metric family (`cpu`, `memory`, `network`, `filesystem`, the Elasticsearch type)
  for each `node`, `pod` and `pod_container` (`MetricsTags.type`), and one `filesystem` document per
  device (`MetricsTags.resource_id`).
- `<Family>MetricsTimestamp`: the batch time, an RFC 3339 string in UTC. `Metrics.<name>.value`.
- `MetricsTags`: `nodename`, `hostname`, `host_id`, `cluster_name` (`default`), `labels` (the node's
  or pod's labels, `key:value` sorted and joined by `,`), `schedulable` for a node; `pod_name`,
  `pod_id`, `namespace_name`, `namespace_id` for a pod; also `container_name` and
  `container_base_image` for a container.
- The index is created with heapster's mapping when it does not exist, and gets the aliases
  `heapster-cpu`, `heapster-memory`, `heapster-network` and `heapster-filesystem`. It is checked
  before every batch, as heapster did: one deleted during the day, or lost with Elasticsearch's data,
  is created again, after the index template `cp-metrics-collector-heapster` for `heapster-*`, which
  holds the same mapping and aliases.

The metrics, and where they come from:

| Metric | node | pod | pod_container |
|---|---|---|---|
| `cpu/usage` (ns, cumulative), `cpu/usage_rate` (millicores) | `node.cpu` | the pod's cgroup | `containers[].cpu` |
| `memory/usage`, `working_set`, `rss`, `page_faults`, `major_page_faults` and their rates | `node.memory` | sum of its containers (no cumulative `page_faults`) | `containers[].memory` |
| `network/rx`, `tx`, `rx_errors`, `tx_errors` and their rates | sum of `node.network.interfaces` | sum of the pod's `interfaces` | - |
| `cpu/request`, `cpu/limit` (millicores), `memory/request`, `memory/limit` (bytes) | sum of its pods | sum of its containers | the pod spec's container or native sidecar, 0 where unset |
| `cpu/node_capacity`, `node_allocatable`, `node_utilization`, `node_reservation`, same for `memory/` | node status and usage | - | - |
| `filesystem/usage`, `limit`, `inodes`, `inodes_free`, `available` | root cgroup's devices from `/metrics/cadvisor` | - | `usage` and `limit` of `containers[].rootfs` |

Rates are computed by heapster's rules: between two batches, when the stats are newer and the
start time is the same. `cpu/usage_rate` is an integer, `1000 * delta ns / delta ns`.
`cpu/node_utilization` is `cpu/usage_rate` over the allocatable millicores, and
`memory/node_utilization` is `memory/usage` (not the working set) over the allocatable bytes.

### Where it differs from heapster

- **Node devices.** The cAdvisor of current kubelets reports each tmpfs by its mount point (`/run`,
  `/tmp`, `/dev/shm`), and also overlay and NFS mounts. heapster's (Kubernetes 1.15) reported block
  devices, and all tmpfs mounts as one `tmpfs`, which the API leaves out of a node's disk space. So
  `/dev/shm` is written as `tmpfs`, and the other tmpfs, overlay and NFS devices are not written.
- **No `available` for a device the summary does not know.** cAdvisor's metrics have no available
  bytes. They come from the summary, for the devices of the kubelet's and the runtime's filesystems.
- **A pod's CPU comes from its cgroup.** heapster added the containers' rates to the pause
  container's, the same sum. A pod the summary has no pod-level CPU for gets its containers' sum.
  A pod's cumulative `cpu/usage` was the pause container's own in heapster, and is the pod's here.
- **A pod's memory is its containers' sum, without the pause container's** (a few hundred KB). The
  pod cgroup is not used: it keeps what finished containers left charged to it. On 1.15, canal's
  init containers left 34 MB of page cache in its pod cgroup.
- **`container_base_image` is the image of the pod spec.** heapster wrote the one cAdvisor reported,
  which with Docker is often the image ID (`sha256:...`).
- **Rate timing.** The summary gives the time of its stats in whole seconds, where heapster had
  cAdvisor's nanoseconds. One 30 s rate can be off by up to about 3% (10% at 10 s), and a busy node's
  `cpu/node_utilization` can go a little above 1 for one batch. The errors do not add up: over the
  windows the readers average, the totals are exact.
- **Native sidecars** (init containers with `restartPolicy: Always`, Kubernetes 1.28 and later) run
  beside the containers, and their requests, limits and image count as theirs. Pod-level
  `spec.resources` is not read.
- **No utilization on a node's first batch.** heapster wrote 0 there, before it had a rate.
- **No rate when a counter goes down.**
- **Not written:** the `sys_container`, `ns` and `cluster` sets, the `general` family (`uptime`,
  `restart_count`), `memory/cache`, disk I/O, accelerators, the pods' own filesystem documents (the
  pause container's), the `available` and `inodes` of a container's filesystem, and the stub sets
  heapster made for containers with no stats yet. Nothing on the platform reads them.
- **A node with no root cgroup series** (a Windows node; `/metrics/cadvisor` absent or empty) gets
  the summary's filesystems, as `rootfs` and, when it is another device, `imagefs`. A
  `/metrics/cadvisor` that fails for a moment writes no filesystem documents for that node in that
  batch, rather than series under other names.

## How it reads the cluster

- The API server: `GET /api/v1/nodes`, `/api/v1/pods` and `/api/v1/namespaces`, with
  `resourceVersion=0`, so the API server answers from its cache. When it does not answer, the last
  node and pod lists stand in for up to 10 minutes, as heapster wrote on from its informers' caches;
  without the namespaces only `namespace_id` is missing. That carries the collector through an API
  server restart. A longer outage stops the scrapes sooner anyway: the kubelets check the token with
  the API server, and keep the answer for a few minutes only (2 by default).
- Each Ready node's kubelet, at its InternalIP and the port in `status.daemonEndpoints`:
  `/stats/summary` and `/metrics/cadvisor`, with the service account token. Up to
  `CP_METRICS_COLLECTOR_THREADS` nodes at once.
- The token is read again every minute, so a bound service account token (1.21 and later) can be
  rotated. Proxy settings in the environment are ignored: all of this traffic is in the cluster.
- The kubelets of a kubeadm cluster serve self-signed certificates, so they are not verified, as
  heapster's `insecure=true`. Set `CP_METRICS_COLLECTOR_KUBELET_CA_FILE` to verify them.
- The RBAC it needs is in `deploy/contents/k8s/cp-metrics-collector/cp-metrics-collector-sa.yaml`:
  reading nodes, pods and namespaces, and the kubelets' stats and metrics. heapster ran as the
  `default` service account, which `install-master.sh` binds to `cluster-admin`.

The token is still worth guarding: the pod list carries the pods' environment, and with it the API
tokens of the runs. With unverified kubelet certificates, whatever answers at a node's address on
the kubelet port is sent the token.

`GET /healthz` on port 8082 answers 200 while batches keep finishing. A batch that Elasticsearch or
the API server failed still counts, since restarting the collector would not bring them back; the
answer says when it last wrote.

## Settings

The `CP_HEAPSTER_*` and `CP_KUBE_*` ones come from `cp-config-global`.

| Variable | Default | |
|---|---|---|
| `CP_METRICS_COLLECTOR_RESOLUTION` | `CP_HEAPSTER_METRIC_RESOLUTION`, else `30s` | the batch interval: `30`, `30s`, `1m` |
| `CP_METRICS_COLLECTOR_ELASTIC_URL` | `CP_HEAPSTER_ELK_INTERNAL_SCHEME`://`CP_HEAPSTER_ELK_INTERNAL_HOST`:`CP_HEAPSTER_ELK_INTERNAL_PORT` | |
| `CP_METRICS_COLLECTOR_INDEX` | `heapster` | the indices are `<index>-YYYY.MM.dd`; a name that reaches `heapster-*`, `cp-gpu-monitor-*` or `cp-network-events-*` is refused |
| `CP_METRICS_COLLECTOR_CLUSTER_NAME` | `default` | `MetricsTags.cluster_name` |
| `CP_METRICS_COLLECTOR_KUBELET_PORT` | `CP_KUBE_KUBELET_PORT`, else `10250` | for a node that reports no port |
| `CP_METRICS_COLLECTOR_KUBELET_CA_FILE` | none: not verified | |
| `CP_METRICS_COLLECTOR_KUBE_URL` | `https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT` | |
| `CP_METRICS_COLLECTOR_TOKEN_FILE`, `CP_METRICS_COLLECTOR_KUBE_CA_FILE` | the service account's | |
| `CP_METRICS_COLLECTOR_THREADS` | `10` | |
| `CP_METRICS_COLLECTOR_TIMEOUT` | `10`, at most half the resolution | seconds per request |
| `CP_METRICS_COLLECTOR_SCRAPE_OFFSET` | `5s` | |
| `CP_METRICS_COLLECTOR_PORT` | `8082` | `/healthz` |
| `CP_METRICS_COLLECTOR_LOG_LEVEL` | `INFO` | |

## Deploying

The image is `lifescience/cloud-pipeline:metrics-collector-<version>`, built by
`deploy/docker/build-dockers.sh` and the `Build-Pipectl-And-Dockers` workflow. Its specs are in
`deploy/contents/k8s/cp-metrics-collector/`. They take the same variables as the other specs
(`CP_DOCKER_DIST_SRV`, and `CP_HEAPSTER_VERSION` for the tag) and run the collector where heapster
runs, on the node labelled `cloud-pipeline/cp-heapster=true`. `pipectl` does not install it yet.

The steps, with the commands and their expected results, for a deployment that runs heapster and
for one that does not: `docs/md/operation/migrate_heapster_to_metrics_collector.md`. In short, the
collector runs beside heapster under another index name first (`cp-metrics-collector`), the compare
tool checks it against heapster, and then `cp-heapster` is scaled to 0 and the collector writes into
`heapster-*`. The two must never write to the same indices: the API would average both.

The collector refuses an index name that starts with `heapster-`: the API takes every such index for
a day of heapster's and parses the rest of its name as the date, which would stop its index
retention and fail the node and run charts. It also refuses the names whose indices or template
would reach the GPU stats or the network events in the same Elasticsearch (`cp`, `cp-gpu-monitor`).

The compare tool prints, for each node, node device and pod, the averages the platform reads from
`heapster-*` and from the collector's indices, with their difference, and with `--threshold` only
the rows that differ by more percent, or that one side lacks.

## Tests

Python 3.12, from this directory:

```bash
python -m pip install -r requirements.txt pytest
python -m pytest tests
```

The CI's `python-tests` job (`.github/workflows/java_tests.yml`) runs them.
`tests/fixtures/` holds real responses: `1.15` from a kubeadm 1.15.4 cluster on Docker (one node),
`1.37` from a kind 1.37.0 cluster on containerd (two nodes), each recorded twice, 30 s or more
apart, with a pod of two containers, with and without requests and limits, that burns CPU, writes
64 MiB to its root filesystem and talks on the network. The API lists keep only the fields the
collector reads.
