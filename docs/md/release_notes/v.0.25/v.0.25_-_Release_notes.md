# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)
- [Capacity block offering search over several instance types](#capacity-block-offering-search-over-several-instance-types)

***

- [Notable Bug fixes](#notable-bug-fixes)
    - [A user who can see a cluster run does not see its auto-scaled workers](#a-user-who-can-see-a-cluster-run-does-not-see-its-auto-scaled-workers)

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

### A user who can see a cluster run does not see its auto-scaled workers

[#4915](https://github.com/epam/cloud-pipeline/issues/4915)

Previously, a user who could see a cluster run - e.g. a reader of its pipeline - did not see the workers of an auto-scaled cluster when expanding the run in the runs table, and could not open them. A worker is a tool run of the cluster's owner with no pipeline, and each child run was checked on its own.
Now, a child run with no pipeline that belongs to the owner of its parent run - such as a worker of an auto-scaled cluster - can be viewed, with its logs and its node, by every user who can view the parent run by its own permissions. A child run of a pipeline keeps the rules of its pipeline, and stopping a child run or changing it still requires the permissions on the child run itself.
