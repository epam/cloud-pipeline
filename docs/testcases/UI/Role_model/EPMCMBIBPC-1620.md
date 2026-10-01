# [MANUAL] Check tool permissions for read-only user

Test verifies that a read-only user sees only the tool they were granted access to.

**Prerequisites**:
- The user does not own any tools, tool groups or registries
- The user, and the groups they belong to, have no rights on the registry, tool group, and tools
- The user has a role without any permissions on registries, groups, and tools

**Preparations**:
1. Login as admin
2. Click the **Tools** button at the navigation panel
3. Select the **Default** registry and the **library** tool group
4. Select the **endpoint-test** tool
5. Click the settings button (gear icon in the upper-right corner)
6. Open the **Permissions** tab
7. Click the add-user button
8. Enter the name of the user from the prerequisites
9. Add the user
10. Click on the user name
11. Check the **Allow read** checkbox
12. Close the pop-up
13. Log out

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Click the **Tools** button at the navigation panel | Only the **endpoint-test** tool is visible |
