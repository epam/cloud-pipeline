# User profile modifications

Test verifies that assigning and unassigning a role to a user is recorded in the system logs.

**Prerequisites**:
- At least two users
- One of the users must have the ROLE_ADMIN role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-7 of the [EPMCMBIBPC-3163](EPMCMBIBPC-3163.md) case |  |
| 2 | Click **UNBLOCK** |  |
| 3 | Click **OK** in the pop-up |  |
| 4 | Click **OK** |  |
| 5 | Click the **SYSTEM LOGS** tab |  |
| 6 | Enter the name of the admin user from the prerequisites into the **User** field, press **Enter** |  |
| 7 | Enter the text "Blocking status=false" into the **Message** field, press **Enter** |  |
| 8 | In the search results, find the latest message like "Update user blocking status. User id=<user_id>. Blocking status=false" |  |
| 9 | Save the <user_id> from step 8 |  |
| 10 | Click the **USER MANAGEMENT** tab |  |
| 11 | Find the non-admin user from the prerequisites in the user list |  |
| 12 | Click the **Edit** button in the row next to the found non-admin user |  |
| 13 | Click the **Delete** icon near the **ROLE_USER** role |  |
| 14 | Click **OK** in the pop-up |  |
| 15 | Repeat steps 11-12 |  |
| 16 | Click the **Add role or group** drop-down list |  |
| 17 | Select the **ROLE_USER** role in the list |  |
| 18 | Click **+ Add** |  |
| 19 | Click **OK** in the pop-up |  |
| 20 | Repeat steps 5-6 |  |
| 21 | Enter the text "id=<user_id>" (where <user_id> is the user ID saved at step 9) into the **Message** field, press **Enter** | <li>In the logs table, there is a row with log message "Assing role. RoleId=2 UserIds=<user_id>", type "security", where <user_id> is the user ID saved at step 9 <li>In the logs table, there is a row with log message "Unassing role. RoleId=2 UserIds=<user_id>", type "security", where <user_id> is the user ID saved at step 9 |
