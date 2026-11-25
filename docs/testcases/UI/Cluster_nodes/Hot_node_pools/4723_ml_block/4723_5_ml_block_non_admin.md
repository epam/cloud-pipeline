# [MANUAL] ML Block mode available to a node pool manager

Test verifies that a non-admin user with the `ROLE_NODE_POOL_MANAGER` role is offered `Future
dated` and `ML Block` modes but not `Default` when creating a hot node pool, and that the
run-owner filter is pre-filled for them in both reservation modes. The case does not search for or
select an offering - it stops before any step that could lead to purchasing a capacity block.

**Prerequisites**:

- a non-admin user with the `ROLE_NODE_POOL_MANAGER` role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user from the prerequisites | |
| 2 | Open the **Cluster state** page | |
| 3 | Click the **HOT NODE POOLS** tab | |
| 4 | Click the "**+ Create**" button | The dialog opens already in `Future dated` mode - its `Mode:` field shows `Future dated` |
| 5 | Click the **Mode** dropdown list | The list offers `Future dated` and `ML Block` only - `Default` is not among the options |
| 6 | Scroll down to the **Filters** section | A filter is already present: _Property_ `Run owner`, with the logged-in user's own name as its value |
| 7 | Click the **Condition** dropdown list in the **Filters** section | It opens and can be changed - it is not disabled |
| 8 | Observe the "**+ Add filter**" button | It is not disabled - another filter can be added |
| 9 | Click the **Mode** dropdown list and select `ML Block` | The section above **Configuration** is renamed `ML Block`, and the `Search in region`/candidates controls appear |
| 10 | Scroll down to the **Filters** section again | The same `Run owner` filter from step 6 is still there, with the same value |
| 11 | Click the **Condition** dropdown list in the **Filters** section | It is still not disabled |
| 12 | Observe the "**+ Add filter**" button | It is still not disabled |
| 13 | Click the **CANCEL** button | The dialog closes without creating a pool |
