# Validation of instance types restrictions (creating objects)

Test verifies that an instance-types mask restricts the Node type dropdown when a user creates a new pipeline and configuration.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Click the gear icon at the left menu to open system settings |  |
| 3 | Click the **User management** tab |  |
| 4 | Click the edit icon opposite the user name from the prerequisites of the EPMCMBIBPC-2637 case |  |
| 5 | In the pop-up that appears, specify a mask value that does not comply with the node types entered at steps 15 and 21 of the EPMCMBIBPC-2637 case into **Allowed instance types mask** (e.g. `m5.*`) |  |
| 6 | Click **OK** |  |
| 7 | Log out |  |
| 8 | Login as the non-admin user from the prerequisites of the EPMCMBIBPC-2637 case |  |
| 9 | Open the **Library** page, navigate to the folder created at step 4 of the EPMCMBIBPC-2637 case |  |
| 10 | Hover over **+ Create v**, click **Pipeline** |  |
| 11 | In the pop-up that appears, specify a valid pipeline name, click **CREATE** |  |
| 12 | Open the created pipeline, click on the pipeline version |  |
| 13 | Click the **CONFIGURATION** tab, click the **Exec environment** collapsed header |  |
| 14 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 5 |
| 15 | Repeat step 9 |  |
| 16 | Hover over **+ Create v**, click **Configuration** |  |
| 17 | In the pop-up that appears, specify a valid configuration name, click **CREATE** |  |
| 18 | Open the created configuration |  |
| 19 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 5 |
