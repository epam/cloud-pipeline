# [MANUAL] User own folder validation

Test verifies the UI available to a user for their own folder, with no other permissions in the pipeline library.

**Prerequisites**:
- The user has their own directory, storage, pipeline, and configuration in the pipeline library
- The user, and the group they belong to, have no permissions in the pipeline library
- The user has no roles that grant permissions on other Cloud Pipeline objects

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Navigate to the folder from the prerequisites | The **Display attributes** and settings buttons are visible |
| 3 | Click the settings button (gear icon in the upper-right corner) |  |
| 4 | Open the **Permissions** tab |  |
| 5 | Click the add-user button | The "Select user" pop-up appears |
| 6 | Click **Cancel** |  |
| 7 | Click the add-group button | The "Select group" pop-up appears |
| 8 | Click **Cancel** |  |
