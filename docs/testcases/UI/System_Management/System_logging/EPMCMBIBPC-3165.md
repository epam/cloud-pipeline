# Permissions management

Test verifies that granting a user permissions on a pipeline is recorded in the system logs.

**Prerequisites**:
- At least two users
- One of the users must have the ROLE_ADMIN role
- An existing pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites |  |
| 2 | Open the **Library** |  |
| 3 | Navigate to and open the pipeline from the prerequisites |  |
| 4 | Hover over the gear icon (**Pipeline settings**), click **Edit** in the list that appears |  |
| 5 | Click the **Permissions** tab |  |
| 6 | Click **Add user** |  |
| 7 | Enter the non-admin user name from the prerequisites |  |
| 8 | Click **OK** |  |
| 9 | Click the user that appears in the **Groups and users** section |  |
| 10 | Set the following permissions: allow **Read** and **Execute**, deny **Write** |  |
| 11 | Close the pop-up |  |
| 12 | Click the gear icon (**System settings**) in the main menu |  |
| 13 | Click the **SYSTEM LOGS** tab |  |
| 14 | Enter the name of the admin user from the prerequisites into the **User** field, press **Enter** |  |
| 15 | Enter the text "Granting permissions" into the **Message** field, press **Enter** | <li>In the logs table, there is a row with log message "Granting permissions. Entity: class=PIPELINE id=<pipeline_id>, name=<pipeline_name>, permission: (mask: 0). Sid: name=<user_name> isPrincipal=true", type "security", where <pipeline_name> is the name of the pipeline opened at step 3, <user_name> is the non-admin user name from the prerequisites <li>In the logs table, there is a row with log message "Granting permissions. Entity: class=PIPELINE id=<pipeline_id>, name=<pipeline_name>, permission: READ,NO_WRITE,EXECUTE (mask: 25). Sid: name=<user_name> isPrincipal=true", type "security", where <pipeline_name> is the name of the pipeline opened at step 3, <user_name> is the non-admin user name from the prerequisites |
