# Permissions form validation

Test verifies that a user added on a pipeline's Permissions tab is displayed in the user list.

**Prerequisites**:
- At least two users
- At least one user with the ROLE_ADMIN role
- At least one existing pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Library** page |  |
| 3 | Click on any existing pipeline |  |
| 4 | Click the gear icon in the upper-right corner |  |
| 5 | Click the **Permissions** tab |  |
| 6 | Click the **Add user** icon |  |
| 7 | In the pop-up that appears, enter an existing user name |  |
| 8 | Click **OK** | The user name entered at step 7, with a delete icon, is displayed in the list in the pop-up opened at step 4 |
