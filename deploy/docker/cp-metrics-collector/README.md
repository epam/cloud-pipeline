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
  `heapster-cpu`, `heapster-memory`, `heapster-network` and `heapster-filesystem`.

The metrics, and where they come from:

| Metric | node | pod | pod_container |
|---|---|---|---|
| `cpu/usage` (ns, cumulative), `cpu/usage_rate` (millicores) | `node.cpu` | the pod's cgroup | `containers[].cpu` |
| `memory/usage`, `working_set`, `rss`, `page_faults`, `major_page_faults` and their rates | `node.memory` | the pod's cgroup | `containers[].memory` |
| `network/rx`, `tx`, `rx_errors`, `tx_errors` and their rates | sum of `node.network.interfaces` | sum of the pod's `interfaces` | - |
| `cpu/request`, `cpu/limit` (millicores), `memory/request`, `memory/limit` (bytes) | sum of its pods | sum of its containers | the pod spec, 0 where unset |
| `cpu/node_capacity`, `node_allocatable`, `node_utilization`, `node_reservation`, same for `memory/` | node status and usage | - | - |
| `filesystem/usage`, `limit`, `inodes`, `inodes_free`, `available` | root cgroup's devices from `/metrics/cadvisor` | - | `containers[].rootfs` |

Rates are computed as heapster computed them: between two batches, when the stats are newer and the
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
- **Pod usage comes from the pod's cgroup.** heapster added the containers' usage to the pause
  container's. It is the same sum. A pod the summary has no pod-level stats for gets its containers'
  sum.
- **No utilization on a node's first batch.** heapster wrote 0 there, before it had a rate.
- **No rate when a counter goes down.**
- **Not written:** the `sys_container`, `ns` and `cluster` sets, the `general` family (`uptime`,
  `restart_count`), `memory/cache`, disk I/O, accelerators, and the stub sets heapster made for
  containers with no stats yet. Nothing on the platform reads them.
- **A node with no root cgroup series** (a Windows node; `/metrics/cadvisor` absent or empty) gets
  the summary's filesystems, as `rootfs` and, when it is another device, `imagefs`. A
  `/metrics/cadvisor` that fails for a moment writes no filesystem documents for that node in that
  batch, rather than series under other names.

## How it reads the cluster

- The API server: `GET /api/v1/nodes`, `/api/v1/pods` and `/api/v1/namespaces`, with
  `resourceVersion=0`, so the API server answers from its cache.
- Each Ready node's kubelet, at its InternalIP and the port in `status.daemonEndpoints`:
  `/stats/summary` and `/metrics/cadvisor`, with the service account token. Up to
  `CP_METRICS_COLLECTOR_THREADS` nodes at once.
- The token is read again every minute, so a bound service account token (1.21 and later) can be
  rotated. Proxy settings in the environment are ignored: all of this traffic is in the cluster.
- The kubelets of a kubeadm cluster serve self-signed certificates, so they are not verified, as
  heapster's `insecure=true`. Set `CP_METRICS_COLLECTOR_KUBELET_CA_FILE` to verify them.
- The RBAC it needs is in `deploy/contents/k8s/cp-metrics-collector/cp-metrics-collector-sa.yaml`.

`GET /healthz` on port 8082 answers 200 while batches keep finishing. A batch that Elasticsearch or
the API server failed still counts, since restarting the collector would not bring them back; the
answer says when it last wrote.

## Settings

The `CP_HEAPSTER_*` and `CP_KUBE_*` ones come from `cp-config-global`.

| Variable | Default | |
|---|---|---|
| `CP_METRICS_COLLECTOR_RESOLUTION` | `CP_HEAPSTER_METRIC_RESOLUTION`, else `30s` | the batch interval: `30`, `30s`, `1m` |
| `CP_METRICS_COLLECTOR_ELASTIC_URL` | `CP_HEAPSTER_ELK_INTERNAL_SCHEME`://`CP_HEAPSTER_ELK_INTERNAL_HOST`:`CP_HEAPSTER_ELK_INTERNAL_PORT` | |
| `CP_METRICS_COLLECTOR_INDEX` | `heapster` | the indices are `<index>-YYYY.MM.dd` |
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

```bash
export CP_DOCKER_DIST_SRV=... CP_HEAPSTER_VERSION=...
envsubst < cp-metrics-collector-sa.yaml | kubectl apply -f -
envsubst < cp-metrics-collector-dpl.yaml | kubectl apply -f -
```

### Beside heapster first

The two must not write to the same indices: the API would average both. Run the collector under
another index name first, which must **not** start with `heapster-`: the API's index retention
parses the date after `heapster-`, and fails on any other text there.

```bash
export CP_METRICS_COLLECTOR_INDEX=cp-metrics-collector
envsubst < cp-metrics-collector-dpl.yaml | kubectl apply -f -
# after 30 minutes or more
kubectl exec deploy/cp-metrics-collector -- python3 -m metrics_collector.compare \
    --right cp-metrics-collector --minutes 30 --threshold 10
```

The compare tool prints, for each node, node device and pod, the averages the platform reads from
`heapster-*` and from `cp-metrics-collector-*`, with their difference, and with `--threshold` only
the rows that differ by more percent, or that one side lacks. The API does not delete the
comparison indices: delete them once the comparison is over (`curl -XDELETE
http://<cp-heapster-elk>/cp-metrics-collector-*`).

To switch: delete the `cp-heapster` deployment, and apply the collector's spec again with
`CP_METRICS_COLLECTOR_INDEX` empty.

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
