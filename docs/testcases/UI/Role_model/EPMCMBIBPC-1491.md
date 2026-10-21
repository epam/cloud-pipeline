# [MANUAL] Commit tool with denied write permission to the tools group

Test verifies that committing a tool is denied while only read permission is granted on its group, and succeeds once write permission is also granted.

**Prerequisites**:
- The user, and the group they belong to, has all rights denied on the tools group
- The user, or the group they belong to, has read+execute permissions for one tool in the group above
- The user has no roles that grant permissions on tools, tool groups, and registries
- The user owns no tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Click the **Tools** button at the navigation panel |  |
| 3 | Select the tools group from the prerequisites |  |
| 4 | Select the tool from the prerequisites |  |
| 5 | Click the **Run** button |  |
| 6 | Click **Custom settings** |  |
| 7 | Fill in the necessary fields |  |
| 8 | Open the tool log page |  |
| 9 | Wait for the **COMMIT** button to appear and click it | The message "You don't have permission to write in registries" is displayed instead of the address field on the pop-up |
| 10 | Log out |  |
| 11 | Login as admin |  |
| 12 | Click the **Tools** button at the navigation panel |  |
| 13 | Select the tools group from the prerequisites |  |
| 14 | Click the gear icon in the upper-right corner |  |
| 15 | Click **Group** |  |
| 16 | Click **Edit** |  |
| 17 | Open the **Permissions** tab |  |
| 18 | Click the name of the user from the prerequisites |  |
| 19 | Check the **read** checkbox in the **allow** column |  |
| 20 | Log out |  |
| 21 | Login as the user from the prerequisites |  |
| 22 | Click the **Runs** button at the navigation panel |  |
| 23 | Click on the running tool from the prerequisites |  |
| 24 | Click the **COMMIT** button | The message "You don't have permission to write in registries" is displayed instead of the address field on the pop-up |
| 25 | Repeat steps 10-24, checking the **write** checkbox in the **allow** column |  |
| 26 | Enter the name of the running tool in the address field |  |
| 27 | Click the **COMMIT** button |  |
| 28 | Click **OK** | The message "Access is denied" appears |
| 29 | Enter a unique version name |  |
| 30 | Click **COMMIT** | The message "Access is denied" appears |
| 31 | Enter a unique name for the tool |  |
| 32 | Click **COMMIT** | The **COMMIT** button immediately changes to unavailable and the commit is done successfully |
| 33 | Repeat steps 2-3 | The committed tool is in the group |
