# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)
- [Fallback instance types for a future-dated capacity reservation](#fallback-instance-types-for-a-future-dated-capacity-reservation)

***

- [Notable Bug fixes](#notable-bug-fixes)

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

***

## Notable Bug fixes
