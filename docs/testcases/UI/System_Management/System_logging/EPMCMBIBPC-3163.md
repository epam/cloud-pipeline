# User authentication. Unsuccessful attempt

Test verifies that a blocked user's failed login attempt is recorded in the system logs.

**Prerequisites**:
- At least two users
- One of the users must have the ROLE_ADMIN role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites |  |
| 2 | Click the gear icon (**System settings**) in the main menu |  |
| 3 | Click the **USER MANAGEMENT** tab |  |
| 4 | Find the non-admin user from the prerequisites in the user list |  |
| 5 | Click the **Edit** button in the row next to the found non-admin user |  |
| 6 | Click **BLOCK** |  |
| 7 | Click **OK** in the pop-up |  |
| 8 | Click **OK** |  |
| 9 | Log out |  |
| 10 | Try to log in to the platform as the non-admin user from step 5 | The authorization/blocking error page is displayed |
| 11 | Login as the admin user from the prerequisites |  |
| 12 | Click the gear icon (**System settings**) in the main menu |  |
| 13 | Click the **SYSTEM LOGS** tab |  |
| 14 | Enter the non-admin user name from step 5 into the **User** field | In the logs table, there is a row with log message "Authentication failed! User <username> is blocked!", user "<username>", type "security", where <username> is the name of the user from step 5 |

**After**:
- Unblock the non-admin user that was blocked
