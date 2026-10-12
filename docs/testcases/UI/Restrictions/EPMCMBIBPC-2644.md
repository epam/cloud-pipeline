# Validation of instance types restrictions (system settings)

Test verifies that a global instance-types mask restricts the Node type dropdown for a pre-mask pipeline/configuration and a post-mask configuration alike, while retaining pre-mask values.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Navigate to the folder created at step 4 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case |  |
| 4 | Hover over **+ Create v**, click **Configuration** |  |
| 5 | In the pop-up that appears, specify a valid configuration name, click **CREATE** |  |
| 6 | Open the created configuration |  |
| 7 | Specify the pipeline created at step 12 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case into the **Pipeline** field |  |
| 8 | Set a valid value for the **Disk (Gb)** field |  |
| 9 | Click **Save** |  |
| 10 | Open the **Preferences** tab in system settings |  |
| 11 | Click the **Cluster** menu item on the left panel |  |
| 12 | Specify a mask value that does not comply with the node types entered at steps 15 and 21 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case into **cluster.allowed.instance.types** (e.g. `m5.*`) |  |
| 13 | Click **Save** |  |
| 14 | Log out |  |
| 15 | Login as the non-admin user from the prerequisites of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case |  |
| 16 | Open the **Library** page, navigate to the folder created at step 4 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case |  |
| 17 | Open the pipeline created at step 12 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case, click the pipeline version |  |
| 18 | Click the **CONFIGURATION** tab, click the **Exec environment** collapsed header | <li>In the **Node type** combobox, there is no selected value <li>The **Docker image** and **Disk (Gb)** fields are non-empty |
| 19 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 12 |
| 20 | Repeat step 16 |  |
| 21 | Open the configuration created at step 19 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case, expand the **Exec environment** section | The **Node type** combobox, **Docker image** and **Disk (Gb)** fields are non-empty |
| 22 | Click the **Node type** combobox | A drop-down list appears with all the values of node types |
| 23 | Repeat step 16 |  |
| 24 | Open the configuration created at step 5, expand the **Exec environment** section | <li>In the **Node type** combobox, there is no selected value <li>The **Docker image** and **Disk (Gb)** fields are non-empty |
| 25 | Click the **Node type** combobox | A drop-down list appears with only the values of node types that comply with the mask entered at step 12 |
