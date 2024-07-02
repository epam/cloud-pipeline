# Hot node pool creation

Test verifies hot node pool creation and its shown in the *Cluster* and *Hot node pools* tables.

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

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Cluster state** page | |
| 3 | Click the **HOT NODE POOLS** tab | |
| 4 | Click the "**+ Create**" button | |
| 5 | Specify valid _Pool name_ | |
| 6 | Specify the "_Starts on_" day - the current day of the week then specify the "_Starts on_" time `00:00` | |
| 7 | Specify the "_Ends on_" day - the next day of the week then specify the "_Ends on_" time `23:59` | |
| 8 | Specify the _Nodes count_ - `2` | |
| 9 | Select the _Region_ - the same one as was saved at step 4 of the preparations | |
| 10 | Select `spot` type for the _Price type_ | |
| 11 | Select any _Instance type_ | |
| 12 | Specify the _Disk_ size between `100` and `150` (e.g. `120`) | |
| 13 | Click the "**+ Add docker image**" button | |
| 14 | In the appeared field, select any tool from the `Default registry` and `library` group (e.g. `ubuntu`) | |
| 15 | Select the _Condition_ - `Matches all filters ("and")` | |
| 16 | Click the "**+ Filter**" button | |
| 17 | Select the _Property_ in the appeared popup - `Run owner` | |
| 18 | Select the _Owner_ - the admin user name from the prerequisites | |
| 19 | Click the **CREATE** button | The new item appears in the pools list. This item contains: <ul><li> the name equals to the specified at step 5 <li> labels: `2 NODES`, `<instance_type>` equals to the selected at step 11, `SPOT`, `<disk_size> GB` equals to the disk size specified at step 12 <li> label with the tool name selected at step 14 label with the schedule in format: `<day_starts_on>, 00:00 - <day_ends_on>, 23:59` with the values of week days specified at steps 6-7 <li> buttons: **Edit** and **Remove** |
| 20 | Click the just-created node pool item | The nodes page appears that contains at least header with the pool name specified at step 5 |
| 21 | Click the **Refresh** button every minute until two nodes appear in the node list | <li> The header becomes `<pool_name> nodes (2 nodes, 2 nodes with associated RunId)` where `<pool_name>` is the pool name specified at step 5 <li> two nodes appear. Each appeared node has: <ul><li> the label with the pool name specified at step 5 <li> the label starts from `RUN ID P-` |
| 22 | Save node names | |
| 23 | Click the **CLUSTER** tab | The full node cluster appears. There are two nodes that have: <ul><li> the label with the pool name specified at step 5 <li> the label starts from `RUN ID P-` |
| 24 | Click the **HOT NODE POOLS** tab | At the panel of the pool created at step 19, there is the label `0/2` |
