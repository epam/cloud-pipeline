# Validation of tools instance types restrictions (over instance management)

Test verifies that a per-tool instance-types mask set via Instance management restricts the Node type dropdown for both admin and non-admin users.

**Prerequisites**:
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Tools** page |  |
| 3 | Select a registry and group, click on the tool from the prerequisites |  |
| 4 | Hover over the icon to the left of the gear icon in the upper-right corner |  |
| 5 | Click **Instance management** in the list that appears |  |
| 6 | In the panel that appears, specify a mask value into **Allowed tool instance types mask** (e.g. `m5.*`) |  |
| 7 | Click **APPLY** |  |
| 8 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 9 | Expand the **Exec environment** section if it's minimized |  |
| 10 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 6 |
| 11 | Log out |  |
| 12 | Login as the user from the prerequisites |  |
| 13 | Repeat steps 2-3 |  |
| 14 | Repeat steps 8-10 | A drop-down list appears with only the values of node types that comply with the mask entered at step 6 |

**After**:
- Clear the **Allowed tool instance types mask** field that was changed at step 6
