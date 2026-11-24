# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)
- [ML Block mode for a hot node pool](#ml-block-mode-for-a-hot-node-pool)

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

## ML Block mode for a hot node pool

Creating a hot node pool now offers a third **Mode**, **ML Block**, beside **Default** and **Future dated**: a pool backed by an AWS Capacity Block for ML, bought whole for a fixed window AWS currently has for sale, rather than searched for over a date range the way a **Future dated** reservation is.

In this mode, the **Search from** / **Search until** / **Duration (days)** fields (the last with a 1-day minimum, rather than 14) are joined by one or more candidate instance types, each with a nodes count. As the candidates, the window or the duration change, the platform searches AWS for offerings matching each candidate and shows them as cards, with their window, zone, nodes count and up-front price - or **No offerings** once a search has settled and found none. Picking a card fills the pool's **Instance type** and **Nodes count** read-only from that exact offering, and **CREATE** is refused until one is picked. Changing the **Region** clears the candidates, the search results and the pick.

The up-front fee is non-refundable, and a purchased block cannot be cancelled - it simply ends at its fixed time. See [ML Block node pools](../../manual/09_Manage_Cluster_nodes/9.1._Hot_node_pools.md#ml-block-node-pools).

***

## Notable Bug fixes

### Unavailable cluster nodes are not detected

[#4647](https://github.com/epam/cloud-pipeline/issues/4647)

Previously, the platform checked only the first Kubernetes condition of a node to find out whether the node is unavailable. On clusters with `Flannel`, the first condition is `NetworkUnavailable`, which does not change when a node stops reporting its status. So such nodes were never detected as unavailable: their runs did not get the `NODE_UNAVAILABLE` tag, and the nodes were not terminated after the grace period.
Now, a node is unavailable when its `Ready` condition is `Unknown`, and the grace period (**`cluster.node.unavailable.grace.period.minutes`**) is counted from the moment Kubernetes marked the node unavailable.
Also, a free node is reused for a new run only if its `Ready` condition is `True`. Before, a node that was not ready, or not reachable at all, could be reused, and the new run could get stuck on it.
Please note: after the update, nodes that have already been unavailable for longer than the grace period are terminated during the first autoscaler cycle, and their runs fail.
