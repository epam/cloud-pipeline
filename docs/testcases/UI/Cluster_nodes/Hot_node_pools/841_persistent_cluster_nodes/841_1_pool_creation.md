# Hot node pool creation

**Prerequisites**:

- admin user

**Preparations**:

1. Login as admin user from the prerequisites
2. Open the **Settings** page
3. Open the **CLOUD REGIONS** tab
4. Saved the selected region name (_default region_)
5. Open the **PREFERENCES** tab
6. Find the preference `cluster.reassign.disk.delta`
7. Set the value `100` for the preference from step 4
8. Save changes

**Actions**:

1. Login as admin user from the prerequisites
2. Open the **Cluster state** page
3. Click the **HOT NODE POOLS** tab
4. Click the "**+ Create**" button
5. Specify valid _Pool name_
6. Specify the "_Starts on_" day - the current day of the week then specify the "_Starts on_" time `00:00`
7. Specify the "_Ends on_" day - the next day of the week then specify the "_Ends on_" time `23:59`
8. Specify the _Nodes count_ - `2`
9. Select the _Region_ - the same one as was saved at step 4 of the preparations
10. Select `spot` type for the _Price type_
11. Select any _Instance type_
12. Specify the _Disk_ size between `100` and `150` (e.g. `120`)
13. Click the "**+ Add docker image**" button
14. In the appeared field, select any tool from the `Default registry` and `library` group (e.g. `ubuntu`)
15. Select the _Condition_ - `Matches all filters ("and")`
16. Click the "**+ Filter**" button
17. Select the _Property_ in the appeared popup - `Run owner`
18. Select the _Owner_ - the admin user name from the prerequisites
19. Click the **CREATE** button
20. Click the just-created node pool item
21. Click the **Refresh** button every minute until two nodes appear in the node list
22. Save node names
23. Click the **CLUSTER** tab
24. Click the **HOT NODE POOLS** tab

***

**Expected result**:

After step 19, the new item appears in the pools list. This item contains:

- the name equals to the specified at step 5
- labels: `2 NODES`, `<instance_type>` equals to the selected at step 11, `SPOT`, `<disk_size> GB` equals to the disk size specified at step 12
- label with the tool name selected at step 14
- label with the schedule in format: `<day_starts_on>, 00:00 - <day_ends_on>, 23:59` with the values of week days specified at steps 6-7
- buttons: **Edit** and **Remove**

After step 20, the nodes page appears that contains at least header with the pool name specified at step 5

After step 21:

- the header becomes `<pool_name> nodes (2 nodes, 2 nodes with associated RunId)` where `<pool_name>` is the pool name specified at step 5
- two nodes appear. Each appeared node has:  
    - the label with the pool name specified at step 5
    - the label starts from `RUN ID P-`

After step 23, the full node cluster appears. There are two nodes that have:

- the label with the pool name specified at step 5
- the label starts from `RUN ID P-`

After step 24, at the panel of the pool created at step 19, there is the label `0/2`
