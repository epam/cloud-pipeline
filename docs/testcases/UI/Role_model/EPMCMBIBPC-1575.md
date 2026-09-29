# Search validation within "edit user" dialog

Test verifies that typing a newly-created group's name in the "Add role or group" input of the edit-user dialog surfaces it in the drop-down list.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open system settings |  |
| 2 | Open the **User management** menu |  |
| 3 | Open the **Groups** tab |  |
| 4 | Create a new group |  |
| 5 | Open the **Users** tab |  |
| 6 | Click the **Edit** icon in the row opposite an existing user name |  |
| 7 | Start typing the group name created at step 4 in the input labeled **Add role or group** | The group created at step 4 appears in the drop-down list |
