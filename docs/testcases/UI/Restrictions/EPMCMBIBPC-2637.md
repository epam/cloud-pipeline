# Preparation for validation of instance types restrictions

Test creates a folder, pipeline and configuration with node types set, as a shared setup step for the instance/price type restriction cases.

**Prerequisites**:
- An existing non-admin user with the ROLE_USER, ROLE_PIPELINE_MANAGER and ROLE_CONFIGURATION_MANAGER roles

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Hover over **+ Create v**, click **Folder** |  |
| 4 | In the pop-up that appears, specify a valid folder name, click **OK** |  |
| 5 | Open the created folder |  |
| 6 | Hover over the gear icon in the upper-right corner, click **Edit folder** in the list that appears |  |
| 7 | Click the **Permissions** tab in the pop-up that appears |  |
| 8 | Click the **Add user** icon |  |
| 9 | In the pop-up that appears, specify the name of the user from the prerequisites, click **OK** |  |
| 10 | Click on the user name that appears, set the **Allow** checkboxes for all permissions, close the pop-up |  |
| 11 | Hover over **+ Create v**, click **Pipeline** |  |
| 12 | In the pop-up that appears, specify a valid pipeline name, click **CREATE** |  |
| 13 | Open the created pipeline, click on the pipeline version |  |
| 14 | Click the **CONFIGURATION** tab, click the **Exec environment** collapsed header |  |
| 15 | In the **Node type** combobox, select any value (e.g. `c5.large`) |  |
| 16 | Click **Save** | The value selected at step 15 is displayed in the **Node type** combobox |
| 17 | Navigate to the folder created at step 4 |  |
| 18 | Hover over **+ Create v**, click **Configuration** |  |
| 19 | In the pop-up that appears, specify a valid configuration name, click **CREATE** |  |
| 20 | Open the created configuration |  |
| 21 | In the **Node type** combobox, select any value (e.g. `c5.xlarge`) |  |
| 22 | Set valid values for the **Docker image**, **Disk (Gb)** fields |  |
| 23 | Click **Save** | The value selected at step 21 is displayed in the **Node type** combobox |
| 24 | Log out |  |
