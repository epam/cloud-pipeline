# Validation of tools instance types restrictions (over user management)

Test verifies that a per-user tool-instance-types mask restricts the Node type dropdown for a tool run.

**Prerequisites**:
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Click the gear icon at the left menu to open system settings |  |
| 3 | Click the **User management** tab |  |
| 4 | Click the edit icon opposite the user name from the prerequisites |  |
| 5 | In the pop-up that appears, specify a mask value into **Allowed tool instance types mask** (e.g. `m5.*`) |  |
| 6 | Click **OK** |  |
| 7 | Log out |  |
| 8 | Login as the user from the prerequisites |  |
| 9 | Open the **Tools** page |  |
| 10 | Select a registry and group, click on the tool from the prerequisites |  |
| 11 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 12 | Expand the **Exec environment** section if it's minimized |  |
| 13 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 5 |
