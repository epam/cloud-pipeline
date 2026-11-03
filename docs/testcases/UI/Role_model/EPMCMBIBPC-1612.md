# [MANUAL] Tool group manager role validation

Test verifies the tool-group management UI available before and after granting a user the ROLE_TOOL_GROUP_MANAGER role.

**Prerequisites**:
- The user, or the group they belong to, has read and write rights on the Default registry
- The user does not have the ROLE_TOOL_GROUP_MANAGER role

**Preparations**:
1. Login as the user from the prerequisites
2. Click the **Tools** button on the navigation panel
3. Click the settings button (gear icon in the upper-right corner) -- a drop-down list appears: Registry, Group, + Enable tool
4. Hover over **Group** -- a drop-down list appears with **Edit** and **Create personal** items
5. Log out
6. Login as admin
7. Press the settings button at the navigation panel
8. Open the **User management** tab
9. Find the user from the prerequisites
10. Press the edit button opposite the user name
11. Add ROLE_TOOL_GROUP_MANAGER
12. Press **OK**
13. Log out

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat preparation steps 1-4 | A drop-down list appears: **+ Create personal**, **+ Create**, **Edit**, **Delete** |
