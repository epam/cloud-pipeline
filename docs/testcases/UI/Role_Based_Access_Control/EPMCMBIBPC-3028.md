# Associate a group to the user

Test verifies creating user groups and adding users to them.

**Prerequisites**:
- A valid non-admin user registered in the platform
- A valid admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites |  |
| 2 | Click the gear icon in the main menu on the left side of the page to open **Settings** |  |
| 3 | Click the **User management** tab, click the **Groups** subtab |  |
| 4 | Click the **+ Create group** button |  |
| 5 | In the pop-up that appears, enter a valid new group name into the **Group name** field (e.g. `test_group_1`) |  |
| 6 | Click **Create** | The group with the name specified at step 5 appears in the group list |
| 7 | Repeat steps 4-6 (e.g. for `test_group_2`) | The group with the name specified at step 7 appears in the group list |
| 8 | Find the group created at step 6 and click the edit button next to the group name |  |
| 9 | In the pop-up that appears, enter the non-admin user name from the prerequisites into the **Search user** field and select the item that appears |  |
| 10 | Click the **+ Add user** button next to the search field | The non-admin user from the prerequisites appears in the user list |
| 11 | Click **OK** |  |
| 12 | Find the group created at step 7 and click the edit button next to the group name |  |
| 13 | Repeat steps 9-10 |  |
| 14 | Repeat steps 9-10 for the admin user from the prerequisites | Both users from the prerequisites are displayed in the user list |
| 15 | Click **OK** |  |
| 16 | Click the **User** subtab |  |
| 17 | Find the admin user from the prerequisites in the user list | The group created at step 7 is displayed in the group list for the selected user |
| 18 | Find the non-admin user from the prerequisites in the user list | The groups created at steps 6, 7 are displayed in the group list for the selected user |

**After**:
- Open the **Groups** subtab in the **User management** tab and remove the groups created at steps 6, 7
