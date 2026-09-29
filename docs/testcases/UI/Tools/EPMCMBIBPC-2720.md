# [MANUAL] Validate hierarchy of tool settings

Test verifies that tool run settings follow the expected priority between a tool version's own settings and another version's settings when launching with custom settings.

**Preparations**:
1. Perform the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Tools** page |  |
| 2 | Select the registry and tools group, open the tool from the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case |  |
| 3 | Click the **VERSIONS** tab |  |
| 4 | Click the **latest** tool version |  |
| 5 | Click the **SETTINGS** tab |  |
| 6 | Specify valid values for the **Instance type**, **Disk (Gb)**, **Cmd template** fields, distinct from the ones specified at step 7 of the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case |  |
| 7 | Click the **SAVE** button |  |
| 8 | Click the **return** button in front of the tool name |  |
| 9 | Hover over the **v** button next to the **Run** button in the right upper corner, then click **Custom settings** in the appeared list |  |
| 10 | Expand the **Exec environment** and **Advanced** sections | The launch page contains: <li> `Launch <tool_name> latest` <li> **Node type** equal to the **Instance type** specified at step 6 <li> **Disk (Gb)** equal to the **Disk (Gb)** specified at step 6 <li> **Cmd template** equal to the **Cmd template** specified at step 6 |
| 11 | Repeat steps 1-2 |  |
| 12 | Click the **SETTINGS** tab |  |
| 13 | Expand the **EXECUTION ENVIRONMENT** section | <li> **Instance type** equal to the **Instance type** specified at step 6 <li> **Disk (Gb)** equal to the **Disk (Gb)** specified at step 6 <li> **Cmd template** equal to the **Cmd template** specified at step 6 |
| 14 | Repeat step 3 |  |
| 15 | At the row with the version selected at step 5 of the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case, hover over the **v** button next to the **Run** button, then click **Custom settings** in the appeared list |  |
| 16 | Repeat step 10 | The launch page contains: <li> `Launch <tool_name> latest` <li> **Node type** equal to the **Instance type** specified at step 7 of the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case <li> **Disk (Gb)** equal to the **Disk (Gb)** specified at step 7 of the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case <li> **Cmd template** equal to the **Cmd template** specified at step 7 of the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case |
| 17 | Repeat steps 1-3 |  |
| 18 | Click the not-**latest** version distinct from the one selected at step 5 of the [EPMCMBIBPC-2719](EPMCMBIBPC-2719.md) case |  |
| 19 | Click the **SETTINGS** tab | **Instance type**, **Disk (Gb)**, **Cmd template** fields are empty |
| 21 | Click the **return** button in front of the tool name |  |
| 22 | At the row with the version selected at step 18, hover over the **v** button next to the **Run** button, then click **Custom settings** in the appeared list |  |
| 23 | Repeat step 10 | The launch page contains: <li> `Launch <tool_name> latest` <li> **Node type** equal to the **Instance type** specified at step 6 <li> **Disk (Gb)** equal to the **Disk (Gb)** specified at step 6 <li> **Cmd template** equal to the **Cmd template** specified at step 6 |
