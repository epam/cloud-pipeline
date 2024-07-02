# Checking disk delta for a node from the pool

Test verifies disk delta for a node from the pool

**Preparations**:

1. Perform [_841\_1_](841_1_pool_creation.md) case
2. Open the **Settings** page
3. Open the **PREFERENCES** tab
4. Find the preference `cluster.reassign.disk.delta`
5. Set the value `50` for the preference from step 4
6. Save changes

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites of [_841\_1_](841_1_pool_creation.md) case | |
| 2 | Repeat steps 2-16 of [_841\_2_](841_2_pool_usage.md) case | <li> disk size (displayed in **Instance** section) does not equal the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case <li> the node name (near the **IP** label) doesn't equal to any from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case |
| 3 | Stop the run launched at step 2 | |
| 4 | Click the **RERUN** button | |
| 5 | Expand the **Exec environment** section | |
| 6 | Set the _Disk_ size equals the value set at step 12 of [_841\_1_](841_1_pool_creation.md) case minus `50` | |
| 7 | Repeat steps 12-16 of [_841\_2_](841_2_pool_usage.md) case | <li> disk size (displayed in **Instance** section) equals the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case <li> the node name (near the **IP** label) is one from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case |
| 8 | Stop the run launched at step 7 | |

**After**:

- return the value `100` for the preference `cluster.reassign.disk.delta`