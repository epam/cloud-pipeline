# Checking the autoscaling of the hot node pool

**Preparations**:

Perform [_841\_1_](841_1_pool_creation.md) case

**Actions**:

1. Open the **Cluster state** page
2. Click the **HOT NODE POOLS** tab
3. Click the **Edit** button for the pool node created at step 19 of [_841\_1_](841_1_pool_creation.md) case
4. Enabled the **Autoscaled** checkbox
5. Set the value `4` for the **Max Size** field
6. Set the value `70` for the **Scale Up Threshold** field
7. Set the value `30` for the **Scale Down Threshold** field
8. Set the value `3` for the **Scale Step** field
9. Click the **SAVE** button
10. Repeat steps 2-13 of [_841\_2_](841_2_pool_usage.md) case
11. Repeat steps 2-13 of [_841\_2_](841_2_pool_usage.md) case
12. Open the **Cluster state** page
13. Click the **HOT NODE POOLS** tab
14. Click the node pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case
15. Click the **Refresh** button every minute until four nodes will be in the node list
16. Repeat step 13
17. Repeat steps 2-13 of [_841\_2_](841_2_pool_usage.md) case
18. Repeat steps 2-13 of [_841\_2_](841_2_pool_usage.md) case
19. Repeat steps 12-13
20. Open the **Runs** page
21. Stop runs launched at steps 10, 11, 17, 18
22. Repeat steps 12-14
23. Click the **Refresh** button every minute until two nodes will stay in the node list
24. Repeat step 13

**After**:

- open the **HOT NODE POOLS** tab and delete the pool node created at [_841\_1_](841_1_pool_creation.md) case

***

**Expected result**:

After step 4, the following fields appear:

- **Min Size**
- **Max Size**
- **Scale Up Threshold**
- **Scale Down Threshold**
- **Scale Step**

After step 9, at the panel of the pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case, the label appears `AUTOSCALED (2-4 NODES)`

After steps 13, 24: at the panel of the pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case, there is the label `2/2`

After step 16, at the panel of the pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case, there is the label `2/4`

After step 16, at the panel of the pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case, there is the label `4/4`
