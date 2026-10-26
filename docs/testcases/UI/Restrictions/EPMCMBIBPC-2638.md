# Validation of instance types restrictions (existing objects)

Test verifies that an instance-types mask restricts the Node type dropdown for a user's existing pipeline and configuration.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Click the gear icon at the left menu to open system settings |  |
| 3 | Click the **User management** tab |  |
| 4 | Click the edit icon opposite the user name from the prerequisites |  |
| 5 | In the pop-up that appears, specify a mask value that does not comply with the node types entered at steps 15 and 21 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case into **Allowed instance types mask** (e.g. `m5.*`) |  |
| 6 | Click **OK** |  |
| 7 | Log out |  |
| 8 | Login as the non-admin user from the prerequisites of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case |  |
| 9 | Open the **Library** page, navigate to the folder created at step 4 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case |  |
| 10 | Open the pipeline created at step 12 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case, click the pipeline version |  |
| 11 | Click the **CONFIGURATION** tab, click the **Exec environment** collapsed header | In the **Node type** combobox, there is no selected value |
| 12 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 5 |
| 13 | Repeat step 9 |  |
| 14 | Open the configuration created at step 19 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case, expand the **Exec environment** section | <li>In the **Node type** combobox, there is no selected value <li>The **Docker image** and **Disk (Gb)** fields are non-empty |
| 15 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 5 |
