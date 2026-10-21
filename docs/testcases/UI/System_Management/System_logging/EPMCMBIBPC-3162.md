# User authentication

Test verifies that successful admin and non-admin login events are recorded in the system logs.

**Prerequisites**:
- At least two users
- One of the users must have the ROLE_ADMIN role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user from the prerequisites |  |
| 2 | Log out |  |
| 3 | Login as the admin user from the prerequisites |  |
| 4 | Click the gear icon (**System settings**) in the main menu |  |
| 5 | Click the **SYSTEM LOGS** tab | <li>In the logs table, there is a row with log message "Successfully authenticate user <...> <admin_username>", user "<admin_username>", type "security", where <admin_username> is the name of the user with the ROLE_ADMIN role from the prerequisites <li>In the logs table, there is a row with log message "Successfully authenticate user <...> <nonadmin_username>", user "<nonadmin_username>", type "security", where <nonadmin_username> is the name of the non-admin user from the prerequisites <li>The datetime of the message for the admin user is later than for the non-admin user |
