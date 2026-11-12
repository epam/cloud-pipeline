# A run with the `CP_CREATE_NEW_NODE` parameter set to `true` gets a new node, not a free one

Test verifies that:
- a run with the `CP_CREATE_NEW_NODE` String parameter set to `true` gets a new node, while a free node of an earlier run with the same settings exists
- a run without this parameter gets that free node

**Prerequisites**:
- User with the permission to launch a test tool
- Test tool, e.g. `library/centos`
- The cluster has not reached the `cluster.max.size` size, and has no free node of the instance type that the test tool runs on by default

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select the test tool | |
| 4 | Launch a tool with default settings | |
| 5 | At the **Runs** page, click the just-launched run (run 1) | |
| 6 | Wait until the SSH hyperlink appears | |
| 7 | Save the node name near the **IP** label | |
| 8 | Click the **STOP** hyperlink in the right upper corner of the page, confirm the stop | Run 1 gets the **STOPPED** status |
| 9 | Open the **Tools** page, select the test tool | |
| 10 | Hover over the **v** button next to **Run**, click **Custom settings** in the list that appears | |
| 11 | On the **Launch** page, click the **Add parameter** button | **Name** and **value** fields for the added parameter appear |
| 12 | Input `CP_CREATE_NEW_NODE` into the name field, and `true` into the value field | |
| 13 | Click **Launch**, then click **Launch** in the pop-up that appears | |
| 14 | At the **Runs** page, click the just-launched run (run 2), expand the **Parameters** section | The section has the `CP_CREATE_NEW_NODE` parameter with the `true` value |
| 15 | Wait until the SSH hyperlink appears | The node name near the **IP** label is not the one saved at step 7 |
| 16 | Click the ***InitializeNode*** task | The log shows that a new instance was started for the run, e.g. `Instance created. ID: <...>, IP: <...>` on AWS |
| 17 | Repeat steps 2-6 (run 3) | The node name near the **IP** label of run 3 is the one saved at step 7 |

**After**:
- Stop runs 2 and 3
