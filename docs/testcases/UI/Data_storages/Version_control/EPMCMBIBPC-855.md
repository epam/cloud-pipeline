# Edit permission test for bucket that have enabled versioning

Test verifies that a user denied WRITE and EXECUTE permissions on a versioned bucket has no edit affordances on it or its folder.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new object storage** |  |
| 4 | Specify storage path |  |
| 5 | Set the **Enable versioning** checkbox if it's unset |  |
| 6 | Click **Create** button |  |
| 7 | Open the created storage |  |
| 8 | Hover over **+ Create v** button |  |
| 9 | Select **Folder** item |  |
| 10 | Specify a valid folder name in the appeared pop-up window |  |
| 11 | Click **OK** button |  |
| 12 | Click on the gear icon at the top of the storage page |  |
| 13 | Click on **Permissions** in the appeared pop-up window |  |
| 14 | Click **Add user** button, input a user name in the appeared pop-up window, confirm by clicking **OK** |  |
| 15 | Click on the appeared row with the user name from step 14 |  |
| 16 | Set permissions for the selected user: READ - allow, WRITE - deny, EXECUTE - deny |  |
| 17 | Logout |  |
| 18 | Login as the user from step 14 |  |
| 19 | Open library | The edit icon for the storage created at step 6 doesn't display |
| 20 | Open the storage created at step 6 | <li> the folder created at step 11 is displayed <li> the **Upload** button doesn't display <li> the delete icon and edit icon for the folder created at step 11 don't display |
