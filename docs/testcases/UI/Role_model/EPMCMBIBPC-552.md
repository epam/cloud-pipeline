# Check folder read-only permissions and full deny permissions for sub-pipeline

Test verifies that a folder-level read permission is inherited, except for a sub-pipeline on which all permissions were explicitly denied.

**Prerequisites**:
- At least two users
- At least one user with the ROLE_ADMIN role
- An existing folder with two or more pipelines in it (the pipelines don't have special permissions set for users)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-12 of the [EPMCMBIBPC-549](EPMCMBIBPC-549.md) case |  |
| 2 | Click on any pipeline in the folder |  |
| 3 | Click on the pipeline version |  |
| 4 | Click the edit icon in the upper-right corner |  |
| 5 | In the pop-up that appears, click the **Permissions** tab |  |
| 6 | Click the **Add user** icon |  |
| 7 | In the pop-up that appears, enter the same user name as used at step 1 |  |
| 8 | Click **OK** |  |
| 9 | In the list that appears, click on the user name specified at step 7 |  |
| 10 | Set the checkboxes in the **Deny** column opposite all permissions |  |
| 11 | Close the pop-up |  |
| 12 | Log out |  |
| 13 | Login as the user specified at step 7 |  |
| 14 | Open the **Library** page |  |
| 15 | Click on the folder from step 1 | All pipelines and subfolders are displayed, excluding the pipeline for which permissions were set at step 10 |
