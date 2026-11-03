# [MANUAL] Validate tool version's settings

Test verifies that settings saved for a specific tool version are used when launching that version with custom settings, independently of other versions.

**Prerequisites**:
- An existing tool with at least 3 versions

**Preparations**:
1. Login as admin
2. Open the **Tools** page
3. Select the registry, tools group and tool from the prerequisites
4. Delete the tool
5. Staying at the same registry and tools group, enable the just-deleted tool
6. Open the tool, click the **VERSIONS** tab
7. Click **VIEW UNSCANNED VERSIONS**
8. Scan all versions

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Tools** page |  |
| 2 | Select the registry and tools group, open the tool from step 3 of the Preparations |  |
| 3 | Click **SETTINGS** tab, expand **EXECUTION ENVIRONMENT** section | **Instance type**, **Disk (Gb)**, **Cmd template** fields are empty |
| 4 | Click **VERSIONS** tab |  |
| 5 | Click any not-**latest** tool version |  |
| 6 | Click **SETTINGS** tab | The **SETTINGS** tab contains: <li> **Execution defaults**, **System parameters**, **Custom parameters** sections <li> **SAVE** button (disabled) |
| 7 | Specify valid values for **Instance type**, **Disk (Gb)**, **Cmd template** |  |
| 8 | Click **SAVE** button |  |
| 9 | Click **return** button in front of the tool name |  |
| 10 | Hover over **v** button next to **Run** button in the right upper corner, click **Custom settings** in the appeared list | The launch page contains: <li> `Launch <tool_name> latest` <li> **Node type**, **Disk (Gb)**, **Cmd template** fields are empty |
| 11 | Repeat steps 1-2, 4 |  |
| 12 | At the row with the version selected at step 5, hover over **v** button next to **Run** button, click **Custom settings** in the appeared list |  |
| 13 | Expand **Exec environment** and **Advanced** sections | <li> **Node type** equal to **Instance type** specified at step 7 <li> **Disk (Gb)** equal to **Disk (Gb)** specified at step 7 <li> **Cmd template** equal to **Cmd template** specified at step 7 |
