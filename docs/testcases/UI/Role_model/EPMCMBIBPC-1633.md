# [MANUAL] Check tool permissions by user with read-only permissions for tools group

Test verifies the tool UI for a user with read-only permissions on the tool's group.

**Prerequisites**:
- The user, and the group they belong to, must have no permissions on any Docker registries, their groups, or the tools in them

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the administrator |  |
| 2 | Navigate to the **Tools** page |  |
| 3 | Select a registry and a group |  |
| 4 | Hover over the **Settings** button |  |
| 5 | Click **Group** |  |
| 6 | Click **Edit** |  |
| 7 | Add a user |  |
| 8 | Grant the user read permission |  |
| 9 | Log out |  |
| 10 | Login as the user whose permissions were edited |  |
| 11 | Click the **Tools** button |  |
| 12 | Select the group whose permissions were edited |  |
| 13 | Select the tool |  |
| 14 | Open the **DESCRIPTION** tab | The buttons **EDIT**, **Run**, and settings (gear icon in the upper-right corner) are absent |
| 15 | Open the **VERSIONS** tab | The buttons **Run**, delete, and settings are absent |
| 16 | Open the **SETTINGS** tab | <li>The **Run** button is absent <li>The input fields are inactive |
