# Check anonymous access

Test verifies that an unregistered but IdP-authenticated user is denied access to a shared endpoint until the ROLE_ANONYMOUS_USER role is granted.

**Prerequisites**:
- An admin user
- A non-admin user that can pass the IdP authentication but is not registered in the platform

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin from the prerequisites |  |
| 2 | Open the **Tools** page |  |
| 3 | Find the tool with the interactive endpoint |  |
| 4 | Launch the tool |  |
| 5 | Open the **Run logs** page |  |
| 6 | Wait until the **Endpoint** hyperlink appears |  |
| 7 | Save the **Endpoint** hyperlink that appears |  |
| 8 | Log out |  |
| 9 | Open a new tab, enter the link saved at step 7 |  |
| 10 | Try to log in as the non-admin user from the prerequisites | The authorization error appears |
| 11 | Login as the admin from the prerequisites |  |
| 12 | Open the running tool launched at step 4 |  |
| 13 | Click the "Not shared (click to configure)" hyperlink |  |
| 14 | In the **share** pop-up, select the ROLE_ANONYMOUS_USER role to share |  |
| 15 | Click **OK** | Near the **Share with:** label, the link "role_anonymous_user" appears |
| 16 | Repeat steps 8-10 | The interactive service opens |
