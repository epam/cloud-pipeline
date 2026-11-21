# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)

***

- [Notable Bug fixes](#notable-bug-fixes)
    - [`cluster.keep.alive.minutes` of 60 or more stops every free node at once](#clusterkeepaliveminutes-of-60-or-more-stops-every-free-node-at-once)

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

## Notable Bug fixes

### `cluster.keep.alive.minutes` of 60 or more stops every free node at once

[#4919](https://github.com/epam/cloud-pipeline/issues/4919)

Previously, `cluster.keep.alive.minutes` accepted any value greater than 0. With the default keep-alive policy, `MINUTES_TILL_HOUR`, a free node is shut down when 2 to this many minutes are left until the next whole hour since it started, so a value of 60 or more shut every free node down at once: free nodes were not kept for the next runs.
Now, with `MINUTES_TILL_HOUR`, `cluster.keep.alive.minutes` must be from 1 to 59, and `cluster.keep.alive.policy` cannot be set to `MINUTES_TILL_HOUR` - nor deleted back to it - while `cluster.keep.alive.minutes` is 60 or more; the error says why. With `IDLE_MINUTES` any value greater than 0 is accepted, as before. A value already set is not changed: check it on a deployment that uses `MINUTES_TILL_HOUR`.
