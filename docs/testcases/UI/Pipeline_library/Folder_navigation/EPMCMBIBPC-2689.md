# [MANUAL] Unlock Project

Test verifies that unlocking a Project removes the lock icons from its folder, Method-Configurations folder and storage, and restores the previously configured permissions.

**Prerequisites**:
- Login as the non-admin user from the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case

**Preparations**:
1. Perform the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case
2. Perform the [EPMCMBIBPC-2688](EPMCMBIBPC-2688.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Navigate to the Project created at step 3 of the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case |  |
| 3 | Click on the gear icon in the right upper corner |  |
| 4 | Click **Unlock** item | Pop-up appears with the text `Are you sure you want to unlock folder {Project_name}`, where `{Project_name}` equals the value specified at step 3 of the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case |
| 5 | Click **OK** button in the appeared pop-up | At the library-tree, in front of the `{Project_name}` folder (created at step 3 of the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case), **Method-Configurations** folder, and `{Project_name}-storage` storage, lock icons don't display |
| 6 | Hover over the gear icon -> click **Edit folder** in the appeared list |  |
| 7 | Click **Permissions** tab | <li> **Add user**, **Add group** buttons are enabled <li> the **Delete** button next to `ROLE_USER` is enabled |
| 8 | Click on the `ROLE_USER` item | <li> **Read** permission is set to **Allow** <li> **Write** and **Execute** permissions are unset (inherit) |
| 9 | Close the pop-up |  |
| 10 | Logout |  |
| 11 | Login as the non-admin user from the [EPMCMBIBPC-2688](EPMCMBIBPC-2688.md) case |  |
| 12 | Repeat steps 1-2 | At the library-tree, in front of the `{Project_name}` folder (created at step 3 of the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case), **Method-Configurations** folder, and `{Project_name}-storage` storage, lock icons don't display |
