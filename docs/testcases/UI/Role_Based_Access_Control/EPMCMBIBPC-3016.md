# Block/unblock user

Test verifies that a blocked user cannot log in, and that their runs remain intact after unblocking.

**Prerequisites**:
- A valid non-admin user registered in the platform
- A valid admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-3 of the [EPMCMBIBPC-3014](EPMCMBIBPC-3014.md) case for the non-admin user from the prerequisites | The main Dashboard appears |
| 2 | Open the **Tools** page |  |
| 3 | Launch any available tool |  |
| 4 | Save the run ID of the just-launched tool |  |
| 5 | Log out |  |
| 6 | Login as the admin from the prerequisites |  |
| 7 | Click the gear icon in the main menu on the left side of the page to open **Settings** |  |
| 8 | Click the **User management** tab, click the **Users** subtab |  |
| 9 | Find the non-admin user from the prerequisites and click the edit button next to the username |  |
| 10 | Click **BLOCK** in the lower-left corner; in the pop-up that appears, click **OK** to confirm the blocking |  |
| 11 | Click **OK** to close the user-editing pop-up |  |
| 12 | Log out |  |
| 13 | Try to log in to the platform as the non-admin user from the prerequisites | The page with the authorization error appears |
| 14 | Repeat steps 6-9 |  |
| 15 | Click **UNBLOCK** in the lower-left corner; in the pop-up that appears, click **OK** to confirm the unblocking |  |
| 16 | Click **OK** to close the user-editing pop-up |  |
| 17 | Log out |  |
| 18 | Repeat step 1 | The main Dashboard appears |
| 19 | Open the **Runs** page | <li>The run is displayed on the **ACTIVE RUNS** tab <li>The run ID is the same as saved at step 4 <li>The run owner is the user from the prerequisites |
