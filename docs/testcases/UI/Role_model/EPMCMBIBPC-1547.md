# [MANUAL] Check tool group by user with read-only permissions for registry

Test verifies that a user with read-only access to a registry sees only the library group and no personal group.

**Prerequisites**:
- The user, and the group they belong to, must have no permissions on any Docker registries, their groups, or the tools in them

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the administrator |  |
| 2 | Navigate to the **Tools** page |  |
| 3 | Select the **Default** registry |  |
| 4 | Hover over the **Settings** button |  |
| 5 | Click **Registry** |  |
| 6 | Click **Edit** |  |
| 7 | Add a user |  |
| 8 | Allow the user read permission |  |
| 9 | Log out |  |
| 10 | Login as the user whose permissions were edited |  |
| 11 | Click the **Tools** button | <li>No personal group, nor a suggestion to create one, is displayed <li>The **library** group is displayed |
