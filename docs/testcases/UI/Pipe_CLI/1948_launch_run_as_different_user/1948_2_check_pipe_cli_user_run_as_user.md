# Check Pipe CLI User Run As User

Test verifies that a user with sufficient permissions can launch a run as a different user using pipe CLI.

**Prerequisites**:
- Admin user
- Regular user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Open the **User Management** tab | |
| 4 | Find the admin user from the prerequisites and open their settings | |
| 5 | Click the configure button next to the ***Can run as this user:***  | |
| 6 | Click *Add user* button | |
| 7 | Specify the regular user from the prerequisites. Click **OK** button | |
| 8 | Click **OK** button | |
| 9 | Logout | |
| 10 | Login as the regular user from the prerequisites | |
| 11 | Open the **Tools** page | |
| 12 | Select test tool | |
| 13 | Run tool with *Custom settings* | |
| 14 | At the **Runs** page, click the just-launched run | |
| 15 | Wait until the **SSH** hyperlink appears | |
| 16 | Click the **SSH** hyperlink | |
| 17 | In the opened tab, enter and perform the `pipe run -di <tool>:latest -u <ADMIN_USER> -y` command, where `<tool>` is any tool name with group, `<ADMIN_USER>` is the admin user from the Prerequisites | The output contains `Pipeline run scheduled with RunId: <runID>` |
| 18 | Store `runID` from output | | 
| 19 | Logout | |
| 20 | Login as the admin user from the prerequisites | |
| 21 | Open the **Runs** page | |
| 22 | Click the run with *ID* stored at step 18 | <li> The *Owner* of the run is the admin user from the Prerequisites. <li> The run has ORIGINAL_OWNER parameter set to the regular user from the Prerequisites. |

**After:**
- Stop the runs launched at steps 13 and 17
- Remove regular user from the prerequisites from the the ***Can run as this user:*** for admin user from the prerequisites
