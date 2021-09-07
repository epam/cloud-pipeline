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
| 4 | Click on **Edit** icon opposite the non-admin user name from the prerequisites | |
| 5 | In an appeared pop-up click on the **Impersonate** button | <li> Page reloads and the **Dashboard** page opens <li> **Stop Impersonation** button appears on the left navigation panel |
| 6 | Open the **Settings** page | |
| 7 | Open the **My Profile** tab | Profile of non-admin user from the prerequisites is shown on the page |
| 8 | Click **Stop Impersonation** button | <li> Page reloads and the **Dashboard** page opens <li> **Stop Impersonation** button disappears from the left navigation panel |
| 9 | Repeat steps 6 - 7 | Profile of admin user from the prerequisites is shown on the page |