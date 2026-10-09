# Create/delete user

Test verifies that creating and deleting a user is recorded in the system logs.

**Prerequisites**:
- Login as the admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the gear icon (**System settings**) in the main menu |  |
| 2 | Click the **USER MANAGEMENT** tab |  |
| 3 | Click the **+Create user** button |  |
| 4 | In the pop-up that appears, enter a valid user name into the **Name** field |  |
| 5 | Click **Create** |  |
| 6 | Find the just created user in the user list |  |
| 7 | Click the **Edit** button in the row next to the found user |  |
| 8 | Click **DELETE** |  |
| 9 | Click **OK** in the pop-up |  |
| 10 | Click the **SYSTEM LOGS** tab |  |
| 11 | Enter the name of the user created at steps 4-5 into the **Message** field, press **Enter** | <li>In the logs table, there is a row with log message "Create user with name: <username>", type "security", where <username> is the name of the user created at steps 4-5 <li>In the logs table, there is a row with log message "Delete user with name: <username> id...", type "security", where <username> is the name of the user created at steps 4-5 |
