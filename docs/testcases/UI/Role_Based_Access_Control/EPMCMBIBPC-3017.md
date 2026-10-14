# Provide admin rights to the user

Test verifies that granting the ROLE_ADMIN role reveals the admin-only settings tabs.

**Prerequisites**:
- A valid non-admin user registered in the platform
- A valid admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user from the prerequisites |  |
| 2 | Click the gear icon in the main menu on the left side of the page to open **Settings** | The **System events**, **User management**, **Email notifications**, **Preferences**, **Cloud Region** tabs are invisible |
| 3 | Log out |  |
| 4 | Login as the admin from the prerequisites |  |
| 5 | Repeat step 2 |  |
| 6 | Click the **User management** tab, click the **Users** subtab |  |
| 7 | Find the non-admin user from the prerequisites and click the edit button next to the username |  |
| 8 | Click the **Add role or group** drop-down list |  |
| 9 | Find the **ROLE_ADMIN** item in the list, select it |  |
| 10 | Click the **+ Add** button next to the drop-down list | The **ROLE_ADMIN** record appears in the roles/groups list |
| 11 | Click **OK** |  |
| 12 | Log out |  |
| 13 | Repeat steps 1-2 | The **System events**, **User management**, **Email notifications**, **Preferences**, **Cloud Region** tabs are visible |
