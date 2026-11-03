# Add the user

Test verifies creating a user and its default role assignment.

**Prerequisites**:
- Disable the system preference `storage.user.home.auto` if it is set

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user |  |
| 2 | Click the gear icon in the main menu on the left side of the page to open **Settings** |  |
| 3 | Click the **User management** tab, click the **Users** subtab |  |
| 4 | Click the **+ Create user** button |  |
| 5 | In the pop-up that appears, enter a valid new user name into the **Name** field (e.g. `test_user`) |  |
| 6 | Click **Create** |  |
| 7 | Click the **Home** button in the main menu |  |
| 8 | Repeat steps 2-3 |  |
| 9 | Enter into the search field the user name specified at step 5, press **Enter** | <li>The record with the name specified at step 5 appears <li>The record includes at least the following labels: "ROLE_USER", "ROLE_PIPELINE_MANAGER", "ROLE_FOLDER_MANAGER", "ROLE_CONFIGURATION_MANAGER" |

**After**:
- Restore the previous state of the system preference `storage.user.home.auto`
