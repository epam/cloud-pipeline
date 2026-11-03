# [MANUAL] Check tools group page by user with denied permissions for registry and approved permissions for tools group

Test verifies that a user denied all permissions on a registry, but granted permissions on its tool group, sees that group's contents.

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
| 9 | Repeat steps 4-7 for the group |  |
| 10 | Allow the user read, write, and execute permissions |  |
| 11 | Log out |  |
| 12 | Login as the user whose permissions were edited |  |
| 13 | Click the **Tools** button | The contents of the group for which permissions were granted is displayed |
