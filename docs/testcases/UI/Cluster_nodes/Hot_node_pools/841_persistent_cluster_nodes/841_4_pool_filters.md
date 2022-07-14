# Checking filters for hot node pool

Test verifies creation hot node pool with filters

**Preparations**:

1. Perform [_841\_1_](841_1_pool_creation.md) case
2. Login as admin user from the prerequisites of [_841\_1_](841_1_pool_creation.md) case
3. Open the **Library** page
4. Create a new `DEFAULT` pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Cluster state** page | |
| 2 | Click the **HOT NODE POOLS** tab | |
| 3 | Click the **Edit** button for the pool node created at step 19 of [_841\_1_](841_1_pool_creation.md) case | |
| 4 | Click the "**+ Add filter**" button | |
| 5 | Click the **Select property** dropdown list and select `Pipeline` item in the list | |
| 6 | Click the **Select pipeline** dropdown list and select the name of the pipeline created at step 4 of the preparations | |
| 7 | Click the **SAVE** button | |
| 8 | Repeat steps 2-16 of [_841\_2_](841_2_pool_usage.md) case | <li> disk size (displayed in **Instance** section) does not equal the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case <li> the node name (near the **IP** label) doesn't equal to any from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case |
| 9 | Stop the run launched at step 8 | |
| 10 | Open the **Library** page | |
| 11 | Open the pipeline created at step 4 of the preparations | |
| 12 | Click the **RUN** button | |
| 13 | Repeat steps 6-11 of [_841\_2_](841_2_pool_usage.md) case | |
| 14 | Click the **Docker image** field | |
| 15 | Select the same tool as at step 14 of [_841\_1_](841_1_pool_creation.md) case | |
| 16 | Click the **OK** button | |
| 17 | Repeat steps 12-16 of [_841\_2_](841_2_pool_usage.md) case | <li> disk size (displayed in **Instance** section) equals the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case <li> the node name (near the **IP** label) is one from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case |

**After**:

- open the configuration of the pool node created at step 19 of [_841\_1_](841_1_pool_creation.md) case and delete the filter added at steps 4-7
- delete the pipeline created at step 4 of the preparations