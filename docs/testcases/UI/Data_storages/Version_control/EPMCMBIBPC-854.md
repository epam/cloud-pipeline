# Read permission test for bucket that have enabled versioning

Test verifies that a user denied READ, WRITE and EXECUTE permissions on a versioned bucket doesn't see it in the library.

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
| 7 | Click on the edit icon opposite the created storage name |  |
| 8 | Click on **Permissions** in the appeared pop-up window |  |
| 9 | Click **Add user** button, input a user name in the appeared pop-up window, confirm by clicking **OK** |  |
| 10 | Click on the appeared row with the user name from step 9 |  |
| 11 | Set permissions for the selected user: READ - deny, WRITE - deny, EXECUTE - deny |  |
| 12 | Logout |  |
| 13 | Login as the user from step 9 |  |
| 14 | Open library | The storage created at step 6 doesn't display |
