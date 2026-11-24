# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)
- [Kubelet metrics collector in place of heapster](#kubelet-metrics-collector-in-place-of-heapster)

***

- [Notable Bug fixes](#notable-bug-fixes)
    - [Unavailable cluster nodes are not detected](#unavailable-cluster-nodes-are-not-detected)

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

## Kubelet metrics collector in place of heapster

The monitoring data behind the node and run charts, the idle, network and overload run notifications, the free disk check before a commit or a pause, and the deployment autoscaler come from heapster **1.5.4**. heapster is retired, and it reads kubelet endpoints that current Kubernetes releases no longer serve.

In the current version, a new service, `cp-metrics-collector`, reads each kubelet's stats summary and cAdvisor metrics and writes documents of heapster's shape into the same `heapster-*` indices of `cp-heapster-elk`, so the platform reads them unchanged. Over 30 minutes side by side with heapster on Kubernetes **1.15.4**, the node's CPU, memory, network and disk averages that the platform's monitors, charts and autoscaler read agreed within 1%, and a test run's CPU and network within 0.2%; a pod's memory is lower by its pause container's few hundred KB. The service's README lists where its documents still differ from heapster's. It works on Kubernetes **1.15** and on current releases (checked on **1.15.4** and **1.37.0**).

Before heapster is removed, the collector can run beside it under another index name, and its compare tool shows, per node and pod, how the two differ. `pipectl` does not install it yet: see [Replace heapster with cp-metrics-collector](../../operation/migrate_heapster_to_metrics_collector.md) for the steps, and `deploy/docker/cp-metrics-collector/README.md` for the service.

***

## Notable Bug fixes

### Unavailable cluster nodes are not detected

[#4647](https://github.com/epam/cloud-pipeline/issues/4647)

Previously, the platform checked only the first Kubernetes condition of a node to find out whether the node is unavailable. On clusters with `Flannel`, the first condition is `NetworkUnavailable`, which does not change when a node stops reporting its status. So such nodes were never detected as unavailable: their runs did not get the `NODE_UNAVAILABLE` tag, and the nodes were not terminated after the grace period.
Now, a node is unavailable when its `Ready` condition is `Unknown`, and the grace period (**`cluster.node.unavailable.grace.period.minutes`**) is counted from the moment Kubernetes marked the node unavailable.
Also, a free node is reused for a new run only if its `Ready` condition is `True`. Before, a node that was not ready, or not reachable at all, could be reused, and the new run could get stuck on it.
Please note: after the update, nodes that have already been unavailable for longer than the grace period are terminated during the first autoscaler cycle, and their runs fail.
