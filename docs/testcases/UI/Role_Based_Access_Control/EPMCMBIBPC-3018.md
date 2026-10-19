# Remove admin rights from the user

Test verifies that removing the ROLE_ADMIN role hides the admin-only settings tabs.

**Preparations**:
1. Perform the [EPMCMBIBPC-3017](EPMCMBIBPC-3017.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites of the [EPMCMBIBPC-3017](EPMCMBIBPC-3017.md) case |  |
| 2 | Click the gear icon in the main menu on the left side of the page to open **Settings** |  |
| 3 | Click the **User management** tab, click the **Users** subtab |  |
| 4 | Find the non-admin user from the prerequisites of the [EPMCMBIBPC-3017](EPMCMBIBPC-3017.md) case and click the edit button next to the username |  |
| 5 | Click the **Remove** icon next to the **ROLE_ADMIN** record in the roles/groups list |  |
| 6 | Click **OK** |  |
| 7 | Log out |  |
| 8 | Login as the non-admin user from the prerequisites of the [EPMCMBIBPC-3017](EPMCMBIBPC-3017.md) case |  |
| 9 | Repeat step 2 | The **System events**, **User management**, **Email notifications**, **Preferences**, **Cloud Region** tabs are invisible |
