# [MANUAL] Change folder owner validation

Test verifies changing a folder's owner via the Permissions tab.

**Prerequisites**:
- A user with permissions to create a pipeline, folder, storage and configuration (ROLE_PIPELINE_MANAGER, ROLE_FOLDER_MANAGER, ROLE_STORAGE_MANAGER, ROLE_CONFIGURATION_MANAGER)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1701](EPMCMBIBPC-1701.md) case |  |
| 2 | Login as the user from the prerequisites |  |
| 3 | Click the **CREATE** button |  |
| 4 | Click **Folder** |  |
| 5 | Enter a folder name and click **Create** |  |
| 6 | Click the created folder |  |
| 7 | Click the gear icon |  |
| 8 | Click the **Permissions** tab |  |
| 9 | Click on the user name | The **Owner** field becomes available for editing |
| 10 | Enter a new user name up to the "@" sign (e.g. `AUTO_EPM-CMBI_PIPELINEAWS`) | A drop-down list with the full user name appears |
| 11 | Click the user name in the drop-down list | The new user name is displayed in the **Owner** field |
| 12 | Click on the user name | The **Owner** field is no longer available for editing |
| 13 | Close the "Rename folder" pop-up |  |
| 14 | Refresh the page | The gear icon is not displayed |
