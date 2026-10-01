# Validation of instance types restrictions (hierarchy)

Test verifies that a per-user instance-types mask takes precedence over the group mask and the global cluster mask, and that a separate tool-instance-types mask applies to tool runs.

**Prerequisites**:
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool
- The [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case is performed

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Preferences** tab in system settings |  |
| 3 | Click the **Cluster** menu item on the left panel |  |
| 4 | Specify a mask value into **cluster.allowed.instance.types** (e.g. `m5.*`) |  |
| 5 | Click **Save** |  |
| 6 | Open the **User management** tab in system settings |  |
| 7 | Click the **Roles** tab, click the edit icon opposite **ROLE_USER** |  |
| 8 | In the pop-up that appears, specify a mask value into **Allowed instance types mask** that differs from the one specified at step 4 (e.g. `c5.*`) |  |
| 9 | Click **OK** |  |
| 10 | Click the **Users** tab |  |
| 11 | Click the edit icon opposite the user name from the prerequisites |  |
| 12 | In the pop-up that appears, specify a mask value into **Allowed instance types mask** that differs from the ones specified at steps 4, 8 (e.g. `r5.*, p2.*`) |  |
| 13 | Specify a mask value into **Allowed tool instance types mask** that differs from the ones specified at steps 4, 8, 12 (e.g. `c5.*, m5.*`) |  |
| 14 | Click **OK** |  |
| 15 | Log out |  |
| 16 | Login as the user from the prerequisites |  |
| 17 | Open the **Library** page, navigate to the folder created at step 4 of the EPMCMBIBPC-2637 case |  |
| 18 | Open the pipeline created at step 12 of the EPMCMBIBPC-2637 case, click the pipeline version |  |
| 19 | Click the **CONFIGURATION** tab, click the **Exec environment** collapsed header |  |
| 20 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 12 |
| 21 | Repeat step 17 |  |
| 22 | Open the configuration created at step 19 of the EPMCMBIBPC-2637 case, expand the **Exec environment** section |  |
| 23 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 13 |
| 24 | Open the **Tools** page |  |
| 25 | Select a registry and group, click on the tool from the prerequisites |  |
| 26 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 27 | Expand the **Exec environment** section if it's minimized |  |
| 28 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 13 |
