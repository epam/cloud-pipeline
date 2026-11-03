# [MANUAL] Change data storage owner validation

Test verifies changing a data storage's owner via the Permissions tab.

**Prerequisites**:
- A user with permissions to create a pipeline, folder, storage and configuration (ROLE_PIPELINE_MANAGER, ROLE_FOLDER_MANAGER, ROLE_STORAGE_MANAGER, ROLE_CONFIGURATION_MANAGER)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1702](EPMCMBIBPC-1702.md) case |  |
| 2 | Login as the user from the prerequisites |  |
| 3 | Click the **CREATE** button |  |
| 4 | Click **Storage** |  |
| 5 | Click **Create new storage** |  |
| 6 | Enter a unique storage path |  |
| 7 | Enter valid values into the **STS duration**, **LTS duration**, **Backup duration** fields |  |
| 8 | Click **Create** |  |
| 9 | Click the created storage |  |
| 10 | Click the gear icon |  |
| 11 | Click the **Permissions** tab |  |
| 12 | Click on the user name | The **Owner** field becomes available for editing |
| 13 | Enter a new user name up to the "@" sign (e.g. `AUTO_EPM-CMBI_PIPELINEAWS`) | A drop-down list with the full user name appears |
| 14 | Click the user name in the drop-down list | The new user name is displayed in the **Owner** field |
| 15 | Click on the user name | The **Owner** field is no longer available for editing |
| 16 | Close the "Edit storage" pop-up |  |
| 17 | Refresh the page | The gear icon is not displayed |
