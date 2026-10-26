# [MANUAL] Check tool accessibility for user with read-only permissions for tools group and full-denied permissions for tool

Test verifies that a tool with fully-denied permissions is hidden even though the user has read-only access to the tool's group.

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
| 9 | Close the pop-up |  |
| 10 | Select a tool |  |
| 11 | Click the edit icon in the top-right corner (gear icon) |  |
| 12 | Click **Permissions** in the list that appears |  |
| 13 | Click the add-user icon |  |
| 14 | Add a user |  |
| 15 | Check all checkboxes in the **Deny** column |  |
| 16 | Log out |  |
| 17 | Login as the user whose permissions were edited |  |
| 18 | Click the **Tools** button |  |
| 19 | Select the group whose permissions were edited | The tool whose permissions were edited is not displayed |
