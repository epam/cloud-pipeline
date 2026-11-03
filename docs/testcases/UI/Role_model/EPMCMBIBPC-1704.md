# [MANUAL] Change detached configuration owner validation

Test verifies changing a detached configuration's owner via the Permissions tab.

**Prerequisites**:
- A user with permissions to create a pipeline, folder, storage and configuration (ROLE_PIPELINE_MANAGER, ROLE_FOLDER_MANAGER, ROLE_STORAGE_MANAGER, ROLE_CONFIGURATION_MANAGER)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1703](EPMCMBIBPC-1703.md) case |  |
| 2 | Login as the user from the prerequisites |  |
| 3 | Click the **CREATE** button |  |
| 4 | Click **Configuration** |  |
| 5 | Enter a configuration name |  |
| 6 | Click **Create** |  |
| 7 | Click the created configuration |  |
| 8 | Click the gear icon |  |
| 9 | Click the **Permissions** tab |  |
| 10 | Click on the user name | The **Owner** field becomes available for editing |
| 11 | Enter a new user name up to the "@" sign (e.g. `AUTO_EPM-CMBI_PIPELINEAWS`) | A drop-down list with the full user name appears |
| 12 | Click the user name in the drop-down list | The new user name is displayed in the **Owner** field |
| 13 | Click on the user name | The **Owner** field is no longer available for editing |
| 14 | Close the "Edit configuration info" pop-up |  |
| 15 | Refresh the page | The gear icon is not displayed |
