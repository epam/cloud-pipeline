# Allow to "impersonate" administrator as a general user

Test verifies that admin can switch to general user mode and revert back.

**Prerequisites**:

- Admin user
- Non-admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Open the **User management** tab | |
| 4 | Click on **Edit** icon opposite the user name from the prerequisites | |
| 4 | In an appeared pop-up click on the **Impersonate** button | <li> Page reloads and the **Dashboard** page opens <li> **Stop Impersonation** button appears on the left navigation panel |
| 5 | Open the **Library** page | |
| 6 | Hover over **+ Create v** button, click on "Folder" item | |
| 7 | In an appeared pop-up specify a valid folder name. Click **OK** button | |
| 8 | Open created folder | Non-admin user name from the prerequisites is shown near the folder header |
| 9 | Click **Stop Impersonation** button | <li> Page reloads and the **Dashboard** page opens <li> **Stop Impersonation** button disappears from the left navigation panel |
| 10 | Repeat steps 5 - 8 | Admin user name from the prerequisites is shown near the folder header |