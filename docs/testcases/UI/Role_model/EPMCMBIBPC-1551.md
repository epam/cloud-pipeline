# [MANUAL] Check tool accessibility for user without permissions for registry and group

Test verifies that a user denied all permissions on a registry and group, but granted permissions on one tool in it, sees only that tool.

**Prerequisites**:
- The user, and the group they belong to, must have no permissions on any Docker registries, their groups, or the tools in them

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the administrator |  |
| 2 | Navigate to the **Tools** page |  |
| 3 | Select a registry and a group |  |
| 4 | Hover over the **Settings** button |  |
| 5 | Click **Registry** |  |
| 6 | Click **Edit** |  |
| 7 | Add a user |  |
| 8 | Deny the user read, write, and execute permissions |  |
| 9 | Repeat steps 4-8 for the group |  |
| 10 | Select a tool |  |
| 11 | Allow the user read, write, and execute permissions for that tool |  |
| 12 | Log out |  |
| 13 | Login as the user whose permissions were edited |  |
| 14 | Click the **Tools** button | Only the tool for which permissions were granted is shown in the list |
