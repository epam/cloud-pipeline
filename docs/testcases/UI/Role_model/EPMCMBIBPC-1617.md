# [MANUAL] User own tool in public group validation

Test verifies the UI available to a user for their own tool in a public tool group, with no other registry/group/tool permissions.

**Prerequisites**:
- The user has their own tool in the Default registry -> library tool group
- The user, and the group they belong to, have no permissions on registries, tool groups, or other tools
- The user has no personal group
- The user has no roles that grant any permissions on registries, tool groups, or other tools

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Click the **Tools** button at the navigation panel |  |
| 3 | Click on the tool from the prerequisites | <li>The **EDIT** button is located opposite **Short description** <li>The **EDIT** button is located opposite **Full description** <li>The **Show attributes**, settings, and **Run** buttons are located in the upper-right corner |
| 4 | Open the **VERSIONS** tab | The **Run** and delete buttons are located opposite every version in the list |
| 5 | Open the **SETTINGS** tab |  |
| 6 | Click the **Instance type** field |  |
| 7 | Select an instance type |  |
| 8 | Click the **Save** button | The changes are saved |
| 9 | Click the settings button (gear icon in the upper-right corner) |  |
| 10 | Open the **Permissions** tab |  |
| 11 | Click the add-user button | The "Select user" pop-up appears |
| 12 | Click **Cancel** |  |
| 13 | Click the add-group button | The "Select group" pop-up appears |
| 14 | Click **Cancel** |  |
