# Check read permissions for bucket

Test verifies that a storage becomes visible in the library tree once read permission is granted.

**Prerequisites**:
- The user, and their group, have no permissions on the storages

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Click on the storage |  |
| 4 | Click the gear icon in the upper-right corner |  |
| 5 | Click the **Permissions** tab |  |
| 6 | Click the **Add user** button |  |
| 7 | In the pop-up that appears, enter an existing user name (from the prerequisites) |  |
| 8 | Click **OK** |  |
| 9 | Click on the user name that appears in the list on the **Permissions** tab |  |
| 10 | In the table that appears, set the checkbox in the **Allow** column opposite the **READ** permission |  |
| 11 | Log out |  |
| 12 | Login as the user specified at step 7 |  |
| 13 | Open the **Library** page | The storage is displayed in the library tree on the left panel |
