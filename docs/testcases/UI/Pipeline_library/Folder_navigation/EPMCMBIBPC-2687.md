# [MANUAL] Unlock folder

Test verifies that unlocking a folder removes the lock icons from it and everything inside it, and restores the previously configured permissions for the role.

**Prerequisites**:
- Login as the non-admin user from the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case

**Preparations**:
1. Perform the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case
2. Perform the [EPMCMBIBPC-2685](EPMCMBIBPC-2685.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Navigate to the folder created at step 3 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case |  |
| 3 | Click on the gear icon in the right upper corner |  |
| 4 | Click **Unlock** item | Pop-up appears with the text `Are you sure you want to unlock folder {folder_name}`, where `{folder_name}` equals the value specified at step 3 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case |
| 5 | Click **OK** button in the appeared pop-up | At the library-tree, in front of the folder (created at step 3 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case), pipeline (created at step 13), storage (created at step 15), configuration (created at step 17) and folder (created at step 18), lock icons don't display |
| 6 | Hover over the gear icon -> click **Edit folder** in the appeared list |  |
| 7 | Click **Permissions** tab | <li> **Add user**, **Add group** buttons are enabled <li> the **Delete** button next to `ROLE_USER` is enabled |
| 8 | Click on the `ROLE_USER` item | <li> **Read** permission is set to **Allow** <li> **Write** and **Execute** permissions are unset (inherit) |
| 9 | Close the pop-up |  |
| 10 | Logout |  |
| 11 | Login as the non-admin user from the [EPMCMBIBPC-2685](EPMCMBIBPC-2685.md) case |  |
| 12 | Repeat steps 1-2 | At the library-tree, in front of the folder (created at step 3 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case), pipeline (created at step 13), storage (created at step 15), configuration (created at step 17) and folder (created at step 18), lock icons don't display |
