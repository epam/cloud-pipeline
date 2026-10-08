# [MANUAL] Check folder read-only permissions and full deny permissions for sub-storage

Test verifies that a folder-level read permission is inherited, except for a sub-storage on which all permissions were explicitly denied.

**Prerequisites**:
- The ability to log out
- Several users
- A user with administrator rights
- A folder containing several storages

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the administrator |  |
| 2 | Click the folder |  |
| 3 | On the opened page, click the edit icon in the top-right corner |  |
| 4 | Click the **Permissions** tab |  |
| 5 | Click the add-user icon |  |
| 6 | Enter an existing user name in the form that appears |  |
| 7 | Click **OK** |  |
| 8 | Click the checkbox in the **allow** column opposite the **read** privilege |  |
| 9 | Close the form |  |
| 10 | Click the storage in the folder |  |
| 11 | Click the edit icon in the page header |  |
| 12 | Click the add-user icon |  |
| 13 | Specify the user name used in the previous steps and click **OK** |  |
| 14 | Click on the added user |  |
| 15 | Enable all checkboxes in the **deny** column and close the form |  |
| 16 | Log out |  |
| 17 | Login as the user whose permissions were edited |  |
| 18 | Click the folder for which the permissions were edited | All the contents of the folder for which access permissions were edited at step 8 are displayed, except for the storage whose access permissions were edited at step 15 |
