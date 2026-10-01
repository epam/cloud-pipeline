# Interactive endpoints access

Test verifies that accessing a tool's endpoint and SSH console is recorded in the system logs.

**Prerequisites**:
- At least two users
- One of the users must have the ROLE_ADMIN role
- An existing tool with an endpoint (the non-admin user must have read and execute permissions on that tool)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user from the prerequisites |  |
| 2 | Launch the tool from the prerequisites |  |
| 3 | Open the **Run logs** page of the launched tool |  |
| 4 | Wait until the **Endpoint** field appears in the upper-left corner |  |
| 5 | Click the endpoint hyperlink |  |
| 6 | Close the opened tab |  |
| 7 | Click the **SSH** hyperlink |  |
| 8 | Close the opened tab |  |
| 9 | Log out |  |
| 10 | Login as the admin user from the prerequisites |  |
| 11 | Click the gear icon (**System settings**) in the main menu |  |
| 12 | Click the **SYSTEM LOGS** tab |  |
| 13 | Enter the name of the non-admin user from the prerequisites into the **User** field, press **Enter** |  |
| 14 | Select the value `edge` for the **Service** field from the corresponding drop-down list | <li>In the logs table, there is a row with log message "...[SECURITY] Application: /pipeline-<ID>-<port>-0/; User: <user_name>; Status: Successfully authenticated...", type "security", where <ID> is the pipeline ID launched at step 2, <port> is the tool endpoint's port number, <user_name> is the non-admin user name from the prerequisites <li>In the logs table, there is a row with log message "...[SECURITY] Application: SSH-/ssh/pipeline/<ID>; User: <user_name>; Status: Successfully authenticated...", type "security", where <ID> is the pipeline ID launched at step 2, <user_name> is the non-admin user name from the prerequisites |

**After**:
- Stop the run launched at step 2
