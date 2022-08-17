# Checking hot node pool usage by non-admin user

Test verifies that non-admin user can use hot node pool at launch run

**Prerequisites**:

- non-admin user with the ability to run tools from `Default registry/library`

**Preparations**:

Perform [_841\_1_](841_1_pool_creation.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as non-admin user from the prerequisites | |
| 2 | Repeat steps 2-16 of [_841\_2_](841_2_pool_usage.md) case | <li> disk size (displayed in **Instance** section) does not equal the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case <li> the node name (near the **IP** label) doesn't equal to any from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case |
| 3 | Stop the run launched at step 2 | |
| 4 | Logout | |
| 5 | Login as admin user from the prerequisites of [_841\_1_](841_1_pool_creation.md) case | |
| 6 | Open the **Cluster state** page | |
| 7 | Click the **HOT NODE POOLS** tab | |
| 8 | Click the **Edit** button for the pool node created at step 19 of [_841\_1_](841_1_pool_creation.md) case | |
| 9 | Click the "**+ Add filter**" button | |
| 10 | Click the **Select property** dropdown list and select `Run owner` item in the list | |
| 11 | Click the **Select owner** dropdown list and select the name of the non-admin user from the prerequisites | |
| 12 | Click the **Condition** dropdown list and select `Matches any filter ("or")` item in the list | |
| 13 | Click the **SAVE** button | |
| 14 | Logout | |
| 15 | Login as non-admin user from the prerequisites | |
| 16 | Repeat steps 2-16 of [_841\_2_](841_2_pool_usage.md) case | <li> disk size (displayed in **Instance** section) equals the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case <li> the node name (near the **IP** label) is one from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case |
| 17 | Stop the run launched at step 16 | |

**After**:

- stop the run launched at step 13
- open the configuration of the pool node created at step 19 of [_841\_1_](841_1_pool_creation.md) case and delete the filter added at steps 9-13