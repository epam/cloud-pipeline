# [MANUAL] Commit tool with allowed write permission to the tools group with denied write permission

Test verifies committing a tool whose group write permission is denied mid-run, both before and after the denial takes effect.

**Prerequisites**:
- The user, and the group they are granted permissions in, has write permission denied on the tools group
- The user, or the group they belong to, is granted all permissions on the tool in the group
- The user has no roles that grant permissions on tools, tool groups, and registries (ROLE_ADMIN, ROLE_USER)
- The user owns no tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Click the **Tools** button at the navigation panel |  |
| 3 | Select the tools group from the prerequisites |  |
| 4 | Select the tool from the prerequisites |  |
| 5 | Click the **Run** button and select **Custom settings** |  |
| 6 | Fill in parameters if necessary and enable the **Start idle** checkbox |  |
| 7 | Click **Launch** |  |
| 8 | Open the tool log page |  |
| 9 | Wait for the **COMMIT** button to appear |  |
| 10 | Click the **COMMIT** button |  |
| 11 | Select the tools group from the prerequisites |  |
| 12 | Try to enter a new unique name for the tool to commit | The **COMMIT** button is inactive |
| 13 | Try to enter a unique version name | The **COMMIT** button is inactive |
| 14 | Log out |  |
| 15 | Login as admin |  |
| 16 | Click the **Tools** button at the navigation panel |  |
| 17 | Select the tools group from the prerequisites |  |
| 18 | Click the gear icon in the upper-right corner |  |
| 19 | Click **Group** |  |
| 20 | Click **Edit** |  |
| 21 | Open the **Permissions** tab |  |
| 22 | Click the name of the user from the prerequisites |  |
| 23 | Check the **read** checkbox in the **deny** column |  |
| 24 | Log out |  |
| 25 | Login as the user from the prerequisites |  |
| 26 | Click the **Runs** button at the navigation panel |  |
| 27 | Click on the running tool from the prerequisites |  |
| 28 | Repeat steps 10-13 | The result is the same as for steps 12-13 (the **COMMIT** button is inactive) |
| 29 | Enter the name of the running tool |  |
| 30 | Click the **COMMIT** button |  |
| 31 | Click **OK** in the pop-up that appears |  |
| 32 | Click **COMMIT** | The **COMMIT** button immediately changes to unavailable and the commit is done successfully |
