# Check "Cluster nodes" by non-admin user

Test verifies that a stopped run's node no longer appears on the Cluster state page for either the run's owner or an admin.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Tools** page |  |
| 3 | Select a registry, group, and click on the tool |  |
| 4 | Grant the non-admin user all permissions (**READ**, **WRITE**, **EXECUTE**) on the opened tool |  |
| 5 | Log out |  |
| 6 | Login as the non-admin user from step 4 |  |
| 7 | Open the **Tools** page |  |
| 8 | Select a registry, group, and click on the tool from step 3 |  |
| 9 | Click the **Run** button |  |
| 10 | In the pop-up that appears, click **Launch** |  |
| 11 | Open the **Cluster state** page |  |
| 12 | Wait until the node for the run launched at step 10 appears in the table |  |
| 13 | Open the **Runs** page, stop the run launched at step 10 |  |
| 14 | Log out |  |
| 15 | Login as admin, repeat steps 7-11 |  |
| 16 | Wait until the node for the run launched at step 15 appears in the table |  |
| 17 | Open the **Runs** page, stop the run launched at step 15 |  |
| 18 | Repeat steps 5-6 |  |
| 19 | Open the **Cluster state** page | The cluster nodes table is empty |
