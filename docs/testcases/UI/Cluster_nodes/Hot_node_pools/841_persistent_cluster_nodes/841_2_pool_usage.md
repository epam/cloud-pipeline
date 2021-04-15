# Checking hot node pool usage

**Preparations**:

Perform [_841\_1_](841_1_pool_creation.md) case

**Actions**:

1. Login as admin user from the prerequisites of [_841\_1_](841_1_pool_creation.md) case
2. Open the **Tools** page
3. Find and open the tool from step 14 of [_841\_1_](841_1_pool_creation.md) case
4. Click **v** button near the **Run** button
5. Click **Custom settings** in the list
6. Expand the **Exec environment** section
7. Set the _Node type_ the same as at step 11 of [_841\_1_](841_1_pool_creation.md) case
8. Set the _Cloud Region_ the same as at step 9 of [_841\_1_](841_1_pool_creation.md) case
9. Set the _Disk_ size equals the value set at step 12 of [_841\_1_](841_1_pool_creation.md) case minus `80`
10. Expand the **Advanced** section
11. Set `spot` type for the _Price type_
12. Click the **Launch** button
13. In the appeared popup, click the **Launch** button
14. At the **Runs** page, click the just-launched run
15. Expand the **Instance** section
16. Wait until the **IP** label appears
17. Click the hyperlink near the **IP** label
18. Open the **Cluster state** page
19. Click the **HOT NODE POOLS** tab
20. Click the node pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case

**After**:

- stop the run launched at step 13

***

**Expected result**:

After step 16:

- "_Running for_" value at the Run logs page is less than 30 sec
- Disk size (displayed in **Instance** section) equals the value specified at step 12 of [_841\_1_](841_1_pool_creation.md) case

After step 17:

- the node name is one from saved names at step 22 of [_841\_1_](841_1_pool_creation.md) case
- the node header contains the label with the pool name specified at step 5 of [_841\_1_](841_1_pool_creation.md) case
- the _Run ID_ label contains the run ID launched at step 13 and doesn't contain text construction `P-`

After step 19, at the panel of the pool created at step 19 of [_841\_1_](841_1_pool_creation.md) case, there is the label `1/2`

After step 20:

- one node has the label starts from `RUN ID P-`
- and second node has the label with the run ID launched at step 13
