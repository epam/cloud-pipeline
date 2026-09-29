# [MANUAL] User/Group permission pop-up focus validation

Test verifies that the add-user/add-group pop-ups on the Permissions tab open with focus already on the account-name field, for every entity type.

**Prerequisites**:
- At least one pipeline, tool, tool group, registry, library folder, storage, and configuration is created
- A user with admin rights

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open a pipeline |  |
| 2 | Press the edit button (gear icon in the upper-right corner) |  |
| 3 | Click **Permissions** |  |
| 4 | Click the add-user button | The "Select user" pop-up appears with focus on the **Enter the account name** field (the cursor is already there, the field edges are highlighted blue) |
| 5 | Start entering a user name |  |
| 6 | Press **Enter** | The selected user name appears in the **Groups and users** list |
| 7 | Repeat steps 4-6 for the add-group button | <li>The "Select group" pop-up appears with focus on the **Enter the account name** field (the cursor is already there, the field edges are highlighted blue) <li>The selected group name appears in the **Groups and users** list |
| 8 | Repeat steps 1-7 for a tool, tool group, registry, library folder, storage, and configuration |  |
