# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)

***

- [Notable Bug fixes](#notable-bug-fixes)
    - [The cleanup of the in-app notifications runs every 86.4 seconds](#the-cleanup-of-the-in-app-notifications-runs-every-864-seconds)

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

### The cleanup of the in-app notifications runs every 86.4 seconds

[#4918](https://github.com/epam/cloud-pipeline/issues/4918)

Previously, the API removed the old in-app notifications (older than `system.notifications.exp.period` days) every 86.4 seconds: the period of this cleanup, the API property `scheduled.notifications.cleanup.sec`, was read in milliseconds.
Now, it is read in seconds, as its name says: with the default `86400`, the cleanup runs when the API starts and then once a day. A deployment that set this property in milliseconds has to set it in seconds.
