# [MANUAL] Change pipeline owner validation

Test verifies changing a pipeline's owner via the Permissions tab.

**Prerequisites**:
- A user with permissions to create a pipeline, folder, storage and configuration (ROLE_PIPELINE_MANAGER, ROLE_FOLDER_MANAGER, ROLE_STORAGE_MANAGER, ROLE_CONFIGURATION_MANAGER)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Click the **CREATE** button |  |
| 3 | Click **Pipeline** |  |
| 4 | Click **Default** |  |
| 5 | Enter a unique pipeline name and click **Create** |  |
| 6 | Click the created pipeline |  |
| 7 | Click the gear icon |  |
| 8 | Click the **Permissions** tab |  |
| 9 | Click on the user name | The **Owner** field becomes available for editing |
| 10 | Enter a new user name up to the "@" sign (e.g. `AUTO_EPM-CMBI_PIPELINEAWS`) | A drop-down list with the full user name appears |
| 11 | Click the user name in the drop-down list | The new user name is displayed in the **Owner** field |
| 12 | Click on the user name | The **Owner** field is no longer available for editing |
| 13 | Close the "Edit pipeline info" pop-up |  |
| 14 | Refresh the page | The gear icon is not displayed |
