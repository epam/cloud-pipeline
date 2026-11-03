# Validation of tools instance types restrictions (system settings)

Test verifies that a global tool-instance-types mask restricts the Node type dropdown for a custom-settings tool run.

**Prerequisites**:
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Preferences** tab in system settings |  |
| 3 | Click the **Cluster** menu item on the left panel |  |
| 4 | Specify a mask value into **cluster.allowed.instance.types.docker** (e.g. `m5.*`) |  |
| 5 | Click **Save** |  |
| 6 | Log out |  |
| 7 | Login as the user from the prerequisites |  |
| 8 | Open the **Tools** page |  |
| 9 | Select a registry and group, click on the tool from the prerequisites |  |
| 10 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 11 | Expand the **Exec environment** section if it's minimized |  |
| 12 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 4 |

**After**:
- Restore the previous value of the **cluster.allowed.instance.types.docker** field, as it was before step 4
